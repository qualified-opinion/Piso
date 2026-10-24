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
import com.janreins.piso.data.models.Goal
import com.janreins.piso.ui.components.DateField
import com.janreins.piso.util.CurrencyUtil

/**
 * Takes money out of a goal, the reverse of [AddMoneyGoalDialog]. By default the money stays in
 * the goal's own account and is only released from the goal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UseMoneyGoalDialog(
    goal: Goal,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirmUse: (amount: Double, toAccountId: Long?, dateMillis: Long) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val goalAccount = accounts.find { it.id == goal.accountId }
    var selectedAccountId by remember { mutableStateOf(goalAccount?.id) }
    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Use Money from ${goal.name}",
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
                    text = "Saved so far: ${CurrencyUtil.formatPeso(goal.currentAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMessage = null
                    },
                    label = { Text("Amount to Use (₱)") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("use_money_amount_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (goalAccount != null && accounts.size > 1) {
                    val currentAccountName = accounts.find { it.id == selectedAccountId }?.name ?: goalAccount.name
                    ExposedDropdownMenuBox(
                        expanded = accountDropdownExpanded,
                        onExpandedChange = { accountDropdownExpanded = !accountDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = currentAccountName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Move To Account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                                .testTag("use_money_account_select"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = accountDropdownExpanded,
                            onDismissRequest = { accountDropdownExpanded = false }
                        ) {
                            accounts.forEach { acc ->
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
                val destination = accounts.find { it.id == selectedAccountId }
                Text(
                    text = when {
                        goalAccount != null && destination != null && destination.id != goalAccount.id ->
                            "Moves the money from ${goalAccount.name} to ${destination.name}."
                        goalAccount != null ->
                            "The money stays in ${goalAccount.name} and is no longer set aside for this goal."
                        else ->
                            "Only the goal's progress changes. No account balance changes."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("use_money_effect_text")
                )

                DateField(
                    dateMillis = dateMillis,
                    onDateChange = { dateMillis = it },
                    testTagPrefix = "use_money"
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
                    if (parsed - goal.currentAmount > 0.005) {
                        errorMessage = "You can use up to the ${CurrencyUtil.formatPeso(goal.currentAmount)} saved."
                        return@Button
                    }
                    onConfirmUse(parsed, selectedAccountId, dateMillis)
                },
                modifier = Modifier.testTag("use_money_confirm_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Use Money")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("use_money_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
