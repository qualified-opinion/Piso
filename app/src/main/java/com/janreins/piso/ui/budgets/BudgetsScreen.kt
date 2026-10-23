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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.janreins.piso.data.models.Budget
import com.janreins.piso.ui.components.ConfirmDialog
import com.janreins.piso.ui.components.PisoCard
import com.janreins.piso.ui.components.PisoEmptyState
import com.janreins.piso.ui.components.PisoTopBar
import com.janreins.piso.ui.components.ProgressBarWithPercent
import com.janreins.piso.ui.theme.ExpenseRed
import com.janreins.piso.ui.theme.TealPrimary
import com.janreins.piso.ui.theme.WarningAmber
import com.janreins.piso.util.CurrencyUtil
import com.janreins.piso.util.DateUtil

@Composable
fun BudgetsScreen(
    viewModel: BudgetsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val budgets = uiState.budgets
    val categories = uiState.categories
    val categorySpending = uiState.currentMonthCategorySpending
    val breakdownMap = uiState.currentMonthBreakdownMap

    val currentMonthKey = uiState.currentMonthKey
    val currentMonthName = DateUtil.getMonthDisplayName(currentMonthKey)

    val currentBudgets = remember(budgets, currentMonthKey) {
        budgets.filter { it.monthKey == currentMonthKey }
    }

    val previousMonthKey = DateUtil.shiftMonthKey(currentMonthKey, -1)
    val copyableFromLastMonth = remember(budgets, currentMonthKey) {
        val alreadySet = budgets.filter { it.monthKey == currentMonthKey }.map { it.category }.toSet()
        budgets.count { it.monthKey == previousMonthKey && it.category !in alreadySet }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var budgetToEdit by remember { mutableStateOf<Budget?>(null) }
    var budgetToDelete by remember { mutableStateOf<Budget?>(null) }

    val totalBudgetLimit = remember(currentBudgets) { currentBudgets.sumOf { it.limitAmount } }
    val totalBudgetSpent = remember(currentBudgets, categorySpending) {
        currentBudgets.sumOf { categorySpending[it.category] ?: 0.0 }
    }

    Scaffold(
        modifier = modifier.testTag("budgets_screen"),
        topBar = {
            PisoTopBar(
                title = "Budgets",
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("budgets_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (copyableFromLastMonth > 0) {
                        IconButton(
                            onClick = { viewModel.copyBudgetsFromPreviousMonth(currentMonthKey) },
                            modifier = Modifier.testTag("budget_copy_last_month_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy last month's budgets",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("budget_add_header_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Set Budget",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("budgets_fab"),
                containerColor = TealPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Set Budget")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Summary Header Card
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                PisoCard(
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    borderColor = TealPrimary.copy(alpha = 0.2f),
                    cornerRadius = 24.dp,
                    contentPadding = 20.dp
                ) {
                    Text(
                        text = "Monthly Budget ($currentMonthName)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${CurrencyUtil.formatPeso(totalBudgetSpent)} of ${CurrencyUtil.formatPeso(totalBudgetLimit)}",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    val overallFraction = if (totalBudgetLimit > 0) (totalBudgetSpent / totalBudgetLimit).toFloat() else 0f
                    Spacer(modifier = Modifier.height(8.dp))
                    ProgressBarWithPercent(
                        fraction = overallFraction,
                        showLabel = false
                    )
                }
            }

            if (currentBudgets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PisoEmptyState(
                            message = "No budgets set for $currentMonthName yet.",
                            icon = Icons.Default.PieChart
                        )
                        if (copyableFromLastMonth > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.copyBudgetsFromPreviousMonth(currentMonthKey) },
                                modifier = Modifier.testTag("budget_copy_last_month_empty_button")
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy ${DateUtil.getMonthDisplayName(previousMonthKey)} budgets")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(currentBudgets, key = { it.id }) { budget ->
                        val spent = categorySpending[budget.category] ?: 0.0
                        val fraction = if (budget.limitAmount > 0) (spent / budget.limitAmount).toFloat() else 0f
                        val statusText = when {
                            fraction > 1.0f -> "Over budget by ${CurrencyUtil.formatPeso(spent - budget.limitAmount)}"
                            fraction >= 0.8f -> "Warning: ${CurrencyUtil.formatPeso(budget.limitAmount - spent)} remaining"
                            else -> "${CurrencyUtil.formatPeso(budget.limitAmount - spent)} remaining"
                        }

                        val statusColor = when {
                            fraction > 1.0f -> ExpenseRed
                            fraction >= 0.8f -> WarningAmber
                            else -> TealPrimary
                        }

                        val breakdown = breakdownMap[budget.category]

                        PisoCard(
                            contentPadding = 16.dp,
                            onClick = { budgetToEdit = budget }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = budget.category,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { budgetToEdit = budget },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { budgetToDelete = budget },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${CurrencyUtil.formatPeso(spent)} spent",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Limit: ${CurrencyUtil.formatPeso(budget.limitAmount)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            ProgressBarWithPercent(
                                fraction = fraction,
                                showLabel = true
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = statusColor
                            )

                            // Subcategory spending breakdown if present
                            if (breakdown != null && breakdown.subcategories.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(6.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    breakdown.subcategories.forEach { sub ->
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
                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }

    // --- Add Budget Dialog ---
    if (showAddDialog) {
        BudgetDialog(
            monthKey = currentMonthKey,
            existingCategories = currentBudgets.map { it.category },
            categories = categories,
            onDismiss = { showAddDialog = false },
            onSave = { newBudget ->
                viewModel.addBudget(newBudget)
                showAddDialog = false
            }
        )
    }

    // --- Edit Budget Dialog ---
    budgetToEdit?.let { currentBudget ->
        BudgetDialog(
            initialBudget = currentBudget,
            monthKey = currentBudget.monthKey,
            existingCategories = currentBudgets.map { it.category },
            categories = categories,
            onDismiss = { budgetToEdit = null },
            onSave = { updatedBudget ->
                viewModel.updateBudget(updatedBudget)
                budgetToEdit = null
            }
        )
    }

    // --- Delete Confirmation Dialog ---
    budgetToDelete?.let { bgtToDelete ->
        ConfirmDialog(
            title = "Delete Budget?",
            message = "Are you sure you want to remove the budget for ${bgtToDelete.category}?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteBudget(bgtToDelete)
                budgetToDelete = null
            },
            onDismiss = { budgetToDelete = null }
        )
    }
}
