package com.janreins.piso.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.RecurringRule
import com.janreins.piso.ui.components.ConfirmDialog
import com.janreins.piso.ui.components.PisoCard
import com.janreins.piso.ui.components.PisoEmptyState
import com.janreins.piso.ui.components.PisoTopBar
import com.janreins.piso.ui.theme.ExpenseRed
import com.janreins.piso.ui.theme.IncomeGreen
import com.janreins.piso.ui.theme.TealPrimary
import com.janreins.piso.util.CurrencyUtil
import com.janreins.piso.util.DateUtil
import com.janreins.piso.util.RecurringSchedule

@Composable
fun RecurringScreen(
    viewModel: RecurringViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var ruleToEdit by remember { mutableStateOf<RecurringRule?>(null) }
    var ruleToDelete by remember { mutableStateOf<RecurringRule?>(null) }

    Scaffold(
        modifier = modifier.testTag("recurring_screen"),
        topBar = {
            PisoTopBar(
                title = "Recurring",
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("recurring_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("recurring_fab"),
                containerColor = TealPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Recurring")
            }
        }
    ) { innerPadding ->
        if (uiState.rules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                PisoEmptyState(
                    message = "No recurring entries yet. Add rent, bills, subscriptions or a daily fare, " +
                        "and Piso records them on their dates.",
                    icon = Icons.Default.Repeat
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Piso records these when you open the app, each on its own date. " +
                            "Changes apply to future dates only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(uiState.rules, key = { it.id }) { rule ->
                    RecurringCard(
                        rule = rule,
                        accounts = uiState.accounts,
                        onClick = { ruleToEdit = rule }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showAddDialog) {
        RecurringDialog(
            accounts = uiState.accounts,
            categories = uiState.categories,
            subcategories = uiState.subcategories,
            onDismiss = { showAddDialog = false },
            onSave = { rule ->
                viewModel.addRule(rule)
                showAddDialog = false
            }
        )
    }

    ruleToEdit?.let { rule ->
        RecurringDialog(
            initialRule = rule,
            accounts = uiState.accounts,
            categories = uiState.categories,
            subcategories = uiState.subcategories,
            onDismiss = { ruleToEdit = null },
            onSave = { updated ->
                viewModel.updateRule(updated)
                ruleToEdit = null
            },
            onPauseToggle = { current ->
                viewModel.setPaused(current, !current.isPaused)
                ruleToEdit = null
            },
            onDelete = { current ->
                ruleToEdit = null
                ruleToDelete = current
            }
        )
    }

    ruleToDelete?.let { rule ->
        ConfirmDialog(
            title = "Delete this schedule?",
            message = "No more entries will be made for ${ruleTitle(rule)}. " +
                "Entries already recorded stay in Activity.",
            onConfirm = {
                viewModel.deleteRule(rule)
                ruleToDelete = null
            },
            onDismiss = { ruleToDelete = null }
        )
    }
}

internal fun ruleTitle(rule: RecurringRule): String =
    rule.note.ifBlank { listOf(rule.category, rule.subcategory).filter { it.isNotBlank() }.joinToString(" · ") }

@Composable
private fun RecurringCard(
    rule: RecurringRule,
    accounts: List<Account>,
    onClick: () -> Unit
) {
    val status = recurringStatus(rule, accounts)
    val accountName = rule.accountId?.let { id -> accounts.firstOrNull { it.id == id }?.name }
    PisoCard(
        onClick = onClick,
        contentPadding = 16.dp,
        modifier = Modifier.testTag("recurring_card_${rule.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ruleTitle(rule),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = listOfNotNull(
                        RecurringSchedule.describe(rule.frequency, rule.intervalCount),
                        accountName,
                        if (rule.askFirst) "Asks first" else null
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = status ?: "Next: ${DateUtil.formatEpochDay(rule.nextDueDay)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status != null) MaterialTheme.colorScheme.error else TealPrimary
                )
            }
            Text(
                text = (if (rule.type == "INCOME") "+" else "-") + CurrencyUtil.formatPeso(rule.amount),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (rule.type == "INCOME") IncomeGreen else ExpenseRed
            )
        }
    }
}
