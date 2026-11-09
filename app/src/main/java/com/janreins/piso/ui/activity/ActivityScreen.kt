package com.janreins.piso.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.janreins.piso.data.models.CARD_PAYMENT_CATEGORY
import com.janreins.piso.data.models.isCardPurchase
import com.janreins.piso.data.models.Transaction
import com.janreins.piso.ui.components.ConfirmDialog
import com.janreins.piso.ui.components.PisoCard
import com.janreins.piso.ui.components.PisoEmptyState
import com.janreins.piso.ui.components.PisoTopBar
import com.janreins.piso.ui.theme.ExpenseContainer
import com.janreins.piso.ui.theme.ExpenseRed
import com.janreins.piso.ui.theme.IncomeContainer
import com.janreins.piso.ui.theme.IncomeGreen
import com.janreins.piso.ui.theme.TealContainer
import com.janreins.piso.ui.theme.TealPrimary
import com.janreins.piso.ui.theme.OnTealPrimary
import com.janreins.piso.ui.theme.OnIncomeGreen
import com.janreins.piso.ui.theme.OnExpenseRed
import com.janreins.piso.util.CurrencyUtil
import com.janreins.piso.util.DateUtil

@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val transactions = uiState.transactions
    val accounts = uiState.accounts
    val categories = uiState.categories
    val subcategories = uiState.subcategories
    val selectedMonthKey = uiState.selectedMonthKey
    val activityFilter = uiState.activityFilter

    var showAddDialog by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }

    var searchQuery by rememberSaveable { mutableStateOf("") }

    val accountsMap = remember(accounts) {
        accounts.associateBy { it.id }
    }

    val monthTransactions = remember(transactions, selectedMonthKey) {
        transactions.filter { DateUtil.getMonthKey(it.dateMillis) == selectedMonthKey }
    }

    // Same rules as the Home "This Month" card: goal contributions are money moved, not spending.
    val monthIncome = remember(monthTransactions) {
        monthTransactions.filter { it.type == "INCOME" && it.goalId == null && it.goalFlow == null }.sumOf { it.amount }
    }
    val monthSpent = remember(monthTransactions) {
        monthTransactions.filter { it.type == "EXPENSE" && it.goalId == null && it.goalFlow == null }.sumOf { it.amount }
    }

    // A search looks through every month unless Jan narrows it to the month on screen.
    var searchAllMonths by rememberSaveable { mutableStateOf(true) }
    val isSearchingAllMonths = searchQuery.isNotBlank() && searchAllMonths

    // Filter transactions by month (unless searching all months), filter type & search text
    val filteredTransactions = remember(transactions, selectedMonthKey, activityFilter, searchQuery, searchAllMonths, accountsMap) {
        filterActivity(
            transactions = transactions,
            monthKey = selectedMonthKey,
            filter = activityFilter,
            query = searchQuery,
            allMonths = searchAllMonths,
            accountNames = accountsMap.mapValues { it.value.name }
        )
    }

    // The list is shown day by day; the order inside each day stays newest first.
    val dayGroups = remember(filteredTransactions) {
        filteredTransactions.groupBy { DateUtil.toEpochDay(it.dateMillis) }.toList()
    }
    val todayEpochDay = DateUtil.todayEpochDay()

    Scaffold(
        modifier = modifier.testTag("activity_screen"),
        topBar = {
            PisoTopBar(title = "Activity")
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("activity_fab"),
                containerColor = TealPrimary,
                contentColor = OnTealPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add transaction")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- Month Selector ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val prev = DateUtil.shiftMonthKey(selectedMonthKey, -1)
                        viewModel.setSelectedMonthKey(prev)
                    },
                    modifier = Modifier.testTag("prev_month_button")
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }

                Text(
                    text = DateUtil.getMonthDisplayName(selectedMonthKey),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                IconButton(
                    onClick = {
                        val next = DateUtil.shiftMonthKey(selectedMonthKey, 1)
                        viewModel.setSelectedMonthKey(next)
                    },
                    modifier = Modifier.testTag("next_month_button")
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }

            // --- Type Filters Row ---
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    "ALL" to "All",
                    "INCOME" to "Income",
                    "EXPENSE" to "Expense",
                    "TRANSFER" to "Transfers",
                    "ADJUSTMENT" to "Adjustments"
                )
                items(filters) { (key, label) ->
                    val isSelected = activityFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setActivityFilter(key) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (key) {
                                "INCOME" -> IncomeGreen
                                "EXPENSE" -> ExpenseRed
                                else -> TealPrimary
                            },
                            selectedLabelColor = when (key) {
                                "INCOME" -> OnIncomeGreen
                                "EXPENSE" -> OnExpenseRed
                                else -> OnTealPrimary
                            }
                        )
                    )
                }
            }

            // --- Search ---
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search category, note, account or amount") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("activity_search_input")
            )

            // --- Where the search looks ---
            if (searchQuery.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = searchAllMonths,
                        onClick = { searchAllMonths = true },
                        label = { Text("All months") },
                        modifier = Modifier.testTag("activity_search_all_months_chip")
                    )
                    FilterChip(
                        selected = !searchAllMonths,
                        onClick = { searchAllMonths = false },
                        label = { Text("Only ${DateUtil.getMonthDisplayName(selectedMonthKey)}") },
                        modifier = Modifier.testTag("activity_search_this_month_chip")
                    )
                }
            }

            // --- Month Totals (a search across all months shows its match count instead) ---
            if (isSearchingAllMonths) {
                Text(
                    text = when (filteredTransactions.size) {
                        0 -> "No matches in any month"
                        1 -> "1 match across all months"
                        else -> "${filteredTransactions.size} matches across all months, newest first"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("activity_search_match_count")
                )
            } else Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MonthTotal(label = "Income", amount = monthIncome, color = IncomeGreen, modifier = Modifier.weight(1f))
                MonthTotal(label = "Spent", amount = monthSpent, color = ExpenseRed, modifier = Modifier.weight(1f))
                MonthTotal(
                    label = "Net",
                    amount = monthIncome - monthSpent,
                    color = if (monthIncome - monthSpent >= 0) IncomeGreen else ExpenseRed,
                    modifier = Modifier.weight(1f)
                )
            }

            // --- Transactions List or Empty State ---
            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    PisoEmptyState(
                        message = when {
                            transactions.isEmpty() -> "No transactions yet – add your first one!"
                            isSearchingAllMonths -> "No transactions in any month match \"${searchQuery.trim()}\"."
                            searchQuery.isNotBlank() -> "No transactions in ${DateUtil.getMonthDisplayName(selectedMonthKey)} match \"${searchQuery.trim()}\"."
                            monthTransactions.isEmpty() -> "No transactions in ${DateUtil.getMonthDisplayName(selectedMonthKey)}."
                            else -> "Nothing here for this filter."
                        },
                        icon = Icons.Default.ReceiptLong
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    dayGroups.forEach { (epochDay, dayTransactions) ->
                        // One heading per day ("Today", "Yesterday", "Tue, Nov 3"), newest day first
                        item(key = "day_$epochDay") {
                            Text(
                                text = DateUtil.formatDayHeader(epochDay, todayEpochDay),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = 4.dp, top = 8.dp)
                                    .semantics { heading() }
                            )
                        }
                        items(dayTransactions, key = { it.id }) { tx ->
                            val accountName = tx.accountId?.let { accountsMap[it]?.name }
                            val toAccountName = tx.transferToId?.let { accountsMap[it]?.name }
                            val isGoalTransfer = tx.type == "TRANSFER" && (tx.goalId != null || tx.goalFlow != null)

                            PisoCard(
                                contentPadding = 14.dp,
                                onClick = {
                                    if (tx.goalId != null || tx.goalFlow != null) {
                                        viewModel.showMessage("To change this, delete it here and add it again from Goals")
                                    } else if (tx.debtId != null) {
                                        viewModel.showMessage("To change this, delete it here and record it again from Debts")
                                    } else if (tx.investmentId != null) {
                                        viewModel.showMessage("To change this, delete it here and record it again from Invest")
                                    } else if (tx.type == "ADJUSTMENT") {
                                        viewModel.showMessage("To change this, delete it and adjust again")
                                    } else {
                                        transactionToEdit = tx
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when (tx.type) {
                                                        "INCOME" -> IncomeContainer
                                                        "EXPENSE" -> ExpenseContainer
                                                        else -> TealContainer
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = when (tx.type) {
                                                    "INCOME" -> Icons.Default.ArrowDownward
                                                    "EXPENSE" -> Icons.Default.ArrowUpward
                                                    "ADJUSTMENT" -> Icons.Default.Tune
                                                    else -> Icons.Default.SwapHoriz
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = when (tx.type) {
                                                    "INCOME" -> IncomeGreen
                                                    "EXPENSE" -> ExpenseRed
                                                    else -> TealPrimary
                                                }
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = if (tx.type == "TRANSFER") {
                                                    if (isGoalTransfer) {
                                                        // Goal contributions: money set aside or moved to the goal's account
                                                        val label = if (tx.goalFlow == "OUT") "From savings" else "Savings"
                                                        if (accountName != null && toAccountName != null && tx.accountId != tx.transferToId) {
                                                            "$label: $accountName → $toAccountName"
                                                        } else {
                                                            label
                                                        }
                                                    } else if (tx.investmentId != null) {
                                                        if (tx.transferToId != null) "Investment sold" else "Investment bought"
                                                    } else if (tx.debtId != null) {
                                                        // A payment to a card whose purchases are recorded
                                                        tx.category.ifBlank { CARD_PAYMENT_CATEGORY }
                                                    } else if (accountName != null && toAccountName != null) {
                                                        "$accountName → $toAccountName"
                                                    } else {
                                                        "Transfer"
                                                    }
                                                } else {
                                                    tx.category.ifBlank { tx.type }
                                                },
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            // An adjustment that adds money only has the "to" side filled in
                                            val oneSided = tx.type == "ADJUSTMENT" || tx.investmentId != null
                                            val shownAccount = if (oneSided) accountName ?: toAccountName else accountName
                                            val showsAccount = tx.type != "TRANSFER" ||
                                                (isGoalTransfer && tx.accountId == tx.transferToId) ||
                                                tx.debtId != null || tx.investmentId != null
                                            val details = listOfNotNull(
                                                tx.subcategory.takeIf { tx.type != "TRANSFER" && it.isNotBlank() },
                                                shownAccount?.takeIf { showsAccount },
                                                "Credit card".takeIf { tx.isCardPurchase },
                                                tx.note.takeIf { it.isNotBlank() },
                                                "Recurring".takeIf { tx.recurringId != null }
                                            ).joinToString(" · ")
                                            if (details.isNotEmpty()) {
                                                Text(
                                                    text = details,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = when (tx.type) {
                                                "INCOME" -> "+${CurrencyUtil.formatPeso(tx.amount)}"
                                                "EXPENSE" -> "-${CurrencyUtil.formatPeso(tx.amount)}"
                                                "ADJUSTMENT" -> (if (tx.transferToId != null) "+" else "-") + CurrencyUtil.formatPeso(tx.amount)
                                                else -> CurrencyUtil.formatPeso(tx.amount)
                                            },
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = when (tx.type) {
                                                    "INCOME" -> IncomeGreen
                                                    "EXPENSE" -> ExpenseRed
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { transactionToDelete = tx },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }

    // --- Add Transaction Dialog ---
    if (showAddDialog) {
        TransactionDialog(
            accounts = accounts,
            categories = categories,
            subcategories = subcategories,
            onAddCategory = { name, kind, onComplete ->
                viewModel.addCategory(name, kind) { success, error ->
                    if (success) onComplete(name.trim()) else error?.let(viewModel::showMessage)
                }
            },
            onAddSubcategory = { parent, kind, name, onComplete ->
                viewModel.addSubcategory(parent, kind, name) { success, error ->
                    if (success) onComplete(name.trim()) else error?.let(viewModel::showMessage)
                }
            },
            onDismiss = { showAddDialog = false },
            onSave = { newTx ->
                viewModel.addTransaction(newTx)
                showAddDialog = false
            }
        )
    }

    // --- Edit Transaction Dialog ---
    transactionToEdit?.let { currentTx ->
        TransactionDialog(
            initialTransaction = currentTx,
            accounts = accounts,
            categories = categories,
            subcategories = subcategories,
            onAddCategory = { name, kind, onComplete ->
                viewModel.addCategory(name, kind) { success, error ->
                    if (success) onComplete(name.trim()) else error?.let(viewModel::showMessage)
                }
            },
            onAddSubcategory = { parent, kind, name, onComplete ->
                viewModel.addSubcategory(parent, kind, name) { success, error ->
                    if (success) onComplete(name.trim()) else error?.let(viewModel::showMessage)
                }
            },
            onDismiss = { transactionToEdit = null },
            onSave = { updatedTx ->
                viewModel.updateTransaction(currentTx, updatedTx)
                transactionToEdit = null
            }
        )
    }

    // --- Delete Confirmation Dialog ---
    transactionToDelete?.let { txToDelete ->
        ConfirmDialog(
            title = "Delete transaction?",
            message = "Are you sure you want to delete this ${txToDelete.type.lowercase()} of ${CurrencyUtil.formatPeso(txToDelete.amount)}? Account balances will be updated.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteTransaction(txToDelete)
                transactionToDelete = null
            },
            onDismiss = { transactionToDelete = null }
        )
    }
}

@Composable
private fun MonthTotal(
    label: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = CurrencyUtil.formatPeso(amount),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
            maxLines = 1
        )
    }
}

/**
 * The transactions Activity lists: those of [monthKey] (or of every month when searching with
 * [allMonths] on), of the chosen type [filter], matching [query] in the category, subcategory,
 * note, either account name or the amount. Keeps the newest-first order it is given.
 */
internal fun filterActivity(
    transactions: List<Transaction>,
    monthKey: String,
    filter: String,
    query: String,
    allMonths: Boolean,
    accountNames: Map<Long, String>
): List<Transaction> {
    val trimmed = query.trim()
    val everyMonth = allMonths && trimmed.isNotEmpty()
    return transactions.filter { tx ->
        val inMonth = everyMonth || DateUtil.getMonthKey(tx.dateMillis) == monthKey
        val matchesType = when (filter) {
            "INCOME", "EXPENSE", "TRANSFER", "ADJUSTMENT" -> tx.type == filter
            else -> true
        }
        val matchesSearch = trimmed.isEmpty() || listOfNotNull(
            tx.category,
            tx.subcategory,
            tx.note,
            tx.accountId?.let { accountNames[it] },
            tx.transferToId?.let { accountNames[it] },
            CurrencyUtil.formatInputAmount(tx.amount)
        ).any { it.contains(trimmed, ignoreCase = true) }
        inMonth && matchesType && matchesSearch
    }
}
