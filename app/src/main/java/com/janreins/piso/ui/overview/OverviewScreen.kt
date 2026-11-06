package com.janreins.piso.ui.overview

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.janreins.piso.ui.components.PisoCard
import com.janreins.piso.ui.components.PisoTopBar
import com.janreins.piso.ui.theme.ExpenseRed
import com.janreins.piso.ui.theme.IncomeGreen
import com.janreins.piso.ui.theme.TealPrimary
import com.janreins.piso.util.CurrencyUtil
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** A year at a glance: income, spending and savings per month, like a monthly P&L. */
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    onBack: () -> Unit,
    onOpenMonth: (monthKey: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.testTag("overview_screen"),
        topBar = {
            PisoTopBar(
                title = "Year overview",
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("overview_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        val data = overview
        if (data == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                YearSelector(data, viewModel)
                OverviewList(data, onOpenMonth)
            }
        }
    }
}

@Composable
private fun YearSelector(data: YearOverview, viewModel: OverviewViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = viewModel::showPreviousYear,
            enabled = data.hasPreviousYear,
            modifier = Modifier.testTag("overview_previous_year")
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous year")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = data.year.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.testTag("overview_year")
            )
            if (!data.isCurrentYear) {
                TextButton(onClick = viewModel::showCurrentYear) { Text("Back to this year") }
            }
        }
        IconButton(
            onClick = viewModel::showNextYear,
            enabled = !data.isCurrentYear,
            modifier = Modifier.testTag("overview_next_year")
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next year")
        }
    }
}

@Composable
private fun OverviewList(data: YearOverview, onOpenMonth: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "summary") { SummaryCard(data) }
        item(key = "months") { MonthsCard(data, onOpenMonth) }
        if (data.topCategories.isNotEmpty()) {
            item(key = "categories") { CategoriesCard(data) }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun SummaryCard(data: YearOverview) {
    PisoCard(
        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
        borderColor = TealPrimary.copy(alpha = 0.2f),
        cornerRadius = 24.dp,
        contentPadding = 20.dp,
        modifier = Modifier.testTag("overview_summary")
    ) {
        val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
        Text(
            text = if (data.isCurrentYear) "Saved in ${data.year} so far" else "Saved in ${data.year}",
            style = MaterialTheme.typography.labelMedium,
            color = onContainer.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = CurrencyUtil.formatPeso(data.totalSaved),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = if (data.totalSaved < 0.0) ExpenseRed else onContainer,
            modifier = Modifier.testTag("overview_total_saved")
        )
        Text(
            text = savingsRateText(data),
            style = MaterialTheme.typography.bodyMedium,
            color = onContainer
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            SummaryFigure("Income", CurrencyUtil.formatPeso(data.totalIncome), IncomeGreen, Modifier.weight(1f))
            SummaryFigure("Spent", CurrencyUtil.formatPeso(data.totalSpent), ExpenseRed, Modifier.weight(1f))
            SummaryFigure("Avg spent / month", CurrencyUtil.formatPeso(data.averageMonthlySpent), onContainer, Modifier.weight(1f))
        }
        if (data.totalGoalMoves > 0.0) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Of what you saved, ${CurrencyUtil.formatPeso(data.totalGoalMoves)} went into your goals.",
                style = MaterialTheme.typography.bodySmall,
                color = onContainer
            )
        }
    }
}

private fun savingsRateText(data: YearOverview): String {
    val rate = data.savingsRate ?: return "No income recorded this year"
    val percent = (rate * 100).roundToInt()
    return if (percent >= 0) "You kept $percent% of your income" else "You spent ${-percent}% more than you earned"
}

@Composable
private fun SummaryFigure(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
private fun MonthsCard(data: YearOverview, onOpenMonth: (String) -> Unit) {
    PisoCard(contentPadding = 16.dp, modifier = Modifier.testTag("overview_months")) {
        Text(
            text = "Month by month",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = if (data.monthsSpentMoreThanEarned > 0) {
                "You spent more than you earned in ${data.monthsSpentMoreThanEarned} of ${data.months.size} months. Tap a month to see its entries."
            } else {
                "Tap a month to see its entries."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(6.dp))

        TableRow("Month", "Income", "Spent", "Saved", header = true)
        // Newest month first, so this month is at the top.
        data.months.asReversed().forEach { m ->
            TableRow(
                month = shortMonthName(m.monthKey) + if (m.isCurrentMonth) " (so far)" else "",
                income = CurrencyUtil.formatPeso(m.income),
                spent = CurrencyUtil.formatPeso(m.spent),
                saved = CurrencyUtil.formatPeso(m.saved),
                savedColor = when {
                    m.saved < 0.0 -> ExpenseRed
                    m.saved > 0.0 -> IncomeGreen
                    else -> null
                },
                dim = !m.hasEntries,
                modifier = Modifier
                    .clickable { onOpenMonth(m.monthKey) }
                    .testTag("overview_month_${m.monthKey}")
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        TableRow(
            month = "Total",
            income = CurrencyUtil.formatPeso(data.totalIncome),
            spent = CurrencyUtil.formatPeso(data.totalSpent),
            saved = CurrencyUtil.formatPeso(data.totalSaved),
            savedColor = if (data.totalSaved < 0.0) ExpenseRed else null,
            bold = true,
            modifier = Modifier.testTag("overview_total_row")
        )
    }
}

@Composable
private fun TableRow(
    month: String,
    income: String,
    spent: String,
    saved: String,
    modifier: Modifier = Modifier,
    header: Boolean = false,
    bold: Boolean = false,
    dim: Boolean = false,
    savedColor: Color? = null
) {
    val base = if (header) {
        MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
    } else {
        MaterialTheme.typography.bodySmall
    }
    val style = if (bold) base.copy(fontWeight = FontWeight.Bold) else base
    val baseColor = when {
        header -> MaterialTheme.colorScheme.onSurfaceVariant
        dim -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(month, style = style, color = baseColor, modifier = Modifier.weight(0.9f))
        Text(income, style = style, color = baseColor, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
        Text(spent, style = style, color = baseColor, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
        Text(
            saved,
            style = style,
            color = if (dim) baseColor else savedColor ?: baseColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.1f)
        )
    }
}

@Composable
private fun CategoriesCard(data: YearOverview) {
    PisoCard(contentPadding = 16.dp, modifier = Modifier.testTag("overview_categories")) {
        Text(
            text = "Where the money went",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Your biggest spending categories in ${data.year}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        data.topCategories.forEach { cat ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(cat.category, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    text = "${CurrencyUtil.formatPeso(cat.spent)} · ${(cat.shareOfSpending * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun shortMonthName(monthKey: String): String = try {
    YearMonth.parse(monthKey).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
} catch (_: Exception) {
    monthKey
}
