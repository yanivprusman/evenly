package com.automatelinux.evenly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// "Balanced": a deep ink-teal like the bubble in a spirit level, warm paper-grey surfaces,
// coral for money you owe and fresh green for money coming back to you.

private val Ink = Color(0xFF12524D)
private val InkDeep = Color(0xFF0B3B37)
private val Mint = Color(0xFF7FD6C6)

private val LightScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3ECE6),
    onPrimaryContainer = InkDeep,
    secondary = Color(0xFF5E6B67),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E2DA),
    onSecondaryContainer = Color(0xFF2A302E),
    tertiary = Color(0xFFB9642F),
    background = Color(0xFFF5F3EE),
    onBackground = Color(0xFF1C2120),
    surface = Color(0xFFF5F3EE),
    onSurface = Color(0xFF1C2120),
    surfaceVariant = Color(0xFFEAE6DE),
    onSurfaceVariant = Color(0xFF5F6663),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFBF8),
    surfaceContainer = Color(0xFFF0EDE6),
    surfaceContainerHigh = Color(0xFFEAE6DE),
    surfaceContainerHighest = Color(0xFFE3DED5),
    outline = Color(0xFFC9C3B8),
    outlineVariant = Color(0xFFE2DDD3),
    error = Color(0xFFC0392B),
)

private val DarkScheme = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF00302B),
    primaryContainer = Color(0xFF1E4A45),
    onPrimaryContainer = Color(0xFFCFF2EA),
    secondary = Color(0xFFB2BDB9),
    onSecondary = Color(0xFF1D2522),
    secondaryContainer = Color(0xFF2C3431),
    onSecondaryContainer = Color(0xFFDDE5E1),
    tertiary = Color(0xFFF0A36F),
    background = Color(0xFF101514),
    onBackground = Color(0xFFE3E7E5),
    surface = Color(0xFF101514),
    onSurface = Color(0xFFE3E7E5),
    surfaceVariant = Color(0xFF232A28),
    onSurfaceVariant = Color(0xFFA3ACA8),
    surfaceContainerLowest = Color(0xFF0B0F0E),
    surfaceContainerLow = Color(0xFF171D1C),
    surfaceContainer = Color(0xFF1B2220),
    surfaceContainerHigh = Color(0xFF232A28),
    surfaceContainerHighest = Color(0xFF2B3330),
    outline = Color(0xFF46504D),
    outlineVariant = Color(0xFF2E3634),
    error = Color(0xFFFF8A7A),
)

@Immutable
data class MoneyColors(
    val owe: Color,       // you owe
    val oweSoft: Color,
    val owed: Color,      // you are owed
    val owedSoft: Color,
    val settled: Color,
    val card: Color,      // raised card surface
    val hero: Color,      // header band behind the overall balance
    val onHero: Color,
)

private val LightMoney = MoneyColors(
    owe = Color(0xFFD9542B), oweSoft = Color(0xFFFBE4DA),
    owed = Color(0xFF1B8A5E), owedSoft = Color(0xFFD9F1E4),
    settled = Color(0xFF7C8784),
    card = Color(0xFFFFFFFF),
    hero = Ink, onHero = Color.White,
)

private val DarkMoney = MoneyColors(
    owe = Color(0xFFFF8A65), oweSoft = Color(0xFF3D2620),
    owed = Color(0xFF5CD6A0), owedSoft = Color(0xFF173A2B),
    settled = Color(0xFF8E9894),
    card = Color(0xFF1A201F),
    hero = Color(0xFF173C38), onHero = Color(0xFFE6F5F1),
)

val LocalMoneyColors = staticCompositionLocalOf { LightMoney }

object Evenly {
    val money: MoneyColors @Composable get() = LocalMoneyColors.current
}

private val base = Typography()

// Tabular numerals so columns of amounts line up and don't jitter while typing.
val MoneyLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum", letterSpacing = (-0.5).sp)
val MoneyMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
val MoneySmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")

private val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMoneyColors provides if (dark) DarkMoney else LightMoney) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
