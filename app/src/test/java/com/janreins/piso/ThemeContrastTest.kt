package com.janreins.piso

import androidx.compose.ui.graphics.Color
import com.janreins.piso.ui.theme.BackgroundDark
import com.janreins.piso.ui.theme.BackgroundLight
import com.janreins.piso.ui.theme.DarkMoneyColors
import com.janreins.piso.ui.theme.LightMoneyColors
import com.janreins.piso.ui.theme.OutlineDark
import com.janreins.piso.ui.theme.OutlineLight
import com.janreins.piso.ui.theme.PisoMoneyColors
import com.janreins.piso.ui.theme.SurfaceDark
import com.janreins.piso.ui.theme.SurfaceLight
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Amounts must stay easy to read in both themes: money colours need at least 4.5:1 against the
 * cards and page behind them (the usual rule for normal-size text), and field borders 3:1.
 */
class ThemeContrastTest {

    private fun channel(c: Float): Double =
        if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun assertReadable(name: String, text: Color, background: Color, minimum: Double = 4.5) {
        val ratio = contrast(text, background)
        assertTrue("$name contrast is $ratio, needs $minimum", ratio >= minimum)
    }

    private fun checkMoney(theme: String, money: PisoMoneyColors, surface: Color, background: Color) {
        for (behind in listOf(surface, background)) {
            assertReadable("$theme income", money.income, behind)
            assertReadable("$theme expense", money.expense, behind)
            assertReadable("$theme warning", money.warning, behind)
        }
        assertReadable("$theme text on income", money.onIncome, money.income)
        assertReadable("$theme text on expense", money.onExpense, money.expense)
        assertReadable("$theme income on its tint", money.income, money.incomeContainer)
        assertReadable("$theme expense on its tint", money.expense, money.expenseContainer)
    }

    @Test
    fun moneyColoursAreReadableInLightMode() {
        checkMoney("Light", LightMoneyColors, SurfaceLight, BackgroundLight)
    }

    @Test
    fun moneyColoursAreReadableInDarkMode() {
        checkMoney("Dark", DarkMoneyColors, SurfaceDark, BackgroundDark)
    }

    @Test
    fun fieldBordersAreVisibleInBothModes() {
        assertReadable("Light outline", OutlineLight, SurfaceLight, minimum = 3.0)
        assertReadable("Dark outline", OutlineDark, SurfaceDark, minimum = 3.0)
    }
}
