package com.mummyludo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun themeProvidesTheApprovedPaletteAndRendersContent() {
        var primary = Color.Unspecified
        var onPrimary = Color.Unspecified
        var background = Color.Unspecified
        var surface = Color.Unspecified
        var onSurface = Color.Unspecified

        compose.setContent {
            MummyLudoTheme {
                primary = MaterialTheme.colorScheme.primary
                onPrimary = MaterialTheme.colorScheme.onPrimary
                background = MaterialTheme.colorScheme.background
                surface = MaterialTheme.colorScheme.surface
                onSurface = MaterialTheme.colorScheme.onSurface
                Text("Themed content")
            }
        }

        compose.onNodeWithText("Themed content").assertIsDisplayed()
        assertEquals(Sage, primary)
        assertEquals(Color.White, onPrimary)
        assertEquals(Cream, background)
        assertEquals(Color.White, surface)
        assertEquals(Ink, onSurface)
        assertEquals(Color(0xFFEF5B67), Red)
        assertEquals(Color(0xFF49B675), Green)
        assertEquals(Color(0xFFF6CB3D), Yellow)
        assertEquals(Color(0xFF438FE7), Blue)
    }
}
