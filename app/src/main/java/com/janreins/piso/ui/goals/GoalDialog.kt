package com.janreins.piso.ui.goals

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
import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.forPicker
import com.janreins.piso.data.models.Goal
import com.janreins.piso.util.CurrencyUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDialog(
    initialGoal: Goal? = null,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (Goal) -> Unit
) {
    var name by remember { mutableStateOf(initialGoal?.name ?: "") }
    var targetText by remember {
        mutableStateOf(if (initialGoal != null) CurrencyUtil.formatInputAmount(initialGoal.targetAmount) else "")
    }
    var currentText by remember {
        mutableStateOf(if (initialGoal != null) CurrencyUtil.formatInputAmount(initialGoal.currentAmount) else "")
    }
    val pickerAccounts = remember(accounts) { accounts.forPicker(initialGoal?.accountId) }
    var selectedAccountId by remember {
        mutableStateOf(initialGoal?.accountId)
    }

    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialGoal == null) "Add Goal" else "Edit Goal",
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
                // Goal Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Goal Name") },
                    placeholder = { Text("e.g. Emergency Fund, Japan Trip, MacBook") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("goal_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Target Amount
                OutlinedTextField(
                    value = targetText,
                    onValueChange = {
                        targetText = it
                        errorMessage = null
                    },
                    label = { Text("Target Amount (₱)") },
                    placeholder = { Text("50,000.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("goal_target_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Current Amount
                OutlinedTextField(
                    value = currentText,
                    onValueChange = { currentText = it },
                    label = { Text("Already Saved (₱, optional)") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("goal_current_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Optional Saved In Account
                if (pickerAccounts.isNotEmpty()) {
                    val currentAccountName = pickerAccounts.find { it.id == selectedAccountId }?.name ?: "None (General savings)"
                    ExposedDropdownMenuBox(
                        expanded = accountDropdownExpanded,
                        onExpandedChange = { accountDropdownExpanded = !accountDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = currentAccountName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Saved in Account (optional)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                                .testTag("goal_account_select"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = accountDropdownExpanded,
                            onDismissRequest = { accountDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None (General savings)") },
                                onClick = {
                                    selectedAccountId = null
                                    accountDropdownExpanded = false
                                }
                            )
                            pickerAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.name} (${CurrencyUtil.formatPeso(acc.balance)})") },
                                    onClick = {
                                        selectedAccountId = acc.id
                                        accountDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

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
                    if (name.isBlank()) {
                        errorMessage = "Goal name cannot be blank."
                        return@Button
                    }
                    val parsedTarget = CurrencyUtil.parsePositiveAmount(targetText)
                    if (parsedTarget == null || parsedTarget <= 0) {
                        errorMessage = "Please enter a target amount greater than 0."
                        return@Button
                    }
                    val parsedCurrent = if (currentText.isBlank()) 0.0 else CurrencyUtil.parseAmount(currentText)
                    if (parsedCurrent == null || parsedCurrent < 0) {
                        errorMessage = "Please enter a valid saved amount (0 or more)."
                        return@Button
                    }

                    val goal = Goal(
                        id = initialGoal?.id ?: 0L,
                        name = name.trim(),
                        targetAmount = parsedTarget,
                        currentAmount = parsedCurrent,
                        isCompleted = parsedCurrent >= parsedTarget,
                        deadlineMillis = initialGoal?.deadlineMillis,
                        accountId = selectedAccountId
                    )
                    onSave(goal)
                },
                modifier = Modifier.testTag("goal_save_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (initialGoal == null) "Add Goal" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("goal_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
