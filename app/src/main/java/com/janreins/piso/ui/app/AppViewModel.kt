package com.janreins.piso.ui.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.local.ThemeMode
import com.janreins.piso.data.local.UserProfile
import com.janreins.piso.data.local.UserProfileManager
import com.janreins.piso.data.repository.FinanceRepository
import com.janreins.piso.ui.state.MainTab
import com.janreins.piso.ui.state.MoreSubScreen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository
    private val profileManager = UserProfileManager.getInstance(application)

    init {
        val db = AppDatabase.getDatabase(application)
        repository = FinanceRepository(db)
        viewModelScope.launch {
            repository.seedDefaultCategoriesIfEmpty()
        }
    }

    // --- Profile & Auth State ---
    val userProfile: StateFlow<UserProfile> = profileManager.userProfile
    val isAppLocked: StateFlow<Boolean> = profileManager.isLocked

    val pickerLockExemption = profileManager.pickerLockExemption

    fun createProfile(name: String, pin: String?) {
        profileManager.createProfile(name, pin)
        showMessage("Welcome to Piso, ${name.trim()}!")
    }

    fun updateDisplayName(name: String) {
        profileManager.updateDisplayName(name)
        showMessage("Profile name updated")
    }

    fun getLockoutRemainingSeconds(): Int {
        return profileManager.getLockoutRemainingSeconds()
    }

    fun verifyPin(pin: String): Boolean {
        return profileManager.verifyPin(pin)
    }

    fun unlockApp(pin: String): Boolean {
        return profileManager.unlock(pin)
    }

    fun lockApp() {
        if (profileManager.hasPin()) {
            profileManager.lock()
            showMessage("Piso locked")
        } else {
            showMessage("Set a PIN first in Settings to enable lock.")
        }
    }

    fun onAppBackgrounded() {
        if (pickerLockExemption.shouldLockOnStop()) {
            profileManager.lock()
        }
    }

    fun onAppStarted() {
        if (pickerLockExemption.shouldLockOnStart()) profileManager.lock()
    }

    fun onAppResumed() {
        if (pickerLockExemption.shouldLockOnResume()) profileManager.lock()
    }

    fun setPin(pin: String) {
        profileManager.setPin(pin)
        showMessage("PIN set successfully")
    }

    fun changePin(oldPin: String, newPin: String): Boolean {
        val success = profileManager.changePin(oldPin, newPin)
        if (success) {
            showMessage("PIN changed successfully")
        }
        return success
    }

    fun removePin(oldPin: String): Boolean {
        val success = profileManager.removePin(oldPin)
        if (success) {
            showMessage("PIN removed")
        }
        return success
    }

    fun setThemeMode(mode: ThemeMode) {
        profileManager.setThemeMode(mode)
    }

    fun clearAllDataAndReset() {
        viewModelScope.launch {
            repository.clearAllData()
            profileManager.clearProfile()
            _currentTab.value = MainTab.HOME
            _moreSubScreen.value = null
            showMessage("Piso has been completely reset")
        }
    }

    // --- Navigation State ---
    private val _currentTab = MutableStateFlow(MainTab.HOME)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    private val _moreSubScreen = MutableStateFlow<MoreSubScreen?>(null)
    val moreSubScreen: StateFlow<MoreSubScreen?> = _moreSubScreen.asStateFlow()

    fun selectTab(tab: MainTab) {
        _currentTab.value = tab
        _moreSubScreen.value = null
    }

    fun openMoreSubScreen(subScreen: MoreSubScreen) {
        _moreSubScreen.value = subScreen
    }

    fun closeMoreSubScreen() {
        _moreSubScreen.value = null
    }

    // --- User Feedback Messages ---
    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    fun showMessage(message: String) {
        viewModelScope.launch {
            _messageEvent.emit(message)
        }
    }
}
