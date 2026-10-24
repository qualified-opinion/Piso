package com.janreins.piso.ui.accounts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.Transaction
import com.janreins.piso.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class AccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<Account> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val isEmpty: Boolean = false
)

class AccountsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = FinanceRepository(db)
    }

    val accounts: StateFlow<List<Account>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<AccountsUiState> = combine(
        accounts,
        transactions
    ) { accs, txs ->
        AccountsUiState(
            isLoading = false,
            accounts = accs,
            transactions = txs,
            isEmpty = accs.isEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountsUiState())

    fun addAccount(account: Account) {
        viewModelScope.launch {
            repository.insertAccount(account)
            showMessage("Account created")
        }
    }

    fun updateAccount(account: Account) {
        viewModelScope.launch {
            repository.updateAccount(account)
            showMessage("Account updated")
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            val hasTx = repository.hasTransactionsForAccount(account.id)
            if (hasTx) {
                showMessage("Move or delete this account’s activity first.")
                return@launch
            }
            repository.deleteAccount(account)
            showMessage("Account deleted")
        }
    }

    fun adjustBalance(accountId: Long, newBalance: Double, dateMillis: Long) {
        viewModelScope.launch {
            val changed = repository.adjustAccountBalance(accountId, newBalance, dateMillis)
            showMessage(if (changed) "Balance adjusted" else "Balance already matches")
        }
    }

    fun showMessage(message: String) {
        viewModelScope.launch {
            _messageEvent.emit(message)
        }
    }
}
