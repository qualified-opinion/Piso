package com.janreins.piso.ui.debts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.Debt
import com.janreins.piso.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class DebtsUiState(
    val isLoading: Boolean = true,
    val debts: List<Debt> = emptyList(),
    val openDebts: List<Debt> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val isEmpty: Boolean = false
)

class DebtsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = FinanceRepository(db)
    }

    val debts: StateFlow<List<Debt>> = repository.allDebts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val openDebts: StateFlow<List<Debt>> = repository.openDebts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<Account>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<DebtsUiState> = combine(
        debts,
        openDebts,
        accounts
    ) { allD, openD, accs ->
        DebtsUiState(
            isLoading = false,
            debts = allD,
            openDebts = openD,
            accounts = accs,
            isEmpty = allD.isEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DebtsUiState())

    fun addDebt(debt: Debt) {
        viewModelScope.launch {
            repository.insertDebt(debt)
        }
    }

    fun updateDebt(debt: Debt) {
        viewModelScope.launch {
            repository.updateDebt(debt)
        }
    }

    fun deleteDebt(debt: Debt) {
        viewModelScope.launch {
            repository.deleteDebt(debt)
        }
    }

    fun recordDebtPayment(debtId: Long, amount: Double, fromAccountId: Long?, dateMillis: Long) {
        viewModelScope.launch {
            repository.recordDebtPayment(debtId, amount, fromAccountId, dateMillis)
            showMessage("Debt payment recorded")
        }
    }

    fun showMessage(message: String) {
        viewModelScope.launch {
            _messageEvent.emit(message)
        }
    }
}
