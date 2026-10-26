package com.janreins.piso.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.janreins.piso.ui.components.PisoCard
import com.janreins.piso.ui.theme.ExpenseRed
import com.janreins.piso.ui.theme.IncomeGreen
import com.janreins.piso.ui.theme.TealPrimary
import com.janreins.piso.util.CurrencyUtil
import com.janreins.piso.util.DateUtil

/**
 * Home card for schedules that ask first: each waiting date with Record and Skip. Nothing
 * touches a balance until Jan taps Record.
 */
@Composable
fun DueRecurringCard(
    due: List<DueRecurring>,
    onRecord: (DueRecurring, Double) -> Unit,
    onSkip: (DueRecurring) -> Unit
) {
    var toRecord by remember { mutableStateOf<DueRecurring?>(null) }

    PisoCard(contentPadding = 18.dp, modifier = Modifier.testTag("home_due_recurring_card")) {
        Text(
            text = "Recurring, waiting for you",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "These don't change your balances until you record them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        due.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = ruleTitle(item.rule), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = DateUtil.formatEpochDay(item.dueDay) +
                            if (item.moreCount > 0) " · ${item.moreCount} more after this" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = CurrencyUtil.formatPeso(item.rule.amount),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (item.rule.type == "INCOME") IncomeGreen else ExpenseRed
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { toRecord = item },
                    modifier = Modifier.testTag("due_record_${item.rule.id}")
                ) {
                    Text("Record", color = TealPrimary)
                }
                TextButton(
                    onClick = { onSkip(item) },
                    modifier = Modifier.testTag("due_skip_${item.rule.id}")
                ) {
                    Text("Skip")
                }
            }
        }
    }

    toRecord?.let { item ->
        RecordDueDialog(
            item = item,
            onDismiss = { toRecord = null },
            onConfirm = { amount ->
                onRecord(item, amount)
                toRecord = null
            }
        )
    }
}

@Composable
private fun RecordDueDialog(
    item: DueRecurring,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf(CurrencyUtil.formatInputAmount(item.rule.amount)) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record ${ruleTitle(item.rule)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Dated ${DateUtil.formatEpochDay(item.dueDay)}. Change the amount if this one is different.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        error = null
                    },
                    label = { Text("Amount (₱)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("due_amount_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = CurrencyUtil.parsePositiveAmount(amountText)
                    if (amount == null || amount <= 0) {
                        error = "Please enter an amount greater than 0."
                    } else {
                        onConfirm(amount)
                    }
                },
                modifier = Modifier.testTag("due_record_confirm")
            ) {
                Text("Record")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
