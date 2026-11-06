package com.janreins.piso.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.janreins.piso.ui.components.PisoCard
import com.janreins.piso.ui.components.PisoEmptyState
import com.janreins.piso.ui.components.PisoTopBar
import com.janreins.piso.ui.theme.ExpenseRed
import com.janreins.piso.ui.theme.TealPrimary
import com.janreins.piso.util.CurrencyUtil
import com.janreins.piso.util.DateUtil

/** Budget against spending per category over the last few months, worst overspend first. */
@Composable
fun BudgetHistoryScreen(
    viewModel: BudgetsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val months by viewModel.historyMonths.collectAsStateWithLifecycle()
    val thisMonthKey = DateUtil.getCurrentMonthKey()

    Scaffold(
        modifier = modifier.testTag("budget_history_screen"),
        topBar = {
            PisoTopBar(
                title = "Budget history",
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("budget_history_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BUDGET_HISTORY_PERIODS.forEach { period ->
                    FilterChip(
                        selected = months == period,
                        onClick = { viewModel.setHistoryMonths(period) },
                        label = { Text("$period months") },
                        modifier = Modifier.testTag("budget_history_period_$period")
                    )
                }
            }

            val data = history
            if (data == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                HistoryList(data, thisMonthKey)
            }
        }
    }
}

@Composable
private fun HistoryList(data: BudgetHistory, thisMonthKey: String) {
    val first = DateUtil.getMonthDisplayName(data.monthKeys.first())
    val last = DateUtil.getMonthDisplayName(data.monthKeys.last())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PisoCard(
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                borderColor = TealPrimary.copy(alpha = 0.2f),
                cornerRadius = 24.dp,
                contentPadding = 20.dp
            ) {
                Text(
                    text = "$first to $last",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (data.timesOver == 0) {
                    Text(
                        text = if (data.categories.isEmpty()) "No budgets in this period" else "Within budget every month",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("budget_history_summary")
                    )
                } else {
                    Text(
                        text = "${CurrencyUtil.formatPeso(data.totalOverspent)} over budget",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = ExpenseRed,
                        modifier = Modifier.testTag("budget_history_summary")
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Went over ${data.timesOver} of ${data.budgetedCategoryMonths} monthly budgets",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (data.categories.isEmpty()) {
            item {
                PisoEmptyState(
                    message = "Set budgets on the Budgets page to compare them with your spending here.",
                    icon = Icons.Default.History
                )
            }
        }

        items(data.categories, key = { "cat_" + it.category }) { cat ->
            CategoryHistoryCard(cat, thisMonthKey)
        }

        if (data.unbudgeted.isNotEmpty()) {
            item(key = "unbudgeted") {
                PisoCard(contentPadding = 16.dp, modifier = Modifier.testTag("budget_history_unbudgeted")) {
                    Text(
                        text = "Spending with no budget",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "These categories had spending in this period but no budget in any month.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    data.unbudgeted.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(entry.category, style = MaterialTheme.typography.bodyMedium)
                            Text(CurrencyUtil.formatPeso(entry.spent), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun CategoryHistoryCard(cat: BudgetHistoryCategory, thisMonthKey: String) {
    PisoCard(contentPadding = 16.dp, modifier = Modifier.testTag("budget_history_card_${cat.category}")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = cat.category,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = if (cat.monthsOver > 0) "Over in ${cat.monthsOver} of ${cat.monthsBudgeted}" else "Never over",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (cat.monthsOver > 0) ExpenseRed else TealPrimary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${CurrencyUtil.formatPeso(cat.totalSpentInBudgetedMonths)} spent of ${CurrencyUtil.formatPeso(cat.totalLimit)} budgeted",
            style = MaterialTheme.typography.bodyMedium
        )
        if (cat.totalOverspent > 0.0) {
            Text(
                text = "Overspent ${CurrencyUtil.formatPeso(cat.totalOverspent)} in total",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = ExpenseRed
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(6.dp))

        HistoryRow("Month", "Budget", "Spent", "Difference", header = true)
        cat.months.forEach { m ->
            val name = DateUtil.getMonthDisplayName(m.monthKey).let { if (m.monthKey == thisMonthKey) "$it (so far)" else it }
            val diff = m.difference
            HistoryRow(
                month = name,
                budget = m.limit?.let { CurrencyUtil.formatPeso(it) } ?: "No budget",
                spent = CurrencyUtil.formatPeso(m.spent),
                difference = when {
                    diff == null -> "–"
                    diff > 0.0 -> "+${CurrencyUtil.formatPeso(diff)} over"
                    else -> "${CurrencyUtil.formatPeso(-diff)} left"
                },
                isOver = m.isOver
            )
        }
    }
}

@Composable
private fun HistoryRow(
    month: String,
    budget: String,
    spent: String,
    difference: String,
    header: Boolean = false,
    isOver: Boolean = false
) {
    val style = if (header) {
        MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
    } else {
        MaterialTheme.typography.bodySmall
    }
    val baseColor = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(month, style = style, color = baseColor, modifier = Modifier.weight(1.3f))
        Text(budget, style = style, color = baseColor, modifier = Modifier.weight(1f))
        Text(spent, style = style, color = if (isOver) ExpenseRed else baseColor, modifier = Modifier.weight(1f))
        Text(
            difference,
            style = if (isOver) style.copy(fontWeight = FontWeight.SemiBold) else style,
            color = if (isOver) ExpenseRed else baseColor,
            modifier = Modifier.weight(1.1f)
        )
    }
}
