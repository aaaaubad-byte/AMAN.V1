package com.aman.customer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val charcoal = Color(0xFF151719)
private val surface = Color(0xFF202326)
private val surfaceRaised = Color(0xFF292D30)
private val red = Color(0xFFD9363E)
private val text = Color(0xFFF5F5F5)
private val muted = Color(0xFFAFB4B8)
private val line = Color(0xFF3B4145)

@Composable
fun CustomerTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(primary = red, onPrimary = Color.White, background = charcoal, onBackground = text,
        surface = surface, onSurface = text, surfaceVariant = surfaceRaised, onSurfaceVariant = muted,
        outline = line, secondary = Color(0xFFBFC4C8), error = Color(0xFFFF777C))
    MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography, content = content)
}
