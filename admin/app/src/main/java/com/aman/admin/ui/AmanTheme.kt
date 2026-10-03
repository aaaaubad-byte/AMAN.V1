package com.aman.admin.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AmanCharcoal = Color(0xFF0B0F12)
private val AmanSurface = Color(0xFF111519)
private val AmanSurfaceRaised = Color(0xFF172027)
private val AmanRed = Color(0xFFFC0B39)
private val AmanText = Color(0xFFF5F6F7)
private val AmanMuted = Color(0xFF9BA5AC)
private val AmanBorder = Color(0xFF263139)
private val AmanGreen = Color(0xFF35C78A)

val AmanColors = darkColorScheme(
    primary = AmanRed,
    onPrimary = Color.White,
    secondary = Color(0xFFABB5BC),
    onSecondary = AmanCharcoal,
    background = AmanCharcoal,
    onBackground = AmanText,
    surface = AmanSurface,
    onSurface = AmanText,
    surfaceVariant = AmanSurfaceRaised,
    onSurfaceVariant = AmanMuted,
    error = Color(0xFFFF5A67),
    outline = AmanBorder,
    tertiary = AmanGreen,
)

@Composable
fun AmanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AmanColors, content = content)
}
