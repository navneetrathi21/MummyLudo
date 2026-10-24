package com.mummyludo.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h1200dp")
class WelcomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun welcomeScreenRendersElementsAndValidatesName() {
        var submittedName: String? = null

        compose.setContent {
            MummyLudoTheme {
                WelcomeScreen(
                    initialName = "",
                    onContinue = { submittedName = it },
                )
            }
        }

        compose.onNodeWithTag("welcome_screen").assertIsDisplayed()
        compose.onNodeWithText("Welcome to Mummy Ludo").assertIsDisplayed()
        compose.onNodeWithText("A fair, tactical board game for everyone.", substring = true).assertIsDisplayed()

        // Continue button should be disabled when empty
        compose.onNodeWithTag("welcome_continue_button").assertIsNotEnabled()

        // Typing whitespace still keeps it disabled
        compose.onNodeWithTag("welcome_name_input").performTextInput("   ")
        compose.onNodeWithTag("welcome_continue_button").assertIsNotEnabled()

        // Typing valid name enables button
        compose.onNodeWithTag("welcome_name_input").performTextClearance()
        compose.onNodeWithTag("welcome_name_input").performTextInput("  Alex  ")
        compose.onNodeWithTag("welcome_continue_button").assertIsEnabled().performClick()

        assertEquals("Alex", submittedName)
    }

    @Test
    fun welcomeScreenImeActionSubmitsWhenValid() {
        var submittedName: String? = null

        compose.setContent {
            MummyLudoTheme {
                WelcomeScreen(
                    initialName = "Cleo",
                    onContinue = { submittedName = it },
                )
            }
        }

        compose.onNodeWithTag("welcome_continue_button").assertIsEnabled()
        compose.onNodeWithTag("welcome_name_input").performImeAction()

        assertEquals("Cleo", submittedName)
    }
}
