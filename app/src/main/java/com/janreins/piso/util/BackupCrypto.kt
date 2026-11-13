package com.janreins.piso.util

import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password protection for backup files. The backup JSON is encrypted with AES-256-GCM, using a
 * key made from the password with PBKDF2-HMAC-SHA256 and a random salt. The file is a small JSON
 * wrapper holding the salt, IV and encrypted data, so it is still recognisable as a Piso backup.
 * The password is never stored; a forgotten password means the file can't be opened.
 */
object BackupCrypto {

    const val MIN_PASSWORD_LENGTH = 8
    const val FORMAT = "piso-encrypted-backup"
    private const val FORMAT_VERSION = 1
    internal const val DEFAULT_ITERATIONS = 150_000
    private const val MIN_ITERATIONS = 10_000
    // A file can only ask for the count we actually use. Anything higher is rejected before
    // key stretching, so a crafted backup can't hang the app on millions of rounds.
    private const val MAX_ITERATIONS = DEFAULT_ITERATIONS
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    class WrongPasswordException : GeneralSecurityException("Wrong password or damaged file")

    private val random = SecureRandom()

    /** Encrypts [plainJson] with [password]. Slow on purpose (key stretching): call off the main thread. */
    fun encrypt(plainJson: String, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): String {
        require(password.size >= MIN_PASSWORD_LENGTH) { "Password too short" }
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Unsupported iteration count" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(FORMAT.toByteArray(Charsets.UTF_8))
        val sealed = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getEncoder()
        return JSONObject()
            .put("appName", "Piso")
            .put("format", FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", iterations)
            .put("salt", encoder.encodeToString(salt))
            .put("iv", encoder.encodeToString(iv))
            .put("data", encoder.encodeToString(sealed))
            .toString()
    }

    /** True if [text] looks like a password-protected Piso backup (no password needed to tell). */
    fun isEncrypted(text: String): Boolean = try {
        JSONObject(text.trim().removePrefix("\uFEFF")).optString("format") == FORMAT
    } catch (_: Exception) {
        false
    }

    /**
     * Decrypts a protected backup back to its JSON. Throws [WrongPasswordException] for a wrong
     * password or a changed file, and IllegalArgumentException if it isn't a protected backup.
     */
    fun decrypt(protectedText: String, password: CharArray): String {
        val root = try {
            JSONObject(protectedText.trim().removePrefix("\uFEFF"))
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a protected backup", e)
        }
        require(root.optString("format") == FORMAT) { "Not a protected backup" }
        require(root.optInt("formatVersion", -1) == FORMAT_VERSION) { "Unsupported protected backup" }
        require(root.optString("kdf") == "PBKDF2WithHmacSHA256") { "Unsupported protected backup" }
        val iterations = root.optInt("iterations", -1)
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Unsupported protected backup" }
        val decoder = Base64.getDecoder()
        val salt: ByteArray
        val iv: ByteArray
        val sealed: ByteArray
        try {
            salt = decoder.decode(root.getString("salt"))
            iv = decoder.decode(root.getString("iv"))
            sealed = decoder.decode(root.getString("data"))
        } catch (e: Exception) {
            throw IllegalArgumentException("Damaged protected backup", e)
        }
        require(salt.size == SALT_BYTES && iv.size == IV_BYTES) { "Damaged protected backup" }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(FORMAT.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(sealed), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            throw WrongPasswordException()
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
