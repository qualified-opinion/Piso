package com.janreins.piso.ui.debts

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.janreins.piso.data.models.Debt
import com.janreins.piso.ui.components.ConfirmDialog
import com.janreins.piso.ui.components.PisoCard
import com.janreins.piso.ui.components.PisoEmptyState
import com.janreins.piso.ui.components.PisoTopBar
import com.janreins.piso.ui.components.ProgressBarWithPercent
import com.janreins.piso.ui.theme.ExpenseContainer
import com.janreins.piso.ui.theme.ExpenseRed
import com.janreins.piso.ui.theme.IncomeGreen
import com.janreins.piso.ui.theme.TealPrimary
import com.janreins.piso.util.CurrencyUtil

@Composable
fun DebtsScreen(
    viewModel: DebtsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val debts = uiState.debts
    val accounts = uiState.accounts

    val totalRemainingDebt = remember(debts) { debts.sumOf { it.remainingAmount } }

    var showAddDialog by remember { mutableStateOf(false) }
    var debtToEdit by remember { mutableStateOf<Debt?>(null) }
    var debtToDelete by remember { mutableStateOf<Debt?>(null) }
    var debtToRecordPayment by remember { mutableStateOf<Debt?>(null) }

    Scaffold(
        modifier = modifier.testTag("debts_screen"),
        topBar = {
            PisoTopBar(
                title = "Debts",
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("debts_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("debt_add_header_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Debt",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("debts_fab"),
                containerColor = TealPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Debt")
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
                    backgroundColor = ExpenseContainer,
                    borderColor = ExpenseRed.copy(alpha = 0.2f),
                    cornerRadius = 24.dp,
                    contentPadding = 20.dp
                ) {
                    Text(
                        text = "Total Remaining Debt",
                        style = MaterialTheme.typography.labelMedium,
                        color = ExpenseRed
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = CurrencyUtil.formatPeso(totalRemainingDebt),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed
                        )
                    )
                    Text(
                        text = "${debts.count { !it.isPaidOff }} active debt${if (debts.count { !it.isPaidOff } == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (debts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    PisoEmptyState(
                        message = "No debts tracked.",
                        icon = Icons.Default.CreditCard
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(debts, key = { it.id }) { debt ->
                        val paidAmount = kotlin.math.max(0.0, debt.originalAmount - debt.remainingAmount)
                        val fractionPaid = if (debt.originalAmount > 0) (paidAmount / debt.originalAmount).toFloat() else 0f

                        PisoCard(
                            contentPadding = 16.dp,
                            borderColor = if (debt.isPaidOff) IncomeGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
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
                                    if (debt.isPaidOff) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Paid Off",
                                            tint = IncomeGreen,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Column {
                                        Text(
                                            text = debt.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = debt.kind,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { debtToEdit = debt },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { debtToDelete = debt },
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

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${CurrencyUtil.formatPeso(debt.remainingAmount)} remaining",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (debt.isPaidOff) IncomeGreen else ExpenseRed
                                    )
                                )
                                Text(
                                    text = "${(fractionPaid * 100).toInt()}% paid",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            ProgressBarWithPercent(
                                fraction = fractionPaid,
                                showLabel = false,
                                customColor = if (debt.isPaidOff) IncomeGreen else TealPrimary
                            )

                            if (debt.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = debt.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!debt.isPaidOff) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { debtToRecordPayment = debt },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("record_debt_payment_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TealPrimary,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Record payment", fontSize = 14.sp)
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

    // --- Add Debt Dialog ---
    if (showAddDialog) {
        DebtDialog(
            onDismiss = { showAddDialog = false },
            onSave = { newDebt ->
                viewModel.addDebt(newDebt)
                showAddDialog = false
            }
        )
    }

    // --- Edit Debt Dialog ---
    debtToEdit?.let { currentDebt ->
        DebtDialog(
            initialDebt = currentDebt,
            onDismiss = { debtToEdit = null },
            onSave = { updatedDebt ->
                viewModel.updateDebt(updatedDebt)
                debtToEdit = null
            }
        )
    }

    // --- Record Payment Dialog ---
    debtToRecordPayment?.let { targetDebt ->
        DebtPaymentDialog(
            debt = targetDebt,
            accounts = accounts,
            onDismiss = { debtToRecordPayment = null },
            onConfirmPayment = { amount, fromAccId, dateMillis ->
                viewModel.recordDebtPayment(targetDebt.id, amount, fromAccId, dateMillis)
                debtToRecordPayment = null
            }
        )
    }

    // --- Delete Confirmation Dialog ---
    debtToDelete?.let { dToDelete ->
        ConfirmDialog(
            title = "Delete Debt?",
            message = "Are you sure you want to delete ${dToDelete.name}? Recorded payment transactions will remain in Activity.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteDebt(dToDelete)
                debtToDelete = null
            },
            onDismiss = { debtToDelete = null }
        )
    }
}
