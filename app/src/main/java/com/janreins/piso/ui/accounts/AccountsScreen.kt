package com.janreins.piso.ui.accounts

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Wallet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.janreins.piso.data.models.Account
import com.janreins.piso.ui.components.ConfirmDialog
import com.janreins.piso.ui.components.PisoCard
import com.janreins.piso.ui.components.PisoEmptyState
import com.janreins.piso.ui.components.PisoTopBar
import com.janreins.piso.ui.theme.TealContainer
import com.janreins.piso.ui.theme.TealPrimary
import com.janreins.piso.util.CurrencyUtil

@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val accounts = uiState.accounts
    val transactions = uiState.transactions
    val totalBalance = remember(accounts) { accounts.sumOf { it.balance } }

    var showAddDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<Account?>(null) }
    var accountToDelete by remember { mutableStateOf<Account?>(null) }
    var accountToAdjust by remember { mutableStateOf<Account?>(null) }

    val groupedAccounts = remember(accounts) {
        accounts.groupBy { it.kind }
    }

    Scaffold(
        modifier = modifier.testTag("accounts_screen"),
        topBar = {
            PisoTopBar(
                title = "Accounts",
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("account_add_header_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Account",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("accounts_fab"),
                containerColor = TealPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Account")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- Summary Header Card ---
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                PisoCard(
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    borderColor = TealPrimary.copy(alpha = 0.2f),
                    cornerRadius = 24.dp,
                    contentPadding = 20.dp
                ) {
                    Text(
                        text = "Total in Accounts",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = CurrencyUtil.formatPeso(totalBalance),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    Text(
                        text = "${accounts.size} account${if (accounts.size == 1) "" else "s"} tracked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )
                }
            }

            if (accounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    PisoEmptyState(
                        message = "No accounts yet – add cash, a bank account, or GCash.",
                        icon = Icons.Default.AccountBalance
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    groupedAccounts.forEach { (kind, kindAccounts) ->
                        item {
                            Text(
                                text = kind,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        items(kindAccounts, key = { it.id }) { account ->
                            PisoCard(
                                contentPadding = 16.dp,
                                onClick = { accountToEdit = account }
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
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(TealContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = getAccountIcon(account.kind),
                                                contentDescription = null,
                                                tint = TealPrimary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = account.name,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            if (account.notes.isNotBlank()) {
                                                Text(
                                                    text = account.notes,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = CurrencyUtil.formatPeso(account.balance),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = {
                                                val hasTransactions = transactions.any {
                                                    it.accountId == account.id || it.transferToId == account.id
                                                }
                                                if (hasTransactions) {
                                                    viewModel.showMessage("Move or delete this account’s activity first.")
                                                } else {
                                                    accountToDelete = account
                                                }
                                            },
                                            modifier = Modifier.size(32.dp).testTag("account_delete_${account.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.outline,
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

    // --- Add Account Dialog ---
    if (showAddDialog) {
        AccountDialog(
            onDismiss = { showAddDialog = false },
            onSave = { newAccount ->
                viewModel.addAccount(newAccount)
                showAddDialog = false
            }
        )
    }

    // --- Edit Account Dialog ---
    accountToEdit?.let { currentAccount ->
        AccountDialog(
            initialAccount = currentAccount,
            onDismiss = { accountToEdit = null },
            onSave = { updatedAccount ->
                viewModel.updateAccount(updatedAccount)
                accountToEdit = null
            },
            onAdjustBalance = {
                accountToEdit = null
                accountToAdjust = currentAccount
            }
        )
    }

    // --- Adjust Balance Dialog ---
    accountToAdjust?.let { targetAccount ->
        AdjustBalanceDialog(
            account = targetAccount,
            onDismiss = { accountToAdjust = null },
            onConfirm = { newBalance, dateMillis ->
                viewModel.adjustBalance(targetAccount.id, newBalance, dateMillis)
                accountToAdjust = null
            }
        )
    }

    // --- Delete Confirmation Dialog ---
    accountToDelete?.let { accToDelete ->
        ConfirmDialog(
            title = "Delete Account?",
            message = "Are you sure you want to delete ${accToDelete.name}?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteAccount(accToDelete)
                accountToDelete = null
            },
            onDismiss = { accountToDelete = null }
        )
    }
}

private fun getAccountIcon(kind: String): ImageVector {
    return when {
        kind.contains("Cash", ignoreCase = true) -> Icons.Default.Payments
        kind.contains("Wallet", ignoreCase = true) || kind.contains("GCash", ignoreCase = true) -> Icons.Default.PhoneAndroid
        kind.contains("Savings", ignoreCase = true) -> Icons.Default.Savings
        kind.contains("Bank", ignoreCase = true) -> Icons.Default.AccountBalance
        else -> Icons.Default.Wallet
    }
}
