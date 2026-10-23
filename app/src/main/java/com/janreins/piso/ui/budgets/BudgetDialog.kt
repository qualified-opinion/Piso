package com.janreins.piso.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.janreins.piso.data.models.Budget
import com.janreins.piso.data.models.Categories
import com.janreins.piso.data.models.UserCategory
import com.janreins.piso.util.CurrencyUtil
import com.janreins.piso.util.DateUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetDialog(
    initialBudget: Budget? = null,
    monthKey: String = DateUtil.getCurrentMonthKey(),
    existingCategories: List<String> = emptyList(),
    categories: List<UserCategory> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (Budget) -> Unit
) {
    val budgetMonthKey = initialBudget?.monthKey ?: monthKey
    val expenseCategories = remember(categories) {
        val custom = categories.filter { it.kind.equals("EXPENSE", ignoreCase = true) && !it.isArchived }.map { it.name }
        if (custom.isNotEmpty()) custom else Categories.EXPENSE
    }

    val availableCategories = remember(expenseCategories, existingCategories, initialBudget) {
        expenseCategories.filter { cat ->
            cat == initialBudget?.category || !existingCategories.contains(cat)
        }
    }

    var category by remember(availableCategories) {
        mutableStateOf(initialBudget?.category ?: availableCategories.firstOrNull() ?: Categories.EXPENSE.first())
    }
    var limitText by remember {
        mutableStateOf(if (initialBudget != null) CurrencyUtil.formatInputAmount(initialBudget.limitAmount) else "")
    }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialBudget == null) "Set Category Budget" else "Edit Budget",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "For ${DateUtil.getMonthDisplayName(budgetMonthKey)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Category Selection
                ExposedDropdownMenuBox(
                    expanded = categoryDropdownExpanded,
                    onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Expense Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                            .testTag("budget_category_select"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false }
                    ) {
                        availableCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Limit Amount
                OutlinedTextField(
                    value = limitText,
                    onValueChange = {
                        limitText = it
                        errorMessage = null
                    },
                    label = { Text("Monthly Limit (₱)") },
                    placeholder = { Text("5,000.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("budget_limit_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedLimit = CurrencyUtil.parsePositiveAmount(limitText)
                    if (parsedLimit == null || parsedLimit <= 0) {
                        errorMessage = "Please enter a budget limit greater than 0."
                        return@Button
                    }
                    // Every category may already have a budget this month; don't create a duplicate
                    if (category != initialBudget?.category && existingCategories.contains(category)) {
                        errorMessage = "$category already has a budget this month."
                        return@Button
                    }

                    val budget = Budget(
                        id = initialBudget?.id ?: 0L,
                        category = category.trim(),
                        limitAmount = parsedLimit,
                        monthKey = budgetMonthKey
                    )
                    onSave(budget)
                },
                modifier = Modifier.testTag("budget_save_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (initialBudget == null) "Set Budget" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("budget_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
