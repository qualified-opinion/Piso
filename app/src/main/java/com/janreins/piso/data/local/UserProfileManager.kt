package com.janreins.piso.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.UUID

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

data class UserProfile(
    val displayName: String = "",
    val hasPin: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.LIGHT
)

fun isWeakPin(pin: String): Boolean {
    return pin in listOf("000000", "111111", "123456", "121212", "654321")
}

class UserProfileManager(
    context: Context,
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
    private val bootCount: () -> Int = {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    }
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("piso_user_profile", Context.MODE_PRIVATE)

    private val _userProfile = MutableStateFlow(loadProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _isLocked = MutableStateFlow(hasPin())
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    val pickerLockExemption = PickerLockExemption(elapsedRealtime)

    private fun loadProfile(): UserProfile {
        val name = prefs.getString(KEY_DISPLAY_NAME, "") ?: ""
        val hash = prefs.getString(KEY_PIN_HASH, "") ?: ""
        val themeStr = prefs.getString(KEY_THEME_MODE, ThemeMode.LIGHT.name) ?: ThemeMode.LIGHT.name
        val theme = try {
            ThemeMode.valueOf(themeStr)
        } catch (_: Exception) {
            ThemeMode.LIGHT
        }
        return UserProfile(
            displayName = name,
            hasPin = hash.isNotBlank(),
            themeMode = theme
        )
    }

    fun hasProfile(): Boolean {
        return _userProfile.value.displayName.isNotBlank()
    }

    fun hasPin(): Boolean {
        val hash = prefs.getString(KEY_PIN_HASH, "") ?: ""
        return hash.isNotBlank()
    }

    private fun getOrCreateSalt(): String {
        var salt = prefs.getString(KEY_PIN_SALT, null)
        if (salt.isNullOrBlank()) {
            salt = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_PIN_SALT, salt).apply()
        }
        return salt
    }

    private fun hashPin(pin: String): String {
        val salt = getOrCreateSalt()
        val input = "piso_salt_${salt}_pin_$pin"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun createProfile(displayName: String, pin: String?) {
        val editor = prefs.edit()
        editor.putString(KEY_DISPLAY_NAME, displayName.trim())
        if (!pin.isNullOrBlank() && pin.length == 6) {
            editor.putString(KEY_PIN_HASH, hashPin(pin))
        } else {
            editor.remove(KEY_PIN_HASH)
        }
        editor.remove(KEY_FAILED_ATTEMPTS)
        editor.remove(KEY_LOCKOUT_UNTIL)
        editor.remove(KEY_LOCKOUT_UNTIL_ELAPSED)
        editor.remove(KEY_LOCKOUT_BOOT_COUNT)
        editor.apply()
        _userProfile.value = loadProfile()
        _isLocked.value = false
    }

    fun updateDisplayName(name: String) {
        prefs.edit().putString(KEY_DISPLAY_NAME, name.trim()).apply()
        _userProfile.value = loadProfile()
    }

    fun getLockoutRemainingSeconds(): Int {
        val now = elapsedRealtime()
        val currentBoot = bootCount()
        var lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_ELAPSED, 0L)
        if (prefs.contains(KEY_LOCKOUT_UNTIL) ||
            (prefs.contains(KEY_LOCKOUT_UNTIL_ELAPSED) &&
                prefs.getInt(KEY_LOCKOUT_BOOT_COUNT, -1) != currentBoot)
        ) {
            // Restart a cooldown after reboot, or when upgrading a wall-clock deadline.
            lockoutUntil = now + lockoutSeconds(prefs.getInt(KEY_FAILED_ATTEMPTS, 0)) * 1000L
            prefs.edit()
                .remove(KEY_LOCKOUT_UNTIL)
                .putLong(KEY_LOCKOUT_UNTIL_ELAPSED, lockoutUntil)
                .putInt(KEY_LOCKOUT_BOOT_COUNT, currentBoot)
                .apply()
        }
        return ((lockoutUntil - now).coerceAtLeast(0L) + 999L).div(1000L).toInt()
    }

    private fun lockoutSeconds(attempts: Int): Int = when {
        attempts >= 10 -> 300 // 5 minutes
        attempts >= 5 -> 30
        else -> 0
    }

    private fun recordFailedAttempt() {
        val currentAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val editor = prefs.edit()
        editor.putInt(KEY_FAILED_ATTEMPTS, currentAttempts)
        val lockoutSeconds = lockoutSeconds(currentAttempts)
        if (lockoutSeconds > 0) {
            editor.putLong(KEY_LOCKOUT_UNTIL_ELAPSED, elapsedRealtime() + lockoutSeconds * 1000L)
            editor.putInt(KEY_LOCKOUT_BOOT_COUNT, bootCount())
        }
        editor.apply()
    }

    private fun resetFailedAttempts() {
        prefs.edit()
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_UNTIL)
            .remove(KEY_LOCKOUT_UNTIL_ELAPSED)
            .remove(KEY_LOCKOUT_BOOT_COUNT)
            .apply()
    }

    /** Wrong PINs since the last correct one. Kept across restarts. */
    fun failedAttempts(): Int = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)

    /**
     * The lock screen offers "Clear all data and reset" only after [RESET_AFTER_FAILED_ATTEMPTS]
     * wrong PINs, so the data can't be wiped by someone picking up the phone. With no PIN there
     * is no lock screen to protect.
     */
    fun canResetFromLockScreen(): Boolean =
        !hasPin() || failedAttempts() >= RESET_AFTER_FAILED_ATTEMPTS

    fun verifyPin(pin: String): Boolean {
        if (getLockoutRemainingSeconds() > 0) return false
        val storedHash = prefs.getString(KEY_PIN_HASH, "") ?: ""
        val valid = storedHash.isBlank() || storedHash == hashPin(pin)
        if (valid) resetFailedAttempts() else recordFailedAttempt()
        return valid
    }

    fun unlock(pin: String): Boolean {
        if (verifyPin(pin)) {
            _isLocked.value = false
            return true
        } else {
            return false
        }
    }

    fun lock() {
        pickerLockExemption.clear()
        if (hasPin()) {
            _isLocked.value = true
        }
    }

    fun setPin(newPin: String) {
        if (newPin.length == 6) {
            prefs.edit().putString(KEY_PIN_HASH, hashPin(newPin)).apply()
            resetFailedAttempts()
            _userProfile.value = loadProfile()
        }
    }

    fun changePin(oldPin: String, newPin: String): Boolean {
        if (!verifyPin(oldPin)) return false
        setPin(newPin)
        return true
    }

    fun removePin(oldPin: String): Boolean {
        if (!verifyPin(oldPin)) return false
        prefs.edit().remove(KEY_PIN_HASH).apply()
        resetFailedAttempts()
        _userProfile.value = loadProfile()
        _isLocked.value = false
        return true
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _userProfile.value = loadProfile()
    }

    fun clearProfile() {
        prefs.edit().clear().apply()
        _userProfile.value = loadProfile()
        _isLocked.value = false
    }

    companion object {
        const val RESET_AFTER_FAILED_ATTEMPTS = 10

        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val KEY_LOCKOUT_UNTIL_ELAPSED = "lockout_until_elapsed"
        private const val KEY_LOCKOUT_BOOT_COUNT = "lockout_boot_count"

        @Volatile
        private var INSTANCE: UserProfileManager? = null

        fun getInstance(context: Context): UserProfileManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserProfileManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
