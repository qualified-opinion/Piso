package com.janreins.piso.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.janreins.piso.ui.components.DateField
import com.janreins.piso.util.CurrencyUtil

/**
 * Says what an adjustment from [current] to [target] will do, or null when nothing changes.
 */
internal fun adjustmentPreview(current: Double, target: Double): String? {
    val difference = CurrencyUtil.roundCents(target - current)
    return when {
        difference > 0 -> "Piso will add ${CurrencyUtil.formatPeso(difference)} to this account."
        difference < 0 -> "Piso will take ${CurrencyUtil.formatPeso(-difference)} off this account."
        else -> null
    }
}

/**
 * Sets an account to its real balance. The difference is saved as a balance adjustment, which
 * doesn't count as income, spending or against any budget.
 */
@Composable
fun AdjustBalanceDialog(
    account: Account,
    onDismiss: () -> Unit,
    onConfirm: (newBalance: Double, dateMillis: Long) -> Unit
) {
    var balanceText by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val parsed = CurrencyUtil.parseAmount(balanceText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Adjust ${account.name}",
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
                    text = "Piso shows ${CurrencyUtil.formatPeso(account.balance)}. Enter what the account really has.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = balanceText,
                    onValueChange = {
                        balanceText = it
                        errorMessage = null
                    },
                    label = { Text("Actual balance (₱)") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adjust_balance_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (parsed != null) {
                    Text(
                        text = adjustmentPreview(account.balance, parsed)
                            ?: "This is already the balance, so nothing will change.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("adjust_balance_preview")
                    )
                }

                Text(
                    text = "This isn't counted as income or spending.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                DateField(
                    dateMillis = dateMillis,
                    onDateChange = { dateMillis = it },
                    testTagPrefix = "adjust_balance"
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
                    if (parsed == null) {
                        errorMessage = "Please enter a valid balance."
                        return@Button
                    }
                    if (adjustmentPreview(account.balance, parsed) == null) {
                        onDismiss()
                        return@Button
                    }
                    onConfirm(parsed, dateMillis)
                },
                modifier = Modifier.testTag("adjust_balance_confirm_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Adjust")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("adjust_balance_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
