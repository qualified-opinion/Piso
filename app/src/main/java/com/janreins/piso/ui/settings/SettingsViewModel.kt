package com.janreins.piso.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.local.BackupReminderStore
import com.janreins.piso.data.local.BackupStatus
import com.janreins.piso.data.local.ThemeMode
import com.janreins.piso.data.local.UserProfile
import com.janreins.piso.data.local.UserProfileManager
import com.janreins.piso.data.models.BackupData
import com.janreins.piso.data.models.BackupSummary
import com.janreins.piso.data.models.UserCategory
import com.janreins.piso.data.models.UserSubcategory
import com.janreins.piso.data.repository.BackupSafety
import com.janreins.piso.data.repository.FinanceRepository
import com.janreins.piso.data.repository.UndoRestoreInfo
import com.janreins.piso.util.BackupCrypto
import com.janreins.piso.util.CsvExporter
import com.janreins.piso.util.ExportWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupImportState(
    val data: BackupData? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    // A password-protected backup waiting for its password; held only until it's unlocked or dismissed.
    val lockedText: String? = null
) {
    val summary: BackupSummary? = data?.summary
    val needsPassword: Boolean get() = lockedText != null
}

/** Reads backup text that isn't password-protected, or explains why it can't be restored. */
internal fun parseBackupText(text: String): BackupImportState {
    val data = BackupData.fromJsonString(text)
    return if (data == null) BackupImportState(error = "This is not a valid Piso backup.") else BackupImportState(data = data)
}

/** Unlocks a password-protected backup and reads it. Wrong password keeps it locked with a message. */
internal fun unlockBackupText(lockedText: String, password: CharArray): BackupImportState = try {
    parseBackupText(BackupCrypto.decrypt(lockedText, password))
} catch (_: BackupCrypto.WrongPasswordException) {
    BackupImportState(lockedText = lockedText, error = "Wrong password, or the file was changed. Try again.")
} catch (_: IllegalArgumentException) {
    BackupImportState(error = "This protected backup is damaged or from a newer Piso.")
}

private const val MAX_BACKUP_BYTES = 20 * 1024 * 1024
private class BackupTooLargeException : IOException()

