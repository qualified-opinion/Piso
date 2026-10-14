package com.janreins.piso.ui.goals

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.Goal
import com.janreins.piso.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class GoalsUiState(
    val isLoading: Boolean = true,
    val goals: List<Goal> = emptyList(),
    val activeGoals: List<Goal> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val isEmpty: Boolean = false
)

class GoalsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = FinanceRepository(db)
    }

    val goals: StateFlow<List<Goal>> = repository.allGoals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeGoals: StateFlow<List<Goal>> = repository.activeGoals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<Account>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<GoalsUiState> = combine(
        goals,
        activeGoals,
        accounts
    ) { allG, actG, accs ->
        GoalsUiState(
            isLoading = false,
            goals = allG,
            activeGoals = actG,
            accounts = accs,
            isEmpty = allG.isEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GoalsUiState())

    fun addGoal(goal: Goal) {
        viewModelScope.launch {
            repository.insertGoal(goal)
        }
    }

    fun updateGoal(goal: Goal) {
        viewModelScope.launch {
            repository.updateGoal(goal)
        }
    }

    fun setGoalCompleted(goalId: Long, done: Boolean) {
        viewModelScope.launch {
            repository.setGoalCompleted(goalId, done)
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch {
            repository.deleteGoal(goal)
        }
    }

    fun addMoneyToGoal(goalId: Long, amount: Double, fromAccountId: Long?) {
        viewModelScope.launch {
            val completed = repository.addMoneyToGoal(goalId, amount, fromAccountId)
            if (completed) {
                showMessage("Nice work – goal reached!")
            } else {
                showMessage("Added money to goal")
            }
        }
    }

    fun showMessage(message: String) {
        viewModelScope.launch {
            _messageEvent.emit(message)
        }
    }
}
