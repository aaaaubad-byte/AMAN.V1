package com.aman.admin.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Keep these semantic tokens aligned with customer/ui/AmanCustomerTheme.kt. The apps
// are separate Gradle builds, so a shared module would add coupling without runtime reuse.
private val AmanBackground = Color(0xFF0B0F12)
private val AmanSurface = Color(0xFF111519)
private val AmanSurfaceRaised = Color(0xFF172027)
private val AmanRed = Color(0xFFFC0B39)
private val AmanText = Color(0xFFF5F6F7)
private val AmanMuted = Color(0xFFADB7BD)
private val AmanBorder = Color(0xFF263139)
private val AmanSuccess = Color(0xFF35C78A)
private val AmanError = Color(0xFFFF6B74)

private val AmanShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
)

private val AmanTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, lineHeight = 28.sp, letterSpacing = 0.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 24.sp, letterSpacing = 0.1.sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.15.sp),
        bodySmall = bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
        labelLarge = labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium = labelMedium.copy(fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.3.sp),
        labelSmall = labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    )
}

private val AmanColors = darkColorScheme(
    primary = AmanRed,
    onPrimary = Color.White,
    secondary = Color(0xFFABB5BC),
    onSecondary = AmanBackground,
    tertiary = AmanSuccess,
    background = AmanBackground,
    onBackground = AmanText,
    surface = AmanSurface,
    onSurface = AmanText,
    surfaceVariant = AmanSurfaceRaised,
    onSurfaceVariant = AmanMuted,
    error = AmanError,
    onError = Color.White,
    outline = AmanBorder,
)

@Composable
fun AmanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AmanColors, typography = AmanTypography, shapes = AmanShapes, content = content)
}
