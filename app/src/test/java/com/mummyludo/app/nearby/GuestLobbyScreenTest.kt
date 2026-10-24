package com.mummyludo.app.nearby

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.mummyludo.app.game.LudoColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GuestLobbyScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val lobby = NearbyLobbyState(
        sessionId = "session-123",
        joinToken = "join-token-123",
        players = listOf(
            NearbyPlayer("host", "Host", true, LudoColor.RED, true),
            NearbyPlayer("guest", "Guest", false, null, false),
        ),
        botColors = setOf(LudoColor.YELLOW),
    )

    @Test
    fun connectingAndErrorStatesAllowLeavingButNotReadiness() {
        var left = false
        compose.setContent {
            GuestLobbyScreen(
                lobby = null,
                playerId = "guest",
                error = "Could not reach host",
                onBack = { left = true },
                onProfile = { _, _, _ -> throw AssertionError("Profile should not be available") },
            )
        }

        compose.onNodeWithText("Connecting to host…").assertIsDisplayed()
        compose.onNodeWithText("Could not reach host").assertIsDisplayed()
        compose.onNodeWithTag("guest_ready").assertDoesNotExist()
        compose.onNodeWithText("‹ Leave").performClick()
        assertTrue(left)
    }

    @Test
    fun unavailableColorsCannotBeSelectedAndNameIsLimited() {
        val profiles = mutableListOf<Triple<String, LudoColor?, Boolean>>()
        compose.setContent {
            GuestLobbyScreen(lobby, "guest", onBack = {}, onProfile = { name, color, ready ->
                profiles += Triple(name, color, ready)
            })
        }

        compose.onNodeWithTag("guest_ready").assertIsNotEnabled()
        compose.onNodeWithTag("guest_color_red").assertIsNotEnabled()
        compose.onNodeWithTag("guest_color_yellow").assertIsNotEnabled()
        assertTrue(profiles.isEmpty())

        compose.onNodeWithTag("guest_name").performTextReplacement("A name that is much longer than twenty-four characters")
        compose.onNodeWithTag("guest_color_blue").assertIsEnabled().performClick()
        assertEquals(1, profiles.size)
        assertEquals(24, profiles.single().first.length)
        assertEquals(LudoColor.BLUE, profiles.single().second)
        assertFalse(profiles.single().third)
    }

    @Test
    fun selectedColorCanToggleReadyAndDisplaysAllPlayerStates() {
        val readyLobby = lobby.copy(players = lobby.players.map {
            if (it.id == "guest") it.copy(color = LudoColor.GREEN, ready = true) else it
        })
        val profiles = mutableListOf<Triple<String, LudoColor?, Boolean>>()
        compose.setContent {
            GuestLobbyScreen(readyLobby, "guest", onBack = {}, onProfile = { name, color, ready ->
                profiles += Triple(name, color, ready)
            })
        }

        compose.onNodeWithText("Host").assertIsDisplayed()
        compose.onNodeWithText("Not ready").assertIsDisplayed()
        compose.onNodeWithTag("guest_ready").assertIsEnabled().performClick()
        assertEquals(Triple("Guest", LudoColor.GREEN, false), profiles.single())
    }
}
