package com.janreins.piso.ui.debts

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
import com.janreins.piso.data.models.Debt
import com.janreins.piso.ui.components.DateField
import com.janreins.piso.util.CurrencyUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtPaymentDialog(
    debt: Debt,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirmPayment: (amount: Double, fromAccountId: Long?, dateMillis: Long) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val pickerAccounts = remember(accounts) { accounts.forPicker() }
    var selectedAccountId by remember { mutableStateOf(pickerAccounts.firstOrNull()?.id) }
    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Record Payment for ${debt.name}",
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
                    text = "Remaining Owed: ${CurrencyUtil.formatPeso(debt.remainingAmount)}",
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
                    label = { Text("Payment Amount (₱)") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("debt_payment_amount_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Paid From Account
                if (pickerAccounts.isNotEmpty()) {
                    val currentAccountName = pickerAccounts.find { it.id == selectedAccountId }?.name ?: "No account (only lower what I owe)"
                    ExposedDropdownMenuBox(
                        expanded = accountDropdownExpanded,
                        onExpandedChange = { accountDropdownExpanded = !accountDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = currentAccountName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Paid From Account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                                .testTag("debt_payment_account_select"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = accountDropdownExpanded,
                            onDismissRequest = { accountDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("No account (only lower what I owe)") },
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

                // With no account nothing is saved to Activity, so there is no date to set
                if (selectedAccountId != null) {
                    DateField(
                        dateMillis = dateMillis,
                        onDateChange = { dateMillis = it },
                        testTagPrefix = "debt_payment"
                    )
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
                    val parsed = CurrencyUtil.parsePositiveAmount(amountText)
                    if (parsed == null || parsed <= 0) {
                        errorMessage = "Please enter an amount greater than 0."
                        return@Button
                    }
                    // The debt can only drop to zero, so paying more would take extra money out of
                    // the account without it being recorded anywhere.
                    if (parsed - debt.remainingAmount > 0.005) {
                        errorMessage = "Payment can't be more than the ${CurrencyUtil.formatPeso(debt.remainingAmount)} still owed."
                        return@Button
                    }
                    onConfirmPayment(parsed, selectedAccountId, dateMillis)
                },
                modifier = Modifier.testTag("debt_payment_confirm_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Record Payment")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("debt_payment_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