data class SettingsUiState(
    val isLoading: Boolean = true,
    val userProfile: UserProfile = UserProfile(),
    val categories: List<UserCategory> = emptyList(),
    val subcategories: List<UserSubcategory> = emptyList()
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository
    private val profileManager = UserProfileManager.getInstance(application)
    private val backupReminder = BackupReminderStore.getInstance(application)

    /** When a backup file was last saved, shown under Backup & Restore. */
    val backupStatus: StateFlow<BackupStatus> = backupReminder.status

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    private var restoreInProgress = false
    private var reviewJob: Job? = null
    private val _backupImportState = MutableStateFlow(BackupImportState())
    val backupImportState: StateFlow<BackupImportState> = _backupImportState.asStateFlow()

    private val _canUndoRestore = MutableStateFlow(false)
    val canUndoRestore: StateFlow<Boolean> = _canUndoRestore.asStateFlow()

    private val _undoRestoreInfo = MutableStateFlow<UndoRestoreInfo?>(null)
    val undoRestoreInfo: StateFlow<UndoRestoreInfo?> = _undoRestoreInfo.asStateFlow()

    private suspend fun refreshUndoRestore() {
        _undoRestoreInfo.value = repository.undoRestoreInfo()
        _canUndoRestore.value = _undoRestoreInfo.value != null
    }

    init {
        val db = AppDatabase.getDatabase(application)
        repository = FinanceRepository(db, BackupSafety(File(application.filesDir, "backups")))
        viewModelScope.launch {
            profileManager.userProfile
                .map { it.displayName.isBlank() }
                .distinctUntilChanged()
                .collect { refreshUndoRestore() }
        }
    }

    val userProfile: StateFlow<UserProfile> = profileManager.userProfile

    val categories: StateFlow<List<UserCategory>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subcategories: StateFlow<List<UserSubcategory>> = repository.allSubcategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<SettingsUiState> = combine(
        userProfile,
        categories,
        subcategories
    ) { profile, cats, subs ->
        SettingsUiState(
            isLoading = false,
            userProfile = profile,
            categories = cats,
            subcategories = subs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    // --- Profile & PIN Actions ---
    fun updateDisplayName(name: String) {
        profileManager.updateDisplayName(name)
        showMessage("Profile name updated")
    }

    fun verifyPin(pin: String): Boolean {
        return profileManager.verifyPin(pin)
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

    fun lockApp() {
        if (profileManager.hasPin()) {
            profileManager.lock()
            showMessage("Piso locked")
        } else {
            showMessage("Set a PIN first in Settings to enable lock.")
        }
    }

    fun launchPicker(launch: () -> Unit) {
        try {
            profileManager.pickerLockExemption.launch(launch)
        } catch (_: RuntimeException) {
            showMessage("Couldn't open file picker")
        }
    }

    // --- Backup & Restore & Clear ---
    fun exportBackup(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val json = try {
                repository.exportBackupJson()
            } catch (e: Exception) {
                showMessage("Couldn't create backup")
                return@launch
            }
            onSuccess(json)
        }
    }

    fun exportTransactionsCsv(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val csv = try {
                CsvExporter.transactionsToCsv(
                    transactions = repository.allTransactions.first(),
                    accounts = repository.allAccounts.first()
                )
            } catch (e: Exception) {
                showMessage("Couldn't create CSV export")
                return@launch
            }
            onSuccess(csv)
        }
    }

    fun reviewBackup(openStream: () -> InputStream?) {
        reviewJob?.cancel()
        _backupImportState.value = BackupImportState(isLoading = true)
        reviewJob = viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) {
                try {
                    val data = openStream()?.use { input ->
                        val bytes = ByteArrayOutputStream()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            // Read one byte past the limit at most, including for providers
                            // that report no size (or the wrong size).
                            val count = input.read(buffer, 0, minOf(buffer.size, MAX_BACKUP_BYTES - bytes.size() + 1))
                            if (count < 0) break
                            if (bytes.size() + count > MAX_BACKUP_BYTES) throw BackupTooLargeException()
                            bytes.write(buffer, 0, count)
                        }
                        bytes.toString(Charsets.UTF_8.name())
                    }
                    when {
                        data == null -> BackupImportState(error = "This is not a valid Piso backup.")
                        BackupCrypto.isEncrypted(data) -> BackupImportState(lockedText = data)
                        else -> parseBackupText(data)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: BackupTooLargeException) {
                    BackupImportState(error = "Backup is too large. Choose a file of 20 MB or less.")
                } catch (_: Exception) {
                    BackupImportState(error = "Couldn't read this backup file. Choose a valid Piso backup.")
                }
            }
            _backupImportState.value = preview
            preview.error?.let { showMessage(it) }
        }
    }

    /** Tries [password] on the protected backup being reviewed. The password is wiped after use. */
    fun unlockBackup(password: CharArray) {
        val locked = _backupImportState.value.lockedText ?: return
        reviewJob?.cancel()
        _backupImportState.value = BackupImportState(isLoading = true, lockedText = locked)
        reviewJob = viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.Default) { unlockBackupText(locked, password) }
            } finally {
                password.fill('\u0000')
            }
            _backupImportState.value = result
        }
    }

    // A password chosen for the next backup file. Held only from the password dialog to the save.
    private var pendingExportPassword: CharArray? = null

    fun setPendingExportPassword(password: CharArray?) {
        pendingExportPassword?.fill('\u0000')
        pendingExportPassword = password
    }

    /**
     * Builds the next backup file's text, protected with the pending password if one was set.
     * If [expectProtected] but the password is gone (the app was closed in between), nothing is
     * written, so a backup Jan meant to protect is never saved in the clear.
     */
    fun exportBackupFile(expectProtected: Boolean, onSuccess: (String) -> Unit) {
        val password = pendingExportPassword
        pendingExportPassword = null
        if (expectProtected && password == null) {
            showMessage("Backup not saved. Choose Export File and enter the password again.")
            return
        }
        viewModelScope.launch {
            val text = try {
                val json = repository.exportBackupJson()
                if (password == null) json else withContext(Dispatchers.Default) { BackupCrypto.encrypt(json, password) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("Couldn't create backup")
                return@launch
            } finally {
                password?.fill('\u0000')
            }
            onSuccess(text)
        }
    }

    fun dismissBackupPreview() {
        reviewJob?.cancel()
        _backupImportState.value = BackupImportState()
    }

    fun importBackup(onResult: (Boolean) -> Unit) {
        if (restoreInProgress) return
        val data = _backupImportState.value.data ?: return
        restoreInProgress = true
        viewModelScope.launch {
            try {
                // The restore runs in a single DB transaction, so a failure leaves existing data untouched.
                val success = try {
                    repository.importBackup(data)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                if (success) {
                    refreshUndoRestore()
                    showMessage("Data restored successfully. Undo restore is available.")
                } else {
                    showMessage("Restore failed. Your current data was kept. Check the backup and available storage.")
                }
                onResult(success)
            } finally {
                restoreInProgress = false
            }
        }
    }

    fun undoRestore(confirmed: UndoRestoreInfo) {
        if (restoreInProgress) return
        restoreInProgress = true
        viewModelScope.launch {
            try {
                val success = try {
                    repository.undoRestore(confirmed.fileName)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                showMessage(if (success) "Undo ${confirmed.action} complete. Previous data restored."
                    else "Couldn't undo ${confirmed.action}. Your current data was kept.")
                refreshUndoRestore()
            } finally {
                restoreInProgress = false
            }
        }
    }

    fun writeExport(text: String, isCsv: Boolean = false, openStream: () -> OutputStream?) {
        viewModelScope.launch {
            try {
                ExportWriter.write(text, openStream)
                // Only a saved backup file counts; a CSV can't be restored.
                if (!isCsv) backupReminder.markBackupSaved()
                showMessage(if (isCsv) "Transactions exported to CSV" else "Backup file saved successfully")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(if (isCsv) "Failed to write CSV file" else "Failed to write backup file")
            }
        }
    }

    fun clearAllData() {
        if (restoreInProgress) return
        restoreInProgress = true
        viewModelScope.launch {
            try {
                repository.clearAllData()
                refreshUndoRestore()
                showMessage("All data cleared. You can undo this once from Settings.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("Couldn't clear all data. Check available storage.")
            } finally {
                restoreInProgress = false
            }
        }
    }

    // --- Category CRUD ---
    fun addCategory(name: String, kind: String, onResult: (Boolean, String?) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            onResult(false, "Category name cannot be empty")
            return
        }
        viewModelScope.launch {
            val existing = repository.allCategories.first()
            if (existing.any { it.kind.equals(kind, ignoreCase = true) && it.name.equals(trimmed, ignoreCase = true) }) {
                onResult(false, "A $kind category named '$trimmed' already exists")
                return@launch
            }
            val newCat = UserCategory(name = trimmed, kind = kind, isArchived = false)
            repository.insertCategory(newCat)
            showMessage("Category '$trimmed' added")
            onResult(true, null)
        }
    }

    fun updateCategoryName(category: UserCategory, newName: String, onResult: (Boolean, String?) -> Unit) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            onResult(false, "Category name cannot be empty")
            return
        }
        if (trimmed == category.name) {
            onResult(true, null)
            return
        }
        viewModelScope.launch {
            val existing = repository.allCategories.first()
            if (existing.any { it.id != category.id && it.kind.equals(category.kind, ignoreCase = true) && it.name.equals(trimmed, ignoreCase = true) }) {
                onResult(false, "A ${category.kind} category named '$trimmed' already exists")
                return@launch
            }
            repository.updateCategoryName(category, trimmed)
            showMessage("Category updated")
            onResult(true, null)
        }
    }

    fun toggleCategoryArchived(category: UserCategory) {
        viewModelScope.launch {
            val nextArchived = !category.isArchived
            repository.setCategoryArchived(category, nextArchived)
            showMessage(if (nextArchived) "${category.name} archived" else "${category.name} unarchived")
        }
    }

    fun deleteCategory(category: UserCategory) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            showMessage("Category removed")
        }
    }

    // --- Subcategory CRUD ---
    fun addSubcategory(parentCategoryName: String, parentKind: String, name: String, onResult: (Boolean, String?) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            onResult(false, "Subcategory name cannot be empty")
            return
        }
        viewModelScope.launch {
            val existing = repository.allSubcategories.first()
            if (existing.any { it.parentCategoryName.equals(parentCategoryName, ignoreCase = true) && it.parentKind.equals(parentKind, ignoreCase = true) && it.name.equals(trimmed, ignoreCase = true) }) {
                onResult(false, "Subcategory '$trimmed' already exists under $parentCategoryName")
                return@launch
            }
            val newSub = UserSubcategory(parentCategoryName = parentCategoryName, name = trimmed, isArchived = false, parentKind = parentKind.uppercase())
            repository.insertSubcategory(newSub)
            showMessage("Subcategory '$trimmed' added")
            onResult(true, null)
        }
    }

    fun updateSubcategoryName(subcategory: UserSubcategory, newName: String, onResult: (Boolean, String?) -> Unit) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            onResult(false, "Subcategory name cannot be empty")
            return
        }
        if (trimmed == subcategory.name) {
            onResult(true, null)
            return
        }
        viewModelScope.launch {
            val existing = repository.allSubcategories.first()
            if (existing.any { it.id != subcategory.id && it.parentCategoryName.equals(subcategory.parentCategoryName, ignoreCase = true) && it.parentKind.equals(subcategory.parentKind, ignoreCase = true) && it.name.equals(trimmed, ignoreCase = true) }) {
                onResult(false, "Subcategory '$trimmed' already exists under ${subcategory.parentCategoryName}")
                return@launch
            }
            repository.updateSubcategoryName(subcategory, trimmed)
            showMessage("Subcategory updated")
            onResult(true, null)
        }
    }

    fun toggleSubcategoryArchived(subcategory: UserSubcategory) {
        viewModelScope.launch {
            val nextArchived = !subcategory.isArchived
            repository.setSubcategoryArchived(subcategory, nextArchived)
            showMessage(if (nextArchived) "${subcategory.name} archived" else "${subcategory.name} unarchived")
        }
    }

    fun deleteSubcategory(subcategory: UserSubcategory) {
        viewModelScope.launch {
            repository.deleteSubcategory(subcategory)
            showMessage("Subcategory removed")
        }
    }

    fun showMessage(message: String) {
        viewModelScope.launch {
            _messageEvent.emit(message)
        }
    }
}
