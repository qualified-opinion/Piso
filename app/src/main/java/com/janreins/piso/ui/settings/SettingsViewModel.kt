package com.janreins.piso.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.local.ThemeMode
import com.janreins.piso.data.local.UserProfile
import com.janreins.piso.data.local.UserProfileManager
import com.janreins.piso.data.models.UserCategory
import com.janreins.piso.data.models.UserSubcategory
import com.janreins.piso.data.repository.BackupSafety
import com.janreins.piso.data.repository.FinanceRepository
import com.janreins.piso.util.CsvExporter
import com.janreins.piso.util.ExportWriter
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val userProfile: UserProfile = UserProfile(),
    val categories: List<UserCategory> = emptyList(),
    val subcategories: List<UserSubcategory> = emptyList()
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository
    private val profileManager = UserProfileManager.getInstance(application)

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    private var restoreInProgress = false

    private val _canUndoRestore = MutableStateFlow(false)
    val canUndoRestore: StateFlow<Boolean> = _canUndoRestore.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = FinanceRepository(db, BackupSafety(File(application.filesDir, "backups")))
        viewModelScope.launch { _canUndoRestore.value = repository.hasUndoRestore() }
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

    fun importBackup(jsonString: String, onResult: (Boolean) -> Unit) {
        if (restoreInProgress) return
        restoreInProgress = true
        viewModelScope.launch {
            try {
                // The restore runs in a single DB transaction, so a failure leaves existing data untouched.
                val success = try {
                    repository.importBackupJson(jsonString)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                if (success) {
                    _canUndoRestore.value = true
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

    fun undoRestore() {
        if (restoreInProgress) return
        restoreInProgress = true
        viewModelScope.launch {
            try {
                val success = try {
                    repository.undoRestore()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                showMessage(if (success) "Previous data restored" else "Couldn't undo restore. Your current data was kept.")
                _canUndoRestore.value = repository.hasUndoRestore()
            } finally {
                restoreInProgress = false
            }
        }
    }

    fun writeExport(text: String, isCsv: Boolean = false, openStream: () -> OutputStream?) {
        viewModelScope.launch {
            try {
                ExportWriter.write(text, openStream)
                showMessage(if (isCsv) "Transactions exported to CSV" else "Backup file saved successfully")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(if (isCsv) "Failed to write CSV file" else "Failed to write backup file")
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            showMessage("All data cleared")
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
