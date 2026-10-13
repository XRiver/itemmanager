package com.riverxu.itemmanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BrandGreen = Color(0xFF1D6F5C)
private val BrandGreenLight = Color(0xFF7FD8C0)
private val BrandTeal = Color(0xFF4A635C)
private val BrandSand = Color(0xFFE7F2EE)

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6EFDD),
    onPrimaryContainer = Color(0xFF002019),
    secondary = BrandTeal,
    onSecondary = Color.White,
    secondaryContainer = BrandSand,
    onSecondaryContainer = Color(0xFF0A1F1A),
    tertiary = Color(0xFF8A6B3F),
    onTertiary = Color.White,
    background = Color(0xFFF7FAF9),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF7FAF9),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDBE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    // 必须显式定义 surface container 色阶：否则 Material3 会退回默认的紫色基线调色板，
    // 卡片等组件会出现与本应用绿色主题不搭的粉紫色
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F6F4),
    surfaceContainer = Color(0xFFECF1EF),
    surfaceContainerHigh = Color(0xFFE6EBE9),
    surfaceContainerHighest = Color(0xFFE0E5E3),
    surfaceDim = Color(0xFFD8DDDB),
    surfaceBright = Color(0xFFF8FCFA),
    outline = Color(0xFF6F7976),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = BrandGreenLight,
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF005142),
    onPrimaryContainer = Color(0xFFB6EFDD),
    secondary = Color(0xFFB1CCC4),
    onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF334B46),
    onSecondaryContainer = Color(0xFFCDE9E0),
    tertiary = Color(0xFFE0C08C),
    onTertiary = Color(0xFF422C05),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0F1513),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
    surfaceContainerLowest = Color(0xFF0A0F0E),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B29),
    surfaceContainerHighest = Color(0xFF303634),
    surfaceDim = Color(0xFF0F1513),
    surfaceBright = Color(0xFF353B39),
    outline = Color(0xFF899390),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** 过期状态配色，供列表与详情共用。 */
object ExpiryColors {
    val safe = Color(0xFF2E7D32)
    val soon = Color(0xFFEF6C00)
    val urgent = Color(0xFFD84315)
    val expired = Color(0xFFC62828)
}

@Composable
fun OpenItemManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
