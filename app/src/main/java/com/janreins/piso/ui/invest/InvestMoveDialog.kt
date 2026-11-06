package com.janreins.piso.ui.invest

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
import com.janreins.piso.data.models.Investment
import com.janreins.piso.data.models.forPicker
import com.janreins.piso.ui.components.DateField
import com.janreins.piso.util.CurrencyUtil

/**
 * Why a sale amount can't be saved, or null if it can. A sale can't be more than the value
 * Piso has, so deleting it later puts the value back exactly.
 */
internal fun saleAmountError(amount: Double, currentValue: Double): String? =
    if (amount - currentValue > 0.005) {
        "That's more than its value of ${CurrencyUtil.formatPeso(currentValue)}. If it has grown, update the value first."
    } else {
        null
    }

/** Buy more of an investment from an account, or sell some into an account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestMoveDialog(
    investment: Investment,
    isSale: Boolean,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, accountId: Long, dateMillis: Long, note: String) -> Unit
) {
    val pickerAccounts = remember(accounts) { accounts.forPicker() }
    var amountText by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf(pickerAccounts.firstOrNull()?.id) }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var note by remember { mutableStateOf("") }
    var accountExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isSale) "Sell ${investment.name}" else "Buy more ${investment.name}",
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
                    text = if (isSale) {
                        "The money goes into the account and the value goes down by the same amount. It isn't income."
                    } else {
                        "The money comes out of the account and the value goes up by the same amount. It isn't spending."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Current value: ${CurrencyUtil.formatPeso(investment.currentValue)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMessage = null
                    },
                    label = { Text(if (isSale) "Amount received (₱)" else "Amount paid (₱)") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invest_move_amount_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (pickerAccounts.isEmpty()) {
                    Text(
                        text = "Add an account first.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = accountExpanded,
                        onExpandedChange = { accountExpanded = !accountExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = pickerAccounts.find { it.id == accountId }?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (isSale) "Into account" else "Paid from account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                                .testTag("invest_move_account_select"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = accountExpanded,
                            onDismissRequest = { accountExpanded = false }
                        ) {
                            pickerAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.name} (${CurrencyUtil.formatPeso(acc.balance)})") },
                                    onClick = {
                                        accountId = acc.id
                                        accountExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                DateField(
                    dateMillis = dateMillis,
                    onDateChange = { dateMillis = it },
                    testTagPrefix = "invest_move"
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invest_move_note_input"),
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
                    val parsed = CurrencyUtil.parsePositiveAmount(amountText)
                    if (parsed == null || parsed <= 0) {
                        errorMessage = "Please enter an amount greater than 0."
                        return@Button
                    }
                    val chosen = accountId
                    if (chosen == null) {
                        errorMessage = "Please pick an account."
                        return@Button
                    }
                    if (isSale) {
                        saleAmountError(parsed, investment.currentValue)?.let {
                            errorMessage = it
                            return@Button
                        }
                    }
                    onConfirm(parsed, chosen, dateMillis, note)
                },
                modifier = Modifier.testTag("invest_move_confirm_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (isSale) "Record Sale" else "Record Purchase")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("invest_move_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
