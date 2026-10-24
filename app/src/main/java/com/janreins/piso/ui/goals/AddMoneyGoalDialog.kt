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
import androidx.compose.runtime.mutableLongStateOf
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
import com.janreins.piso.ui.components.DateField
import com.janreins.piso.util.CurrencyUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMoneyGoalDialog(
    goal: Goal,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirmAdd: (amount: Double, fromAccountId: Long?, dateMillis: Long) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val goalAccount = accounts.find { it.id == goal.accountId }
    val pickerAccounts = remember(accounts) { accounts.forPicker() }
    // Default to an account other than the one the goal is kept in, so the contribution is a
    // real move of money into the goal's account.
    var selectedAccountId by remember {
        mutableStateOf(
            pickerAccounts.firstOrNull { it.id != goal.accountId }?.id ?: pickerAccounts.firstOrNull()?.id
        )
    }
    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val remainingNeeded = kotlin.math.max(0.0, goal.targetAmount - goal.currentAmount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Money to ${goal.name}",
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
                    text = "Goal Target: ${CurrencyUtil.formatPeso(goal.targetAmount)} (Remaining: ${CurrencyUtil.formatPeso(remainingNeeded)})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMessage = null
                    },
                    label = { Text("Amount to Add (₱)") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_money_amount_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Source Account
                if (pickerAccounts.isNotEmpty()) {
                    val currentAccountName = pickerAccounts.find { it.id == selectedAccountId }?.name ?: "None (just update goal progress)"
                    ExposedDropdownMenuBox(
                        expanded = accountDropdownExpanded,
                        onExpandedChange = { accountDropdownExpanded = !accountDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = currentAccountName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("From Account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                                .testTag("add_money_account_select"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = accountDropdownExpanded,
                            onDismissRequest = { accountDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None (just update goal progress)") },
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

                // Explain what happens to account balances
                val sourceAccount = pickerAccounts.find { it.id == selectedAccountId }
                Text(
                    text = when {
                        sourceAccount != null && goalAccount != null && sourceAccount.id != goalAccount.id ->
                            "Moves the money from ${sourceAccount.name} to ${goalAccount.name}, where this goal is kept."
                        sourceAccount != null ->
                            "The money stays in ${sourceAccount.name} and is set aside for this goal. Your balance and net worth don't change."
                        else ->
                            "Only the goal's progress changes. No account balance changes."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("add_money_effect_text")
                )

                DateField(
                    dateMillis = dateMillis,
                    onDateChange = { dateMillis = it },
                    testTagPrefix = "add_money"
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
                    val parsed = CurrencyUtil.parsePositiveAmount(amountText)
                    if (parsed == null || parsed <= 0) {
                        errorMessage = "Please enter an amount greater than 0."
                        return@Button
                    }
                    onConfirmAdd(parsed, selectedAccountId, dateMillis)
                },
                modifier = Modifier.testTag("add_money_confirm_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Add Money")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("add_money_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
