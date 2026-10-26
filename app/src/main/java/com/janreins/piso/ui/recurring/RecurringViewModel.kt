package com.janreins.piso.ui.recurring

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.RecurringRule
import com.janreins.piso.data.models.UserCategory
import com.janreins.piso.data.models.UserSubcategory
import com.janreins.piso.data.repository.FinanceRepository
import com.janreins.piso.util.DateUtil
import com.janreins.piso.util.RecurringSchedule
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecurringUiState(
    val isLoading: Boolean = true,
    val rules: List<RecurringRule> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<UserCategory> = emptyList(),
    val subcategories: List<UserSubcategory> = emptyList(),
    val today: Long = DateUtil.todayEpochDay()
)

/** The oldest waiting date of a schedule that asks first, and how many more dates wait after it. */
data class DueRecurring(
    val rule: RecurringRule,
    val dueDay: Long,
    val moreCount: Int
)

/** Schedules that ask first and have a date waiting on or before [today], oldest first. */
internal fun dueForConfirmation(rules: List<RecurringRule>, today: Long): List<DueRecurring> =
    rules.filter { it.askFirst && !it.isPaused && !it.hasEnded && it.nextDueDay <= today }
        .sortedWith(compareBy({ it.nextDueDay }, { it.id }))
        .map { rule ->
            val waiting = RecurringSchedule.dueDates(rule, LocalDate.ofEpochDay(today)).size
            DueRecurring(rule, rule.nextDueDay, (waiting - 1).coerceAtLeast(0))
        }

/** What a schedule's card says about it besides its next date, or null when it is running. */
internal fun recurringStatus(rule: RecurringRule, accounts: List<Account>): String? {
    val account = rule.accountId?.let { id -> accounts.firstOrNull { it.id == id } }
    return when {
        rule.hasEnded -> "Ended"
        rule.isPaused && account?.isArchived == true -> "Paused: account archived"
        rule.isPaused -> "Paused"
        else -> null
    }
}

class RecurringViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = FinanceRepository(db)
    }

    val uiState: StateFlow<RecurringUiState> = combine(
        repository.allRecurringRules,
        repository.allAccounts,
        repository.allCategories,
        repository.allSubcategories,
        DateUtil.currentEpochDayFlow()
    ) { rules, accounts, categories, subcategories, today ->
        RecurringUiState(
            isLoading = false,
            rules = rules,
            accounts = accounts,
            categories = categories,
            subcategories = subcategories,
            today = today
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecurringUiState())

    fun addRule(rule: RecurringRule) {
        viewModelScope.launch {
            repository.insertRecurringRule(rule)
            // A start date today or in the past is recorded straight away.
            val made = repository.postDueRecurring()
            showMessage(if (made > 0) "Schedule saved, ${entriesText(made)} added" else "Schedule saved")
        }
    }

    fun updateRule(rule: RecurringRule) {
        viewModelScope.launch {
            repository.updateRecurringRule(rule)
            val made = repository.postDueRecurring()
            showMessage(if (made > 0) "Schedule updated, ${entriesText(made)} added" else "Schedule updated")
        }
    }

    fun setPaused(rule: RecurringRule, paused: Boolean) {
        viewModelScope.launch {
            val changed = repository.setRecurringPaused(rule.id, paused)
            when {
                changed && paused -> showMessage("Schedule paused")
                changed -> {
                    repository.postDueRecurring()
                    showMessage("Schedule resumed")
                }
                !paused -> showMessage("Unarchive the account first, or pick another account.")
            }
        }
    }

    fun deleteRule(rule: RecurringRule) {
        viewModelScope.launch {
            repository.deleteRecurringRule(rule)
            showMessage("Schedule deleted. Entries already recorded stay in Activity.")
        }
    }

    fun showMessage(message: String) {
        viewModelScope.launch {
            _messageEvent.emit(message)
        }
    }
}

internal fun entriesText(count: Int): String =
    if (count == 1) "1 recurring entry" else "$count recurring entries"
