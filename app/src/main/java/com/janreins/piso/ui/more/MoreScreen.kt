package com.janreins.piso.ui.more

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.janreins.piso.ui.components.PisoCard
import com.janreins.piso.ui.components.PisoTopBar
import com.janreins.piso.ui.state.MoreSubScreen
import com.janreins.piso.ui.theme.ExpenseContainer
import com.janreins.piso.ui.theme.ExpenseRed
import com.janreins.piso.ui.theme.IncomeContainer
import com.janreins.piso.ui.theme.IncomeGreen
import com.janreins.piso.ui.theme.TealContainer
import com.janreins.piso.ui.theme.TealPrimary
import com.janreins.piso.util.CurrencyUtil

@Composable
fun MoreScreen(
    viewModel: MoreViewModel,
    onOpenSubScreen: (MoreSubScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val budgets = uiState.budgets
    val debts = uiState.debts
    val investments = uiState.investments
    val totalInvestments = uiState.totalInvestments
    val totalDebtsRemaining = uiState.totalDebtsRemaining

    Scaffold(
        modifier = modifier.testTag("more_screen"),
        topBar = {
            PisoTopBar(title = "More")
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Financial Planning",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // --- Budgets Item ---
            item {
                MoreMenuCard(
                    title = "Budgets",
                    subtitle = "${budgets.size} category budget${if (budgets.size == 1) "" else "s"}",
                    icon = Icons.Default.PieChart,
                    iconBg = TealContainer,
                    iconTint = TealPrimary,
                    onClick = { onOpenSubScreen(MoreSubScreen.BUDGETS) },
                    testTag = "more_budgets_button"
                )
            }

            // --- Recurring Item ---
            item {
                val count = uiState.recurringCount
                MoreMenuCard(
                    title = "Recurring",
                    subtitle = if (count == 0) "Rent, bills and fares, recorded for you" else "$count schedule${if (count == 1) "" else "s"}",
                    icon = Icons.Default.Repeat,
                    iconBg = TealContainer,
                    iconTint = TealPrimary,
                    onClick = { onOpenSubScreen(MoreSubScreen.RECURRING) },
                    testTag = "more_recurring_button"
                )
            }

            // --- Debts Item ---
            item {
                MoreMenuCard(
                    title = "Debts",
                    subtitle = if (debts.isEmpty()) "No debts tracked" else "${CurrencyUtil.formatPeso(totalDebtsRemaining)} remaining",
                    icon = Icons.Default.CreditCard,
                    iconBg = ExpenseContainer,
                    iconTint = ExpenseRed,
                    onClick = { onOpenSubScreen(MoreSubScreen.DEBTS) },
                    testTag = "more_debts_button"
                )
            }

            // --- Invest Item ---
            item {
                MoreMenuCard(
                    title = "Invest",
                    subtitle = if (investments.isEmpty()) "No assets tracked" else "${CurrencyUtil.formatPeso(totalInvestments)} portfolio value",
                    icon = Icons.Default.ShowChart,
                    iconBg = IncomeContainer,
                    iconTint = IncomeGreen,
                    onClick = { onOpenSubScreen(MoreSubScreen.INVEST) },
                    testTag = "more_invest_button"
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Preferences & Data",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // --- Settings Item ---
            item {
                MoreMenuCard(
                    title = "Settings",
                    subtitle = "Backup, restore & clear private data",
                    icon = Icons.Default.Settings,
                    iconBg = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onOpenSubScreen(MoreSubScreen.SETTINGS) },
                    testTag = "more_settings_button"
                )
            }

            // --- App Identity Badge ---
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Piso",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Your private money book",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version 1.0 · 100% Offline & Private",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun MoreMenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    PisoCard(
        onClick = onClick,
        contentPadding = 16.dp,
        modifier = Modifier.testTag(testTag)
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
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
