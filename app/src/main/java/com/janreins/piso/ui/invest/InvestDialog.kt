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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.janreins.piso.data.models.Categories
import com.janreins.piso.data.models.Investment
import com.janreins.piso.util.CurrencyUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestDialog(
    initialInvestment: Investment? = null,
    onDismiss: () -> Unit,
    onSave: (Investment) -> Unit
) {
    var name by remember { mutableStateOf(initialInvestment?.name ?: "") }
    var kind by remember { mutableStateOf(initialInvestment?.kind ?: Categories.INVESTMENT_KINDS.first()) }
    var valueText by remember {
        mutableStateOf(if (initialInvestment != null) CurrencyUtil.formatInputAmount(initialInvestment.currentValue) else "")
    }
    var quantityText by remember {
        mutableStateOf(initialInvestment?.quantity ?: "")
    }
    var notes by remember { mutableStateOf(initialInvestment?.notes ?: "") }

    var typeDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialInvestment == null) "Add Investment" else "Edit Investment",
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
                    label = { Text("Asset Name") },
                    placeholder = { Text("e.g. PSE Index Fund, BTC, Gold 10g") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invest_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Type / Kind
                ExposedDropdownMenuBox(
                    expanded = typeDropdownExpanded,
                    onExpandedChange = { typeDropdownExpanded = !typeDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = kind,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Investment Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                            .testTag("invest_type_select"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = typeDropdownExpanded,
                        onDismissRequest = { typeDropdownExpanded = false }
                    ) {
                        Categories.INVESTMENT_KINDS.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t) },
                                onClick = {
                                    kind = t
                                    typeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Current Value
                OutlinedTextField(
                    value = valueText,
                    onValueChange = {
                        valueText = it
                        errorMessage = null
                    },
                    label = { Text("Current Value (₱)") },
                    placeholder = { Text("10,000.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invest_value_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Quantity
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Quantity / Shares (optional)") },
                    placeholder = { Text("e.g. 100 shares or 0.05 BTC") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invest_quantity_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("e.g. Stored in cold wallet / Col Financial") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invest_notes_input"),
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
                    if (name.isBlank()) {
                        errorMessage = "Asset name cannot be blank."
                        return@Button
                    }
                    val parsedValue = CurrencyUtil.parseNonNegativeAmount(valueText)
                    if (parsedValue == null || parsedValue < 0) {
                        errorMessage = "Please enter a valid current value."
                        return@Button
                    }

                    val investment = Investment(
                        id = initialInvestment?.id ?: 0L,
                        name = name.trim(),
                        kind = kind,
                        currentValue = parsedValue,
                        quantity = quantityText.trim().ifBlank { null },
                        notes = notes.trim(),
                        lastUpdatedMillis = System.currentTimeMillis()
                    )
                    onSave(investment)
                },
                modifier = Modifier.testTag("invest_save_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (initialInvestment == null) "Add Investment" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("invest_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
