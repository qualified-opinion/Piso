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
import com.janreins.piso.data.models.Categories
import com.janreins.piso.data.models.Debt
import com.janreins.piso.data.models.UserCategory
import com.janreins.piso.data.models.UserSubcategory
import com.janreins.piso.ui.components.DateField
import com.janreins.piso.util.CurrencyUtil

/** The expense categories a card purchase can go under: active ones, or the defaults. */
internal fun purchaseCategories(categories: List<UserCategory>): List<String> =
    categories
        .filter { it.kind.equals("EXPENSE", ignoreCase = true) && !it.isArchived }
        .map { it.name }
        .ifEmpty { Categories.EXPENSE }

/** A purchase made on a credit card: spending now, paid later from an account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardPurchaseDialog(
    card: Debt,
    categories: List<UserCategory>,
    subcategories: List<UserSubcategory>,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, category: String, subcategory: String, note: String, dateMillis: Long) -> Unit
) {
    val categoryNames = remember(categories) { purchaseCategories(categories) }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categoryNames.first()) }
    var subcategory by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var subcategoryExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val subcategoryNames = remember(subcategories, category) {
        subcategories.filter {
            it.parentCategoryName.equals(category, ignoreCase = true) &&
                it.parentKind.equals("EXPENSE", ignoreCase = true) &&
                !it.isArchived
        }.map { it.name }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Purchase on ${card.name}",
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
                    text = "Counts as spending now and adds to what you owe on the card. No account changes until you pay the card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMessage = null
                    },
                    label = { Text("Amount (₱)") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_purchase_amount_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                            .testTag("card_purchase_category_select"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categoryNames.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    if (name != category) subcategory = ""
                                    category = name
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                if (subcategoryNames.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = subcategoryExpanded,
                        onExpandedChange = { subcategoryExpanded = !subcategoryExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = subcategory.ifBlank { "None" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Subcategory (optional)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subcategoryExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                                .testTag("card_purchase_subcategory_select"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = subcategoryExpanded,
                            onDismissRequest = { subcategoryExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    subcategory = ""
                                    subcategoryExpanded = false
                                }
                            )
                            subcategoryNames.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        subcategory = name
                                        subcategoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                DateField(
                    dateMillis = dateMillis,
                    onDateChange = { dateMillis = it },
                    testTagPrefix = "card_purchase"
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. Groceries at SM") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_purchase_note_input"),
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
                    onConfirm(parsed, category, subcategory, note, dateMillis)
                },
                modifier = Modifier.testTag("card_purchase_confirm_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Record Purchase")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("card_purchase_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
