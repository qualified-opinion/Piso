package com.janreins.piso.ui.more

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.models.Budget
import com.janreins.piso.data.models.Debt
import com.janreins.piso.data.models.Investment
import com.janreins.piso.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class MoreUiState(
    val isLoading: Boolean = true,
    val budgets: List<Budget> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val investments: List<Investment> = emptyList(),
    val totalInvestments: Double = 0.0,
    val totalDebtsRemaining: Double = 0.0,
    val recurringCount: Int = 0
)

class MoreViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = FinanceRepository(db)
    }

    val budgets: StateFlow<List<Budget>> = repository.allBudgets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val debts: StateFlow<List<Debt>> = repository.allDebts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val investments: StateFlow<List<Investment>> = repository.allInvestments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<MoreUiState> = combine(
        budgets,
        debts,
        investments,
        repository.allRecurringRules
    ) { bdgts, dts, invs, rules ->
        MoreUiState(
            isLoading = false,
            budgets = bdgts,
            debts = dts,
            investments = invs,
            totalInvestments = invs.sumOf { it.currentValue },
            totalDebtsRemaining = dts.sumOf { it.remainingAmount },
            recurringCount = rules.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MoreUiState())

    fun showMessage(message: String) {
        viewModelScope.launch {
            _messageEvent.emit(message)
        }
    }
}
