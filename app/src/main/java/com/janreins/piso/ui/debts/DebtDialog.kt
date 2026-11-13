package com.janreins.piso.ui.debts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Switch
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
import com.janreins.piso.data.models.Categories
import com.janreins.piso.data.models.Debt
import com.janreins.piso.data.models.amountOwed
import com.janreins.piso.util.CurrencyUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtDialog(
    initialDebt: Debt? = null,
    hasRecordedPurchases: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (Debt, seenRemaining: Double?) -> Unit
) {
    var name by remember { mutableStateOf(initialDebt?.name ?: "") }
    var kind by remember { mutableStateOf(initialDebt?.kind ?: Categories.DEBT_KINDS.first()) }
    var amounts by remember {
        mutableStateOf(
            DebtAmountInputState(
                originalText = initialDebt?.let { CurrencyUtil.formatInputAmount(it.originalAmount) } ?: "",
                remainingText = initialDebt?.let { CurrencyUtil.formatInputAmount(it.amountOwed()) } ?: "",
                remainingEdited = initialDebt != null
            )
        )
    }
    var notes by remember { mutableStateOf(initialDebt?.notes ?: "") }
    var tracksPurchases by remember { mutableStateOf(initialDebt?.tracksPurchases ?: false) }

    var kindDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialDebt == null) "Add Debt" else "Edit Debt",
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
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Debt Name") },
                    placeholder = { Text("e.g. BPI Credit Card, Friend Loan") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("debt_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Kind
                ExposedDropdownMenuBox(
                    expanded = kindDropdownExpanded,
                    onExpandedChange = { kindDropdownExpanded = !kindDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = kind,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Debt Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kindDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                            .testTag("debt_kind_select"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = kindDropdownExpanded,
                        onDismissRequest = { kindDropdownExpanded = false }
                    ) {
                        Categories.DEBT_KINDS.forEach { k ->
                            DropdownMenuItem(
                                text = { Text(k) },
                                onClick = {
                                    kind = k
                                    kindDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Original Amount
                OutlinedTextField(
                    value = amounts.originalText,
                    onValueChange = {
                        amounts = amounts.editOriginal(it)
                        errorMessage = null
                    },
                    label = { Text("Original Amount (₱)") },
                    placeholder = { Text("20,000.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("debt_original_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Remaining Amount
                OutlinedTextField(
                    value = amounts.remainingText,
                    onValueChange = {
                        amounts = amounts.editRemaining(it)
                        errorMessage = null
                    },
                    label = { Text("Remaining Amount (₱)") },
                    placeholder = { Text("20,000.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("debt_remaining_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("e.g. Monthly installment") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("debt_notes_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Credit cards only: record each purchase as spending when it's made
                if (kind == CREDIT_CARD_KIND) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("I record purchases on this card", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (tracksPurchases) {
                                    "Each purchase counts as spending when you make it. Paying the card only moves money, so it isn't counted twice."
                                } else {
                                    "Off: spending is counted when you pay the card, as before."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = tracksPurchases,
                            onCheckedChange = { tracksPurchases = it },
                            modifier = Modifier.testTag("debt_tracks_purchases_switch")
                        )
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
                        errorMessage = "Debt name cannot be blank."
                        return@Button
                    }
                    val parsedOriginal = CurrencyUtil.parsePositiveAmount(amounts.originalText)
                    if (parsedOriginal == null || parsedOriginal <= 0) {
                        errorMessage = "Please enter a valid original amount greater than 0."
                        return@Button
                    }
                    val parsedRemaining = amounts.remainingForSave(parsedOriginal)
                    if (parsedRemaining == null || parsedRemaining < 0) {
                        errorMessage = "Please enter a valid remaining amount (0 or more)."
                        return@Button
                    }
                    val tracks = kind == CREDIT_CARD_KIND && tracksPurchases
                    // Purchases already counted as spending. Turning tracking off would count
                    // them again the next time the card is paid.
                    if (initialDebt?.tracksPurchases == true && !tracks && hasRecordedPurchases) {
                        errorMessage = "This card has purchases recorded. Keep purchase tracking on, or paying the card counts that spending again."
                        return@Button
                    }

                    val debt = Debt(
                        id = initialDebt?.id ?: 0L,
                        name = name.trim(),
                        kind = kind,
                        originalAmount = parsedOriginal,
                        remainingAmount = parsedRemaining,
                        notes = notes.trim(),
                        dueMillis = initialDebt?.dueMillis,
                        tracksPurchases = tracks
                    )
                    // seenRemaining is what the field showed when the dialog opened. The save
                    // keeps a payment that landed since then when the user didn't change it.
                    onSave(debt, initialDebt?.amountOwed())
                },
                modifier = Modifier.testTag("debt_save_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (initialDebt == null) "Add Debt" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("debt_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

/** The debt kind that can track its purchases. */
const val CREDIT_CARD_KIND = "Credit Card"
