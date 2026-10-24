package com.mummyludo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.mummyludo.app.game.*
import com.mummyludo.app.nearby.*

internal val Cream = Color(0xFFF7F3EA)
internal val Ink = Color(0xFF26352F)
internal val Sage = Color(0xFF3E6655)
internal val Red = Color(0xFFEF5B67)
internal val Green = Color(0xFF49B675)
internal val Yellow = Color(0xFFF6CB3D)
internal val Blue = Color(0xFF438FE7)

@Composable
internal fun MummyLudoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Sage,
            onPrimary = Color.White,
            background = Cream,
            surface = Color.White,
            onSurface = Ink,
        ),
        content = content,
    )
}
