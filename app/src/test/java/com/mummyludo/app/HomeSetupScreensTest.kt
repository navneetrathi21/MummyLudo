package com.mummyludo.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h1200dp")
class HomeSetupScreensTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `home screen without saved game shows new game as primary button`() {
        var newGameClicked = false
        var nearbyClicked = false
        var rulesClicked = false

        compose.setContent {
            MummyLudoTheme {
                HomeScreen(
                    hasSavedGame = false,
                    onNewGame = { newGameClicked = true },
                    onNearby = { nearbyClicked = true },
                    onRules = { rulesClicked = true },
                )
            }
        }

        compose.onNodeWithText("Mummy Ludo").assertIsDisplayed()
        compose.onNodeWithText("A fair game for everyone").assertIsDisplayed()
        compose.onNodeWithTag("home_new_game").assertIsDisplayed().performClick()
        assertTrue(newGameClicked)

        compose.onNodeWithTag("home_play_nearby").assertIsDisplayed().performClick()
        assertTrue(nearbyClicked)

        compose.onNodeWithText("How to play").assertIsDisplayed().performClick()
        assertTrue(rulesClicked)
    }

    @Test
    fun `home screen with saved game shows continue game button`() {
        var continueClicked = false
        var newGameClicked = false

        compose.setContent {
            MummyLudoTheme {
                HomeScreen(
                    hasSavedGame = true,
                    onContinueGame = { continueClicked = true },
                    onNewGame = { newGameClicked = true },
                    onNearby = {},
                    onRules = {},
                )
            }
        }

        compose.onNodeWithTag("home_continue_game").assertIsDisplayed().performClick()
        assertTrue(continueClicked)

        compose.onNodeWithTag("home_new_game").assertIsDisplayed().performClick()
        assertTrue(newGameClicked)
    }

    @Test
    fun `setup screen updates counts, toggles reverse duel, and triggers start`() {
        var humans = 1
        var bots = 1
        var reversed = false
        var started = false
        var backed = false

        compose.setContent {
            MummyLudoTheme {
                SetupScreen(
                    humanCount = humans,
                    botCount = bots,
                    reverseDuel = reversed,
                    onHumans = { humans = it },
                    onBots = { bots = it },
                    onReverse = { reversed = !reversed },
                    onBack = { backed = true },
                    onStart = { started = true },
                )
            }
        }

        compose.onNodeWithText("Match setup").assertIsDisplayed()
        compose.onNodeWithText("Player Red · Bot Yellow").assertIsDisplayed()

        compose.onNodeWithText("Swap").performClick()
        assertTrue(reversed)

        compose.onNodeWithTag("setup_humans_2").performClick()
        assertEquals(2, humans)

        compose.onNodeWithTag("setup_bots_2").performClick()
        assertEquals(2, bots)

        compose.onNodeWithTag("setup_start").performClick()
        assertTrue(started)

        compose.onNodeWithText("‹ Back").performClick()
        assertTrue(backed)
    }
}
