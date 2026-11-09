package com.janreins.piso.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Piso palette: forest greens on a warm off-white. These raw values feed the colour schemes in
// Theme.kt. Screens use the theme-aware names further down, so light and dark mode both read well.
val BrandForest = Color(0xFF2D6A4F)
val BrandPine = Color(0xFF1B4332)
val BrandAccent = Color(0xFF40916C)
val BrandSage = Color(0xFF52B788)
val BrandSageSoft = Color(0xFF74C69D)
val BrandMint = Color(0xFF95D5B2)
val BrandContainerLight = Color(0xFFE8F3EE)
val OnBrandContainerLight = Color(0xFF1B4332)
val BrandInk = Color(0xFF062016)
val BrandMist = Color(0xFFD8EADF)

// Light mode surfaces (warm off-white)
val BackgroundLight = Color(0xFFFBF9F6)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFF3EFEA)
// Field borders and dimmed text need real contrast (about 4.7:1 on white); hairline dividers and
// card borders use the soft variant.
val OutlineLight = Color(0xFF6B766F)
val OutlineVariantLight = Color(0xFFE5E0D8)

// Dark mode surfaces
val BackgroundDark = Color(0xFF111815)
val SurfaceDark = Color(0xFF1A241F)
val SurfaceVariantDark = Color(0xFF233029)
val OutlineDark = Color(0xFF8D9690)
val OutlineVariantDark = Color(0xFF33443B)

/**
 * Money colours: green for income, red for spending, amber for a budget near its limit.
 * Each has a light and a dark version so the amounts stay easy to read in both themes
 * (at least 4.5:1 against the card behind them).
 */
@Immutable
data class PisoMoneyColors(
    val income: Color,
    val onIncome: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    val expense: Color,
    val onExpense: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color,
    val warning: Color,
    val warningContainer: Color
)

val LightMoneyColors = PisoMoneyColors(
    income = Color(0xFF2D6A4F),
    onIncome = Color.White,
    incomeContainer = Color(0xFFE8F3EE),
    onIncomeContainer = Color(0xFF1B4332),
    expense = Color(0xFFC62E3A),
    onExpense = Color.White,
    expenseContainer = Color(0xFFFDECEE),
    onExpenseContainer = Color(0xFF9E1B24),
    warning = Color(0xFFB45309),
    warningContainer = Color(0xFFFEF3C7)
)

val DarkMoneyColors = PisoMoneyColors(
    income = Color(0xFF74C69D),
    onIncome = Color(0xFF062016),
    incomeContainer = Color(0xFF1F3A2C),
    onIncomeContainer = Color(0xFFD8EADF),
    expense = Color(0xFFFF7B7B),
    onExpense = Color(0xFF3B0A0E),
    expenseContainer = Color(0xFF4A1F23),
    onExpenseContainer = Color(0xFFFEE2E2),
    warning = Color(0xFFF2A33A),
    warningContainer = Color(0xFF4A3510)
)

val LocalPisoMoneyColors = staticCompositionLocalOf { LightMoneyColors }

// Theme-aware names used across the screens. They follow the chosen theme (Light, Dark or System).
val TealPrimary: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.primary

/** Text and icons drawn on a [TealPrimary] background. */
val OnTealPrimary: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onPrimary

val TealContainer: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.primaryContainer

val OnTealContainer: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onPrimaryContainer

val IncomeGreen: Color
    @Composable @ReadOnlyComposable
    get() = LocalPisoMoneyColors.current.income

/** Text and icons drawn on an [IncomeGreen] background. */
val OnIncomeGreen: Color
    @Composable @ReadOnlyComposable
    get() = LocalPisoMoneyColors.current.onIncome

val IncomeContainer: Color
    @Composable @ReadOnlyComposable
    get() = LocalPisoMoneyColors.current.incomeContainer

val OnIncomeContainer: Color
    @Composable @ReadOnlyComposable
    get() = LocalPisoMoneyColors.current.onIncomeContainer

val ExpenseRed: Color
    @Composable @ReadOnlyComposable
    get() = LocalPisoMoneyColors.current.expense

/** Text and icons drawn on an [ExpenseRed] background. */
val OnExpenseRed: Color
    @Composable @ReadOnlyComposable
    get() = LocalPisoMoneyColors.current.onExpense

val ExpenseContainer: Color
    @Composable @ReadOnlyComposable
    get() = LocalPisoMoneyColors.current.expenseContainer

val OnExpenseContainer: Color
    @Composable @ReadOnlyComposable
    get() = LocalPisoMoneyColors.current.onExpenseContainer

val WarningAmber: Color
    @Composable @ReadOnlyComposable
    get() = LocalPisoMoneyColors.current.warning

val WarningContainer: Color
    @Composable @ReadOnlyComposable
    get() = LocalPisoMoneyColors.current.warningContainer
