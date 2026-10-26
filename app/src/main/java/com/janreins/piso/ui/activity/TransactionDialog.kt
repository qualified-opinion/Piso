package com.janreins.piso.ui.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.forPicker
import com.janreins.piso.data.models.Categories
import com.janreins.piso.data.models.Transaction
import com.janreins.piso.data.models.UserCategory
import com.janreins.piso.data.models.UserSubcategory
import com.janreins.piso.ui.components.DateField
import com.janreins.piso.ui.settings.CategoryInputDialog
import com.janreins.piso.ui.theme.ExpenseRed
import com.janreins.piso.ui.theme.IncomeGreen
import com.janreins.piso.ui.theme.TealPrimary
import com.janreins.piso.util.CurrencyUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDialog(
    initialTransaction: Transaction? = null,
    preselectedType: String = "EXPENSE",
    accounts: List<Account>,
    categories: List<UserCategory> = emptyList(),
    subcategories: List<UserSubcategory> = emptyList(),
    onAddCategory: ((name: String, kind: String, onComplete: (String) -> Unit) -> Unit)? = null,
    onAddSubcategory: ((parentName: String, parentKind: String, name: String, onComplete: (String) -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    var type by remember {
        mutableStateOf(initialTransaction?.type ?: preselectedType)
    }
    var amountText by remember {
        mutableStateOf(if (initialTransaction != null) CurrencyUtil.formatInputAmount(initialTransaction.amount) else "")
    }

    // Archived categories stay hidden from the picker, except the one an edited transaction already uses.
    val availableCategories = remember(categories, type) {
        val matching = categories
            .filter { it.kind.equals(type, ignoreCase = true) }
            .filter { !it.isArchived || (it.name == initialTransaction?.category && type == initialTransaction?.type) }
            .map { it.name }
        matching.ifEmpty { if (type == "INCOME") Categories.INCOME else Categories.EXPENSE }
    }

    fun defaultCategoryFor(txType: String): String =
        initialTransaction?.category
            ?.takeIf { txType == initialTransaction.type && it.isNotBlank() }
            ?: categories.firstOrNull { it.kind.equals(txType, ignoreCase = true) && !it.isArchived }?.name
            ?: (if (txType == "INCOME") Categories.INCOME.first() else Categories.EXPENSE.first())

    // Not keyed on the category list: a quick-added category makes the list re-emit, which
    // previously reset the selection back to the default.
    var category by remember { mutableStateOf(defaultCategoryFor(type)) }

    val availableSubcategories = remember(subcategories, category, type) {
        subcategories.filter {
            it.parentCategoryName.equals(category, ignoreCase = true) &&
                it.parentKind.equals(type, ignoreCase = true) &&
                !it.isArchived
        }.map { it.name }
    }

    var subcategory by remember(category) {
        val initialSub = initialTransaction?.subcategory ?: ""
        val initialParent = initialTransaction?.category ?: ""
        val value = if (category.equals(initialParent, ignoreCase = true)) initialSub else ""
        mutableStateOf(value)
    }

    var note by remember {
        mutableStateOf(initialTransaction?.note ?: "")
    }
    var dateMillis by remember {
        mutableLongStateOf(initialTransaction?.dateMillis ?: System.currentTimeMillis())
    }

    // Archived accounts stay hidden from the pickers, except ones an edited transaction already uses.
    val pickerAccounts = remember(accounts) {
        accounts.forPicker(initialTransaction?.accountId, initialTransaction?.transferToId)
    }

    var selectedAccountId by remember {
        mutableStateOf(initialTransaction?.accountId ?: pickerAccounts.firstOrNull()?.id)
    }
    var transferToAccountId by remember {
        mutableStateOf(initialTransaction?.transferToId ?: pickerAccounts.getOrNull(1)?.id ?: pickerAccounts.firstOrNull()?.id)
    }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var subcategoryDropdownExpanded by remember { mutableStateOf(false) }
    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var transferToDropdownExpanded by remember { mutableStateOf(false) }

    var showQuickAddCategoryDialog by remember { mutableStateOf(false) }
    var showQuickAddSubcategoryDialog by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialTransaction == null) "Add Transaction" else "Edit Transaction",
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
                // Type Selector Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("EXPENSE" to "Expense", "INCOME" to "Income", "TRANSFER" to "Transfer").forEach { (typeKey, label) ->
                        val isSelected = type == typeKey
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (type != typeKey) {
                                    type = typeKey
                                    category = defaultCategoryFor(typeKey)
                                    subcategory = ""
                                }
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (typeKey) {
                                    "INCOME" -> IncomeGreen
                                    "EXPENSE" -> ExpenseRed
                                    else -> TealPrimary
                                },
                                selectedLabelColor = androidx.compose.ui.graphics.Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Amount Field
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
                        .testTag("transaction_amount_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Category Dropdown (Hidden for Transfer)
                if (type != "TRANSFER") {
                    ExposedDropdownMenuBox(
                        expanded = categoryDropdownExpanded,
                        onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                                .testTag("transaction_category_select"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = categoryDropdownExpanded,
                            onDismissRequest = { categoryDropdownExpanded = false }
                        ) {
                            availableCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        category = cat
                                        subcategory = ""
                                        categoryDropdownExpanded = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = TealPrimary,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(
                                            text = "Add category…",
                                            color = TealPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                },
                                onClick = {
                                    categoryDropdownExpanded = false
                                    showQuickAddCategoryDialog = true
                                }
                            )
                        }
                    }

                    // Optional Subcategory Dropdown
                    // Shown if the category has active subcategories OR if user wants to add one
                    if (availableSubcategories.isNotEmpty() || subcategory.isNotBlank()) {
                        ExposedDropdownMenuBox(
                            expanded = subcategoryDropdownExpanded,
                            onExpandedChange = { subcategoryDropdownExpanded = !subcategoryDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = if (subcategory.isBlank()) "None" else subcategory,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Subcategory (optional)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subcategoryDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                    .fillMaxWidth()
                                    .testTag("transaction_subcategory_select"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = subcategoryDropdownExpanded,
                                onDismissRequest = { subcategoryDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("None (no subcategory)") },
                                    onClick = {
                                        subcategory = ""
                                        subcategoryDropdownExpanded = false
                                    }
                                )
                                availableSubcategories.forEach { sub ->
                                    DropdownMenuItem(
                                        text = { Text(sub) },
                                        onClick = {
                                            subcategory = sub
                                            subcategoryDropdownExpanded = false
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = TealPrimary,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Text(
                                                text = "Add subcategory…",
                                                color = TealPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    },
                                    onClick = {
                                        subcategoryDropdownExpanded = false
                                        showQuickAddSubcategoryDialog = true
                                    }
                                )
                            }
                        }
                    } else {
                        // Small text button to add a subcategory under this category if none exist yet
                        TextButton(
                            onClick = { showQuickAddSubcategoryDialog = true },
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                text = "Add subcategory under $category",
                                fontSize = 13.sp,
                                color = TealPrimary
                            )
                        }
                    }
                }

                // Account Selection
                if (pickerAccounts.isNotEmpty()) {
                    val currentAccountName = pickerAccounts.find { it.id == selectedAccountId }?.name ?: "Select Account"
                    ExposedDropdownMenuBox(
                        expanded = accountDropdownExpanded,
                        onExpandedChange = { accountDropdownExpanded = !accountDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = currentAccountName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (type == "TRANSFER") "From Account" else "Account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                                .testTag("transaction_account_select"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = accountDropdownExpanded,
                            onDismissRequest = { accountDropdownExpanded = false }
                        ) {
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

                    // Transfer To Account (Only for TRANSFER)
                    if (type == "TRANSFER") {
                        val toAccountName = pickerAccounts.find { it.id == transferToAccountId }?.name ?: "Select Destination"
                        ExposedDropdownMenuBox(
                            expanded = transferToDropdownExpanded,
                            onExpandedChange = { transferToDropdownExpanded = !transferToDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = toAccountName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Transfer To") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = transferToDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                    .fillMaxWidth()
                                    .testTag("transaction_transfer_to_select"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = transferToDropdownExpanded,
                                onDismissRequest = { transferToDropdownExpanded = false }
                            ) {
                                pickerAccounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text("${acc.name} (${CurrencyUtil.formatPeso(acc.balance)})") },
                                        onClick = {
                                            transferToAccountId = acc.id
                                            transferToDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Note Field
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. Grocery, Lunch with friends") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_note_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Date picker (back-dating is common when catching up on receipts)
                DateField(
                    dateMillis = dateMillis,
                    onDateChange = { dateMillis = it },
                    testTagPrefix = "transaction"
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
                    val parsedAmount = CurrencyUtil.parsePositiveAmount(amountText)
                    if (parsedAmount == null || parsedAmount <= 0) {
                        errorMessage = "Please enter an amount greater than 0."
                        return@Button
                    }
                    if (pickerAccounts.isNotEmpty() && selectedAccountId == null) {
                        errorMessage = "Please select an account."
                        return@Button
                    }
                    if (type == "TRANSFER") {
                        if (pickerAccounts.size < 2) {
                            errorMessage = "Add at least two accounts to record a transfer."
                            return@Button
                        }
                        if (transferToAccountId == null || selectedAccountId == transferToAccountId) {
                            errorMessage = "Transfer source and destination accounts must be different."
                            return@Button
                        }
                    }

                    val tx = Transaction(
                        id = initialTransaction?.id ?: 0L,
                        dateMillis = dateMillis,
                        type = type,
                        category = if (type == "TRANSFER") "Transfer" else category.trim(),
                        subcategory = if (type == "TRANSFER") "" else subcategory.trim(),
                        amount = parsedAmount,
                        note = note.trim(),
                        accountId = selectedAccountId,
                        transferToId = if (type == "TRANSFER") transferToAccountId else null,
                        goalId = initialTransaction?.goalId,
                        goalFlow = initialTransaction?.goalFlow,
                        debtId = initialTransaction?.debtId,
                        recurringId = initialTransaction?.recurringId
                    )
                    onSave(tx)
                },
                modifier = Modifier.testTag("transaction_save_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (initialTransaction == null) "Save" else "Update")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("transaction_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )

    // Quick Add Category Dialog
    if (showQuickAddCategoryDialog) {
        CategoryInputDialog(
            title = "Add ${if (type == "INCOME") "Income" else "Expense"} Category",
            initialName = "",
            confirmText = "Add",
            onDismiss = { showQuickAddCategoryDialog = false },
            onConfirm = { newName ->
                val existing = availableCategories.firstOrNull { it.equals(newName.trim(), ignoreCase = true) }
                if (existing != null) {
                    category = existing
                    subcategory = ""
                    showQuickAddCategoryDialog = false
                } else if (onAddCategory != null) {
                    onAddCategory(newName, type) { createdName ->
                        category = createdName
                        subcategory = ""
                        showQuickAddCategoryDialog = false
                    }
                } else {
                    category = newName
                    subcategory = ""
                    showQuickAddCategoryDialog = false
                }
            }
        )
    }

    // Quick Add Subcategory Dialog
    if (showQuickAddSubcategoryDialog) {
        CategoryInputDialog(
            title = "Add Subcategory under $category",
            initialName = "",
            confirmText = "Add",
            placeholder = "e.g. Groceries, Dining out",
            onDismiss = { showQuickAddSubcategoryDialog = false },
            onConfirm = { newSubName ->
                val existing = availableSubcategories.firstOrNull { it.equals(newSubName.trim(), ignoreCase = true) }
                if (existing != null) {
                    subcategory = existing
                    showQuickAddSubcategoryDialog = false
                } else if (onAddSubcategory != null) {
                    onAddSubcategory(category, type, newSubName) { createdSubName ->
                        subcategory = createdSubName
                        showQuickAddSubcategoryDialog = false
                    }
                } else {
                    subcategory = newSubName
                    showQuickAddSubcategoryDialog = false
                }
            }
        )
    }
}
