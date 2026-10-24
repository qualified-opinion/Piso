package com.janreins.piso.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.janreins.piso.ui.components.PisoCard
import com.janreins.piso.ui.components.ProgressBarWithPercent
import com.janreins.piso.ui.state.MainTab
import com.janreins.piso.ui.state.MoreSubScreen
import com.janreins.piso.ui.theme.ExpenseContainer
import com.janreins.piso.ui.theme.ExpenseRed
import com.janreins.piso.ui.theme.IncomeContainer
import com.janreins.piso.ui.theme.IncomeGreen
import com.janreins.piso.ui.theme.TealContainer
import com.janreins.piso.ui.theme.TealPrimary
import com.janreins.piso.util.CurrencyUtil
import com.janreins.piso.util.DateUtil

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenAddTransaction: (preselectedType: String) -> Unit,
    onNavigateToTab: (MainTab) -> Unit,
    onNavigateToSubScreen: (MoreSubScreen) -> Unit,
    onNavigateToActivityFilter: (monthKey: String, filter: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val netWorth = uiState.netWorthSummary
    val monthSummary = uiState.currentMonthSummary
    val spendingBreakdown = uiState.currentMonthSpendingBreakdown
    val accounts = uiState.accounts
    val budgets = uiState.budgets
    val activeGoals = uiState.activeGoals
    val openDebts = uiState.openDebts
    val recentTransactions = uiState.recentTransactions
    val categorySpending = uiState.currentMonthCategorySpending
    val userProfile = uiState.userProfile

    val currentMonthKey = uiState.currentMonthKey
    val currentMonthName = DateUtil.getMonthDisplayName(currentMonthKey)
    val baseGreeting = DateUtil.getGreeting()
    val greeting = if (userProfile.displayName.isNotBlank()) "$baseGreeting, ${userProfile.displayName}" else baseGreeting

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_content")
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Top Bar Greeting ---
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = currentMonthName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onNavigateToSubScreen(MoreSubScreen.SETTINGS) },
                    modifier = Modifier.testTag("home_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- Big Net Worth Card ---
        item {
            PisoCard(
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                borderColor = TealPrimary.copy(alpha = 0.2f),
                cornerRadius = 26.dp,
                contentPadding = 22.dp
            ) {
                Text(
                    text = "Net Worth",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = CurrencyUtil.formatPeso(netWorth.netWorth),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${CurrencyUtil.formatPeso(netWorth.accountsTotal)} in accounts · ${CurrencyUtil.formatPeso(netWorth.investmentsTotal)} invested · ${CurrencyUtil.formatPeso(netWorth.debtsTotal)} owed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                )
            }
        }

        // --- This Month Card ---
        item {
            PisoCard(contentPadding = 18.dp) {
                Text(
                    text = "This Month ($currentMonthName)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Net",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyUtil.formatPeso(monthSummary.net),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (monthSummary.net >= 0) IncomeGreen else ExpenseRed
                            )
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Income",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyUtil.formatPeso(monthSummary.income),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = IncomeGreen
                            )
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Spent",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyUtil.formatPeso(monthSummary.spent),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = ExpenseRed
                            )
                        )
                    }
                }

                // Goal contributions note
                if (monthSummary.goalMoves > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(TealContainer.copy(alpha = 0.5f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "You also set aside ${CurrencyUtil.formatPeso(monthSummary.goalMoves)} for goals — that’s savings, not spending.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // --- Spending by Category Breakdown ---
        item {
            PisoCard(
                onClick = {
                    onNavigateToActivityFilter(currentMonthKey, "EXPENSE")
                    onNavigateToTab(MainTab.ACTIVITY)
                },
                contentPadding = 18.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Spending by category",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "View all",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (spendingBreakdown.isEmpty()) {
                    Text(
                        text = "No expenses this month yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        spendingBreakdown.forEachIndexed { index, item ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            }
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.category,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = CurrencyUtil.formatPeso(item.totalAmount),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = ExpenseRed
                                        )
                                    )
                                }

                                if (item.subcategories.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 14.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        item.subcategories.forEach { sub ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = sub.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = CurrencyUtil.formatPeso(sub.amount),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Two Large Action Buttons ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onOpenAddTransaction("INCOME") },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("home_add_income_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IncomeGreen,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add income",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = { onOpenAddTransaction("EXPENSE") },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("home_add_expense_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExpenseRed,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add expense",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- Accounts Preview ---
        item {
            PisoCard(
                onClick = { onNavigateToTab(MainTab.ACCOUNTS) },
                contentPadding = 16.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Accounts",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "View all",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (accounts.isEmpty()) {
                    Text(
                        text = "No accounts yet – add cash, a bank account, or GCash.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    accounts.take(3).forEachIndexed { index, account ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = account.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = account.kind,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = CurrencyUtil.formatPeso(account.balance),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }

        // --- Budget Preview (Up to 4 Bars) ---
        val currentBudgets = budgets.filter { it.monthKey == currentMonthKey }
        if (currentBudgets.isNotEmpty()) {
            item {
                PisoCard(
                    onClick = { onNavigateToSubScreen(MoreSubScreen.BUDGETS) },
                    contentPadding = 16.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Budget Preview",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Budgets",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    currentBudgets.take(4).forEach { budget ->
                        val spent = categorySpending[budget.category] ?: 0.0
                        val fraction = if (budget.limitAmount > 0) (spent / budget.limitAmount).toFloat() else 0f
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = budget.category,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "${CurrencyUtil.formatPeso(spent)} / ${CurrencyUtil.formatPeso(budget.limitAmount)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            ProgressBarWithPercent(
                                fraction = fraction,
                                showLabel = false
                            )
                        }
                    }
                }
            }
        }

        // --- Active Goals Preview (Up to 3) ---
        if (activeGoals.isNotEmpty()) {
            item {
                PisoCard(
                    onClick = { onNavigateToTab(MainTab.GOALS) },
                    contentPadding = 16.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Goals",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Goals",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    activeGoals.take(3).forEachIndexed { index, goal ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        val fraction = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat() else 0f
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = goal.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "${CurrencyUtil.formatPeso(goal.currentAmount)} / ${CurrencyUtil.formatPeso(goal.targetAmount)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            ProgressBarWithPercent(fraction = fraction, showLabel = true)
                        }
                    }
                }
            }
        }

        // --- Open Debts Preview (Up to 3) ---
        if (openDebts.isNotEmpty()) {
            item {
                PisoCard(
                    onClick = { onNavigateToSubScreen(MoreSubScreen.DEBTS) },
                    contentPadding = 16.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Open Debts",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Debts",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    openDebts.take(3).forEachIndexed { index, debt ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = debt.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = debt.kind,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = CurrencyUtil.formatPeso(debt.remainingAmount),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = ExpenseRed
                                    )
                                )
                                Text(
                                    text = "of ${CurrencyUtil.formatPeso(debt.originalAmount)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Latest 6 Activity Rows ---
        item {
            PisoCard(
                onClick = { onNavigateToTab(MainTab.ACTIVITY) },
                contentPadding = 16.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Activity",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (recentTransactions.isEmpty()) {
                    Text(
                        text = "No transactions yet – add your first one!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    recentTransactions.forEachIndexed { index, tx ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (tx.type) {
                                                "INCOME" -> IncomeContainer
                                                "EXPENSE" -> ExpenseContainer
                                                else -> TealContainer
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (tx.type) {
                                            "INCOME" -> Icons.Default.ArrowDownward
                                            "EXPENSE" -> Icons.Default.ArrowUpward
                                            else -> Icons.Default.ReceiptLong
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = when (tx.type) {
                                            "INCOME" -> IncomeGreen
                                            "EXPENSE" -> ExpenseRed
                                            else -> TealPrimary
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = when {
                                            tx.type == "TRANSFER" && (tx.goalId != null || tx.goalFlow != null) -> "Savings"
                                            tx.type == "TRANSFER" -> "Transfer"
                                            else -> tx.category.ifBlank { tx.type }
                                        },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (tx.type != "TRANSFER" && tx.subcategory.isNotBlank()) {
                                            "${tx.subcategory}${if (tx.note.isNotBlank()) " · " + tx.note else ""}"
                                        } else if (tx.note.isNotBlank()) {
                                            tx.note
                                        } else {
                                            DateUtil.formatDateShort(tx.dateMillis)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = when (tx.type) {
                                    "INCOME" -> "+${CurrencyUtil.formatPeso(tx.amount)}"
                                    "EXPENSE" -> "-${CurrencyUtil.formatPeso(tx.amount)}"
                                    "ADJUSTMENT" -> (if (tx.transferToId != null) "+" else "-") + CurrencyUtil.formatPeso(tx.amount)
                                    else -> CurrencyUtil.formatPeso(tx.amount)
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = when (tx.type) {
                                        "INCOME" -> IncomeGreen
                                        "EXPENSE" -> ExpenseRed
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
