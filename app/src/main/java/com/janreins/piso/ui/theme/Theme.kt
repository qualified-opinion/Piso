package com.janreins.piso.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.janreins.piso.data.local.ThemeMode

// Every colour role is set. Roles left out fall back to Material's purple baseline, which used to
// tint the bottom bar, dialogs, menus, the date picker and switches lavender.
private val DarkColorScheme = darkColorScheme(
    primary = BrandSage,
    onPrimary = BrandInk,
    primaryContainer = BrandPine,
    onPrimaryContainer = BrandMist,
    inversePrimary = BrandForest,
    secondary = BrandSageSoft,
    onSecondary = BrandInk,
    secondaryContainer = Color(0xFF2A4A3C),
    onSecondaryContainer = BrandMist,
    tertiary = BrandMint,
    onTertiary = BrandInk,
    tertiaryContainer = BrandPine,
    onTertiaryContainer = BrandMist,
    background = BackgroundDark,
    onBackground = Color(0xFFE1E3DF),
    surface = SurfaceDark,
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFBFC9C2),
    surfaceTint = BrandSage,
    inverseSurface = Color(0xFFE1E3DF),
    inverseOnSurface = Color(0xFF2E312F),
    error = DarkMoneyColors.expense,
    onError = DarkMoneyColors.onExpense,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    scrim = Color.Black,
    surfaceBright = Color(0xFF37433D),
    surfaceDim = BackgroundDark,
    surfaceContainerLowest = Color(0xFF0C120F),
    surfaceContainerLow = Color(0xFF161F1A),
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceVariantDark,
    surfaceContainerHighest = Color(0xFF2C3A32)
)

private val LightColorScheme = lightColorScheme(
    primary = BrandForest,
    onPrimary = Color.White,
    primaryContainer = BrandContainerLight,
    onPrimaryContainer = OnBrandContainerLight,
    inversePrimary = BrandMint,
    secondary = BrandAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8EBE1),
    onSecondaryContainer = BrandPine,
    tertiary = BrandPine,
    onTertiary = Color.White,
    tertiaryContainer = BrandContainerLight,
    onTertiaryContainer = BrandInk,
    background = BackgroundLight,
    onBackground = Color(0xFF191C1A),
    surface = SurfaceLight,
    onSurface = Color(0xFF191C1A),
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = Color(0xFF49544E),
    surfaceTint = BrandForest,
    inverseSurface = Color(0xFF2E312F),
    inverseOnSurface = Color(0xFFEFF1ED),
    error = LightMoneyColors.expense,
    onError = LightMoneyColors.onExpense,
    errorContainer = LightMoneyColors.expenseContainer,
    onErrorContainer = LightMoneyColors.onExpenseContainer,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    scrim = Color.Black,
    surfaceBright = SurfaceLight,
    surfaceDim = Color(0xFFE6E2DC),
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = Color(0xFFFCFBF9),
    surfaceContainer = Color(0xFFF8F6F2),
    surfaceContainerHigh = Color(0xFFF4F1EC),
    surfaceContainerHighest = Color(0xFFEDE9E3)
)

@Composable
fun PisoTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemInDark
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalPisoMoneyColors provides if (isDark) DarkMoneyColors else LightMoneyColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
