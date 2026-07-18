package com.mummyludo.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class MummyLudoUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun newGameCanBeConfiguredAndCompleted() {
        compose.onNodeWithTag("home_new_game").performClick()
        compose.onNodeWithTag("setup_humans_1").performClick()
        compose.onNodeWithTag("setup_bots_3").performClick()
        compose.onNodeWithTag("setup_start").assertIsEnabled().performClick()
        compose.onNodeWithTag("ludo_board").assertIsDisplayed()
        compose.onNodeWithTag("game_auto_play").performClick()
        compose.onNodeWithTag("result_winner").assertIsDisplayed()
        compose.onNodeWithTag("result_play_again").assertIsDisplayed()
    }

    @Test fun soloAlwaysHasBotAndCloseDiscardsGame() {
        compose.onNodeWithTag("home_new_game").performClick()
        compose.onNodeWithText("1 human · 1 bot", substring = true).assertExists()
        compose.onNodeWithTag("setup_start").performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("This match will be discarded.").assertIsDisplayed()
        compose.onNodeWithText("Close game").performClick()
        compose.onNodeWithTag("home_new_game").assertIsDisplayed()
    }
}
