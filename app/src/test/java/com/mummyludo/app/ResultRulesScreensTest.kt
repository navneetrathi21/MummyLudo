package com.mummyludo.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.mummyludo.app.game.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h1200dp")
class ResultRulesScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rulesScreen_displays_correct_content() {
        var backClicked = false
        composeRule.setContent {
            MummyLudoTheme {
                RulesScreen(onBack = { backClicked = true })
            }
        }

        // Verify title
        composeRule.onNodeWithText("How to play").assertIsDisplayed()

        // Verify rules content exists
        composeRule.onNodeWithText("Roll a 6 to enter a token", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Safe squares cannot be captured", substring = true).assertIsDisplayed()

        // Verify back button
        composeRule.onNodeWithText("‹ Back").assertIsDisplayed().performClick()
        assertTrue(backClicked)
    }

    @Test
    fun result_screen_shows_winner_and_handles_clicks() {
        var againClicked = false
        var homeClicked = false
        val ranking = listOf(LudoColor.RED, LudoColor.YELLOW)
        val dummyState = GameState(
            seats = listOf(
                Seat(LudoColor.RED, PlayerType.HUMAN, "Player 1"),
                Seat(LudoColor.YELLOW, PlayerType.BOT, "Bot 1"),
            ),
            tokens = emptyList(),
            ranking = ranking,
            completed = true,
        )

        composeRule.setContent {
            MummyLudoTheme {
                ResultScreen(
                    state = dummyState,
                    onAgain = { againClicked = true },
                    onHome = { homeClicked = true },
                )
            }
        }

        // Verify winner is displayed
        composeRule.onNodeWithTag("result_winner").assertIsDisplayed()
        composeRule.onNodeWithText("Red wins!", substring = true).assertIsDisplayed()

        // Verify play again button
        composeRule.onNodeWithTag("result_play_again").assertIsDisplayed().performClick()
        assertTrue(againClicked)

        // Verify home button
        composeRule.onNodeWithText("Home").assertIsDisplayed().performClick()
        assertTrue(homeClicked)
    }
}
