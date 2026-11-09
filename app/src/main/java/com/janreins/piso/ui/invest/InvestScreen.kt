package com.janreins.piso.ui.invest

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.janreins.piso.data.models.Investment
import com.janreins.piso.ui.components.ConfirmDialog
import com.janreins.piso.ui.components.PisoCard
import com.janreins.piso.ui.components.PisoEmptyState
import com.janreins.piso.ui.components.PisoTopBar
import com.janreins.piso.ui.theme.IncomeContainer
import com.janreins.piso.ui.theme.IncomeGreen
import com.janreins.piso.ui.theme.TealPrimary
import com.janreins.piso.ui.theme.OnTealPrimary
import com.janreins.piso.util.CurrencyUtil
import com.janreins.piso.util.DateUtil

@Composable
fun InvestScreen(
    viewModel: InvestViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val investments = uiState.investments
    val totalInvestments = remember(investments) { investments.sumOf { it.currentValue } }

    var showAddDialog by remember { mutableStateOf(false) }
    var investmentToEdit by remember { mutableStateOf<Investment?>(null) }
    var investmentToDelete by remember { mutableStateOf<Investment?>(null) }
    // The investment being bought (false) or sold (true)
    var investmentMove by remember { mutableStateOf<Pair<Investment, Boolean>?>(null) }

    Scaffold(
        modifier = modifier.testTag("invest_screen"),
        topBar = {
            PisoTopBar(
                title = "Investments",
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("invest_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("invest_add_header_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Investment",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("invest_fab"),
                containerColor = TealPrimary,
                contentColor = OnTealPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Investment")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Summary Header Card
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                PisoCard(
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    borderColor = TealPrimary.copy(alpha = 0.2f),
                    cornerRadius = 24.dp,
                    contentPadding = 20.dp
                ) {
                    Text(
                        text = "Total portfolio value",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = CurrencyUtil.formatPeso(totalInvestments),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    Text(
                        text = "${investments.size} asset${if (investments.size == 1) "" else "s"} tracked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )
                }
            }

            if (investments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    PisoEmptyState(
                        message = "No investments yet – add your first one!",
                        icon = Icons.Default.ShowChart
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(investments, key = { it.id }) { item ->
                        PisoCard(
                            contentPadding = 16.dp,
                            onClick = { investmentToEdit = item }
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
                                            .background(IncomeContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = getInvestIcon(item.kind),
                                            contentDescription = null,
                                            tint = IncomeGreen,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = buildString {
                                                append(item.kind)
                                                if (!item.quantity.isNullOrBlank()) {
                                                    append(" · Qty: ")
                                                    append(item.quantity)
                                                }
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = CurrencyUtil.formatPeso(item.currentValue),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = IncomeGreen
                                            )
                                        )
                                        Text(
                                            text = "Updated ${DateUtil.formatDateShort(item.lastUpdatedMillis)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { investmentToDelete = item },
                                        modifier = Modifier.size(32.dp)
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

                            if (item.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = item.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Buy more from an account, or sell into one: never spending or income
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { investmentMove = item to false },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("invest_buy_button"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Buy more")
                                }
                                OutlinedButton(
                                    onClick = { investmentMove = item to true },
                                    enabled = item.currentValue > 0.0,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("invest_sell_button"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Sell")
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

    // --- Add Investment Dialog ---
    if (showAddDialog) {
        InvestDialog(
            accounts = uiState.accounts,
            onDismiss = { showAddDialog = false },
            onSave = { newInvest ->
                viewModel.addInvestment(newInvest)
                showAddDialog = false
            },
            onSaveFromAccount = { newInvest, accountId, dateMillis ->
                viewModel.addInvestmentFromAccount(newInvest, accountId, dateMillis)
                showAddDialog = false
            }
        )
    }

    // --- Edit Investment Dialog ---
    investmentToEdit?.let { currentInvest ->
        InvestDialog(
            initialInvestment = currentInvest,
            onDismiss = { investmentToEdit = null },
            onSave = { updatedInvest ->
                viewModel.updateInvestment(updatedInvest)
                investmentToEdit = null
            }
        )
    }

    // --- Buy or Sell Dialog ---
    investmentMove?.let { (inv, isSale) ->
        InvestMoveDialog(
            investment = inv,
            isSale = isSale,
            accounts = uiState.accounts,
            onDismiss = { investmentMove = null },
            onConfirm = { amount, accountId, dateMillis, note ->
                if (isSale) {
                    viewModel.sellInvestment(inv.id, amount, accountId, dateMillis, note)
                } else {
                    viewModel.buyInvestment(inv.id, amount, accountId, dateMillis, note)
                }
                investmentMove = null
            }
        )
    }

    // --- Delete Confirmation Dialog ---
    investmentToDelete?.let { invToDelete ->
        ConfirmDialog(
            title = "Delete investment?",
            message = "Are you sure you want to delete ${invToDelete.name}? Its buys and sales stay in Activity as transfers, and your accounts don't change.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteInvestment(invToDelete)
                investmentToDelete = null
            },
            onDismiss = { investmentToDelete = null }
        )
    }
}

private fun getInvestIcon(kind: String): ImageVector {
    return when {
        kind.contains("Crypto", ignoreCase = true) || kind.contains("Bitcoin", ignoreCase = true) -> Icons.Default.CurrencyBitcoin
        kind.contains("Gold", ignoreCase = true) || kind.contains("Silver", ignoreCase = true) -> Icons.Default.MonetizationOn
        else -> Icons.Default.ShowChart
    }
}
