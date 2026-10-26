package com.janreins.piso.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.Categories
import com.janreins.piso.data.models.RecurringRule
import com.janreins.piso.data.models.UserCategory
import com.janreins.piso.data.models.UserSubcategory
import com.janreins.piso.data.models.forPicker
import com.janreins.piso.ui.components.DateField
import com.janreins.piso.ui.theme.ExpenseRed
import com.janreins.piso.ui.theme.IncomeGreen
import com.janreins.piso.util.CurrencyUtil
import com.janreins.piso.util.DateUtil
import com.janreins.piso.util.RecurringSchedule

/**
 * Adds or edits a recurring schedule. Editing changes future dates only; entries already
 * recorded stay as they are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringDialog(
    initialRule: RecurringRule? = null,
    accounts: List<Account>,
    categories: List<UserCategory>,
    subcategories: List<UserSubcategory>,
    onDismiss: () -> Unit,
    onSave: (RecurringRule) -> Unit,
    onPauseToggle: ((RecurringRule) -> Unit)? = null,
    onDelete: ((RecurringRule) -> Unit)? = null
) {
    var type by remember { mutableStateOf(initialRule?.type ?: "EXPENSE") }
    var amountText by remember {
        mutableStateOf(initialRule?.let { CurrencyUtil.formatInputAmount(it.amount) } ?: "")
    }

    val availableCategories = remember(categories, type) {
        val matching = categories
            .filter { it.kind.equals(type, ignoreCase = true) }
            .filter { !it.isArchived || (it.name == initialRule?.category && type == initialRule?.type) }
            .map { it.name }
        matching.ifEmpty { if (type == "INCOME") Categories.INCOME else Categories.EXPENSE }
    }

    fun defaultCategoryFor(ruleType: String): String =
        initialRule?.category?.takeIf { ruleType == initialRule.type && it.isNotBlank() }
            ?: categories.firstOrNull { it.kind.equals(ruleType, ignoreCase = true) && !it.isArchived }?.name
            ?: (if (ruleType == "INCOME") Categories.INCOME.first() else Categories.EXPENSE.first())

    var category by remember { mutableStateOf(defaultCategoryFor(type)) }
    val availableSubcategories = remember(subcategories, category, type) {
        subcategories.filter {
            it.parentCategoryName.equals(category, ignoreCase = true) &&
                it.parentKind.equals(type, ignoreCase = true) &&
                !it.isArchived
        }.map { it.name }
    }
    var subcategory by remember(category) {
        mutableStateOf(
            if (category.equals(initialRule?.category, ignoreCase = true)) initialRule?.subcategory ?: "" else ""
        )
    }
    var note by remember { mutableStateOf(initialRule?.note ?: "") }

    val pickerAccounts = remember(accounts) { accounts.forPicker(initialRule?.accountId) }
    var accountId by remember { mutableStateOf(initialRule?.accountId ?: pickerAccounts.firstOrNull()?.id) }

    var frequency by remember { mutableStateOf(initialRule?.frequency ?: RecurringRule.MONTHLY) }
    var intervalText by remember { mutableStateOf((initialRule?.intervalCount ?: 1).toString()) }
    var startMillis by remember {
        mutableLongStateOf(initialRule?.let { DateUtil.epochDayToMillis(it.startDay) } ?: System.currentTimeMillis())
    }
    var hasEndDate by remember { mutableStateOf(initialRule?.endDay != null) }
    var endMillis by remember {
        mutableLongStateOf(initialRule?.endDay?.let(DateUtil::epochDayToMillis) ?: System.currentTimeMillis())
    }
    var askFirst by remember { mutableStateOf(initialRule?.askFirst ?: false) }

    var categoryExpanded by remember { mutableStateOf(false) }
    var subcategoryExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialRule == null) "Add Recurring" else "Edit Recurring",
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("EXPENSE" to "Expense", "INCOME" to "Income").forEach { (typeKey, label) ->
                        FilterChip(
                            selected = type == typeKey,
                            onClick = {
                                if (type != typeKey) {
                                    type = typeKey
                                    category = defaultCategoryFor(typeKey)
                                    subcategory = ""
                                }
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (typeKey == "INCOME") IncomeGreen else ExpenseRed,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("recurring_type_${typeKey.lowercase()}")
                        )
                    }
                }

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
                        .testTag("recurring_amount_input"),
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
                            .testTag("recurring_category_select"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        availableCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    subcategory = ""
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                if (availableSubcategories.isNotEmpty() || subcategory.isNotBlank()) {
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
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = subcategoryExpanded,
                            onDismissRequest = { subcategoryExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None (no subcategory)") },
                                onClick = {
                                    subcategory = ""
                                    subcategoryExpanded = false
                                }
                            )
                            availableSubcategories.forEach { sub ->
                                DropdownMenuItem(
                                    text = { Text(sub) },
                                    onClick = {
                                        subcategory = sub
                                        subcategoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (pickerAccounts.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = accountExpanded,
                        onExpandedChange = { accountExpanded = !accountExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = pickerAccounts.find { it.id == accountId }?.name ?: "Select Account",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                                .testTag("recurring_account_select"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = accountExpanded,
                            onDismissRequest = { accountExpanded = false }
                        ) {
                            pickerAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name) },
                                    onClick = {
                                        accountId = acc.id
                                        accountExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. Rent, Netflix, Jeepney fare") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recurring_note_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Repeats", style = MaterialTheme.typography.labelLarge)
                // Two rows of two, so the chips fit a narrow phone
                RecurringRule.FREQUENCIES.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { freq ->
                            FilterChip(
                                selected = frequency == freq,
                                onClick = { frequency = freq },
                                label = { Text(RecurringSchedule.describe(freq, 1)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("recurring_frequency_${freq.lowercase()}")
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { value ->
                        intervalText = value.filter { it.isDigit() }.take(3)
                        errorMessage = null
                    },
                    label = { Text("Every how many") },
                    supportingText = {
                        Text(RecurringSchedule.describe(frequency, intervalText.toIntOrNull() ?: 1))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recurring_interval_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                DateField(
                    dateMillis = startMillis,
                    onDateChange = { startMillis = it },
                    testTagPrefix = "recurring_start",
                    label = "Starts"
                )
                if (frequency == RecurringRule.MONTHLY || frequency == RecurringRule.YEARLY) {
                    val day = java.time.LocalDate.ofEpochDay(DateUtil.toEpochDay(startMillis)).dayOfMonth
                    if (day > 28) {
                        Text(
                            text = "In shorter months it falls on the last day of the month.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Has an end date", modifier = Modifier.weight(1f))
                    Switch(
                        checked = hasEndDate,
                        onCheckedChange = { hasEndDate = it },
                        modifier = Modifier.testTag("recurring_end_switch")
                    )
                }
                if (hasEndDate) {
                    DateField(
                        dateMillis = endMillis,
                        onDateChange = { endMillis = it },
                        testTagPrefix = "recurring_end",
                        label = "Ends"
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ask me before recording")
                        Text(
                            text = if (askFirst) {
                                "Waits on Home for you to record it, so you can change the amount first."
                            } else {
                                "Recorded on its date automatically."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = askFirst,
                        onCheckedChange = { askFirst = it },
                        modifier = Modifier.testTag("recurring_ask_first_switch")
                    )
                }

                if (initialRule != null && (onPauseToggle != null || onDelete != null)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        onPauseToggle?.let { toggle ->
                            TextButton(
                                onClick = { toggle(initialRule) },
                                modifier = Modifier.testTag("recurring_pause_button")
                            ) {
                                Text(if (initialRule.isPaused) "Resume" else "Pause")
                            }
                        }
                        onDelete?.let { delete ->
                            TextButton(
                                onClick = { delete(initialRule) },
                                modifier = Modifier.testTag("recurring_delete_button")
                            ) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = CurrencyUtil.parsePositiveAmount(amountText)
                    val interval = intervalText.toIntOrNull()
                    val startDay = DateUtil.toEpochDay(startMillis)
                    val endDay = if (hasEndDate) DateUtil.toEpochDay(endMillis) else null
                    errorMessage = when {
                        amount == null || amount <= 0 -> "Please enter an amount greater than 0."
                        pickerAccounts.isNotEmpty() && accountId == null -> "Please select an account."
                        interval == null || interval < 1 -> "Enter how often it repeats, 1 or more."
                        endDay != null && endDay < startDay -> "The end date can't be before the start date."
                        else -> null
                    }
                    if (errorMessage != null || amount == null || interval == null) return@Button
                    onSave(
                        RecurringRule(
                            id = initialRule?.id ?: 0L,
                            type = type,
                            category = category,
                            subcategory = subcategory,
                            amount = amount,
                            note = note,
                            accountId = accountId,
                            frequency = frequency,
                            intervalCount = interval,
                            anchorDay = java.time.LocalDate.ofEpochDay(startDay).dayOfMonth,
                            startDay = startDay,
                            endDay = endDay,
                            nextDueDay = initialRule?.nextDueDay ?: startDay,
                            isPaused = initialRule?.isPaused ?: false,
                            askFirst = askFirst
                        )
                    )
                },
                modifier = Modifier.testTag("recurring_save_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (initialRule == null) "Save" else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("recurring_cancel_button")) {
                Text("Cancel")
            }
        }
    )
}
