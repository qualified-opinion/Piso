package com.janreins.piso.util

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** What happened when the phone asked for a fingerprint. */
sealed interface FingerprintOutcome {
    data object Success : FingerprintOutcome
    /** Jan tapped "Use PIN", or closed the prompt. Nothing to show. */
    data object Cancelled : FingerprintOutcome
    /** The phone's fingerprints changed since this was turned on, so it has to be turned on again. */
    data object FingerprintsChanged : FingerprintOutcome
    data class Error(val message: String) : FingerprintOutcome
}

/**
 * Fingerprint unlock through Android's own fingerprint prompt (Android 10 and later). Nothing
 * leaves the phone and Piso never sees the fingerprint.
 *
 * A key in the phone's secure key store works only after a fingerprint scan, and Android
 * destroys it when a fingerprint is added to the phone. So someone who knows the phone's own
 * screen lock can't add their finger and then open Piso without the Piso PIN.
 */
object FingerprintAuth {
    private const val KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "piso_fingerprint_unlock"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** Android's prompt and the key settings Piso needs arrived in Android 10. */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** True when this phone has a fingerprint sensor (or other strong biometric) with one saved. */
    fun canUse(context: Context): Boolean {
        if (!isSupported()) return false
        return try {
            val manager = context.getSystemService(BiometricManager::class.java) ?: return false
            val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                manager.canAuthenticate()
            }
            status == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    /** Why fingerprint can't be turned on, in plain words. */
    fun unavailableReason(): String = when {
        !isSupported() -> "Fingerprint unlock needs Android 10 or later."
        else -> "Add a fingerprint in your phone's settings first."
    }

    /** Makes a new key, replacing any old one. Called when fingerprint unlock is turned on. */
    fun createKey(): Boolean = try {
        deleteKey()
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Every use needs a fresh fingerprint; the phone's own PIN doesn't count.
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_STORE).run {
            init(builder.build())
            generateKey()
        }
        true
    } catch (_: Exception) {
        false
    }

    /** Removes the key. Safe to call when there is none, or where there is no key store. */
    fun deleteKey() {
        try {
            KeyStore.getInstance(KEY_STORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
            // No key store (tests) or no key: nothing to remove.
        }
    }

    /** Errors where Jan closed the prompt or chose the PIN, so no message is needed. */
    internal fun isCancel(errorCode: Int): Boolean = errorCode in setOf(
        BiometricPrompt.BIOMETRIC_ERROR_CANCELED,
        BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED,
        BiometricPrompt.BIOMETRIC_ERROR_NEGATIVE_BUTTON
    )

    /** Errors that mean the key was lost, for example the phone's screen lock was removed. */
    internal fun isKeyLost(errorCode: Int): Boolean = errorCode in setOf(
        BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS
    )

    /**
     * Shows the phone's fingerprint prompt. [onOutcome] runs on the main thread, once. Returns a
     * signal that closes the prompt, or null if the prompt couldn't be shown (then [onOutcome]
     * has already run).
     */
    fun authenticate(
        context: Context,
        title: String,
        subtitle: String,
        negativeText: String,
        onOutcome: (FingerprintOutcome) -> Unit
    ): CancellationSignal? {
        if (!isSupported()) {
            onOutcome(FingerprintOutcome.Error(unavailableReason()))
            return null
        }
        return authenticateQ(context, title, subtitle, negativeText, onOutcome)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun authenticateQ(
        context: Context,
        title: String,
        subtitle: String,
        negativeText: String,
        onOutcome: (FingerprintOutcome) -> Unit
    ): CancellationSignal? {
        val cipher = try {
            val key = KeyStore.getInstance(KEY_STORE).apply { load(null) }
                .getKey(KEY_ALIAS, null) as? SecretKey
            if (key == null) {
                onOutcome(FingerprintOutcome.FingerprintsChanged)
                return null
            }
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            deleteKey()
            onOutcome(FingerprintOutcome.FingerprintsChanged)
            return null
        } catch (_: Exception) {
            onOutcome(FingerprintOutcome.Error("Couldn't start the fingerprint check. Use your PIN."))
            return null
        }

        val executor = context.mainExecutor
        val signal = CancellationSignal()
        var finished = false
        fun finish(outcome: FingerprintOutcome) {
            if (finished) return
            finished = true
            onOutcome(outcome)
        }

        val prompt = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setConfirmationRequired(false)
            .setNegativeButton(negativeText, executor) { _, _ -> finish(FingerprintOutcome.Cancelled) }
            .build()

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // The key works only after a real scan, so using it proves the scan happened.
                val ok = try {
                    result.cryptoObject?.cipher?.doFinal(ByteArray(16)) != null
                } catch (_: Exception) {
                    false
                }
                finish(if (ok) FingerprintOutcome.Success else FingerprintOutcome.Error("Fingerprint not confirmed. Use your PIN."))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when {
                    isCancel(errorCode) -> finish(FingerprintOutcome.Cancelled)
                    isKeyLost(errorCode) -> finish(FingerprintOutcome.FingerprintsChanged)
                    else -> finish(FingerprintOutcome.Error(errString.toString()))
                }
            }
            // onAuthenticationFailed (a finger that didn't match): the prompt says so and stays open.
        }

        return try {
            prompt.authenticate(BiometricPrompt.CryptoObject(cipher), signal, executor, callback)
            signal
        } catch (_: Exception) {
            finish(FingerprintOutcome.Error("Couldn't start the fingerprint check. Use your PIN."))
            null
        }
    }
}
