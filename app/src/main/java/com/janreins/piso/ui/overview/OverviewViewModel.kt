package com.janreins.piso.ui.overview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.repository.FinanceRepository
import com.janreins.piso.util.DateUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The year overview: income, spending and savings per month, read-only. */
class OverviewViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FinanceRepository(AppDatabase.getDatabase(application))

    // 0 is this year, -1 last year, and so on
    private val yearOffset = MutableStateFlow(0)

    fun showPreviousYear() {
        val current = overview.value
        if (current == null || current.hasPreviousYear) {
            yearOffset.value = stepYearOffset(yearOffset.value, -1)
        }
    }

    fun showNextYear() {
        yearOffset.value = stepYearOffset(yearOffset.value, 1)
    }

    fun showCurrentYear() {
        yearOffset.value = 0
    }

    val overview: StateFlow<YearOverview?> = combine(
        repository.allTransactions,
        DateUtil.currentMonthKeyFlow(),
        yearOffset
    ) { txList, thisMonth, offset ->
        buildYearOverview(txList, yearOf(thisMonth) + offset, thisMonth)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
