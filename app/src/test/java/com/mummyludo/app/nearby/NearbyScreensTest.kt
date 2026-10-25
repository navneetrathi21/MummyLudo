package com.mummyludo.app.nearby

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
@Config(sdk = [35], qualifiers = "w411dp-h1200dp")
class NearbyScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun entryScreenDisplaysActionsAndInvokesEveryCallback() {
        var backed = false
        var hosted = false
        var joined = false
        compose.setContent {
            NearbyEntryScreen(
                onBack = { backed = true },
                onHost = { hosted = true },
                onJoin = { joined = true },
            )
        }

        compose.onNodeWithText("Play nearby").assertIsDisplayed()
        compose.onNodeWithText("‹ Back").performClick()
        compose.onNodeWithTag("nearby_host").performClick()
        compose.onNodeWithTag("nearby_join").performClick()

        assertTrue(backed)
        assertTrue(hosted)
        assertTrue(joined)
    }

    @Test
    fun incompleteHostLobbyExplainsHiddenWifiAndCannotStart() {
        var closed = false
        var openedWifi = false
        var started = false
        val lobby = lobby(
            NearbyPlayer("host", "Host", true, LudoColor.RED, true),
            NearbyPlayer("guest", "Guest", false, null, false),
        )
        compose.setContent {
            HostLobbyScreen(
                lobby = lobby,
                hostAddress = null,
                wifiName = null,
                onBack = { closed = true },
                onOpenWifiSettings = { openedWifi = true },
                onStart = { started = true },
            )
        }

        compose.onNodeWithText("Same Wi-Fi Network").assertIsDisplayed()
        compose.onNodeWithText("All players must join the same Wi-Fi or hotspot").assertIsDisplayed()
        compose.onNodeWithText("Choosing color").assertExists()
        compose.onNodeWithText("Waiting").assertExists()
        compose.onNodeWithTag("host_game_qr").assertDoesNotExist()
        compose.onNodeWithTag("host_start_game").assertIsNotEnabled().performClick()
        compose.onNodeWithText("‹ Close").performClick()
        compose.onNodeWithText("Share or change Wi-Fi").performClick()

        assertTrue(closed)
        assertTrue(openedWifi)
        assertFalse(started)
    }

    @Test
    fun readyHostLobbyShowsQrEveryColorAndStarts() {
        var started = false
        val lobby = lobby(
            NearbyPlayer("red", "Red player", true, LudoColor.RED, true),
            NearbyPlayer("green", "Green player", false, LudoColor.GREEN, true),
            NearbyPlayer("yellow", "Yellow player", false, LudoColor.YELLOW, true),
            NearbyPlayer("blue", "Blue player", false, LudoColor.BLUE, true),
        )
        compose.setContent {
            HostLobbyScreen(
                lobby = lobby,
                hostAddress = "192.168.1.20",
                wifiName = "Family Wi-Fi",
                onBack = {},
                onOpenWifiSettings = {},
                onStart = { started = true },
            )
        }

        compose.onNodeWithText("Same Wi-Fi Network").assertIsDisplayed()
        compose.onNodeWithText("All players must join the same Wi-Fi or hotspot").assertIsDisplayed()
        compose.onNodeWithTag("host_game_qr").assertIsDisplayed()
        compose.onNodeWithContentDescription("Game join QR code").assertIsDisplayed()
        LudoColor.entries.forEach { compose.onNodeWithText(it.label).assertExists() }
        compose.onNodeWithTag("host_start_game").assertIsEnabled().performClick()

        assertTrue(started)
    }

    @Test
    fun invalidJoinCodeOffersRecoveryWithoutJoinConfirmation() {
        var backed = false
        var openedWifi = false
        var scanned = false
        compose.setContent {
            JoinNearbyScreen(
                initialCode = "not-a-game-code",
                onBack = { backed = true },
                onOpenWifiSettings = { openedWifi = true },
                onScan = { scanned = true },
                onJoin = { throw AssertionError("Invalid code must not be joinable") },
            )
        }

        compose.onNodeWithTag("nearby_join_confirm").assertDoesNotExist()
        compose.onNodeWithText("‹ Back").performClick()
        compose.onNodeWithTag("nearby_scan_camera").performClick()
        compose.onNodeWithText("Open Wi-Fi settings").performClick()

        assertTrue(backed)
        assertTrue(openedWifi)
        assertTrue(scanned)
    }

    @Test
    fun validJoinCodeDisplaysConfirmationAndReturnsDecodedPayload() {
        val expected = JoinPayload(
            sessionId = "session-123",
            hostAddress = "192.168.1.20",
            port = 42871,
            joinToken = "join-token-123",
        )
        var joined: JoinPayload? = null
        compose.setContent {
            JoinNearbyScreen(
                initialCode = JoinPayloadCodec.encode(expected),
                onBack = {},
                onOpenWifiSettings = {},
                onScan = {},
                onJoin = { joined = it },
            )
        }

        compose.onNodeWithText("Game found").assertIsDisplayed()
        compose.onNodeWithTag("nearby_join_confirm").assertIsEnabled().performClick()
        assertEquals(expected, joined)
    }

    @Test
    fun manual4DigitCodeCanBeEnteredAndResolvedToJoinGame() {
        val expected = JoinPayload(
            sessionId = "4821",
            hostAddress = "192.168.1.50",
            port = 42871,
            joinToken = "4821",
        )
        var joined: JoinPayload? = null
        compose.setContent {
            JoinNearbyScreen(
                initialCode = "",
                onBack = {},
                onOpenWifiSettings = {},
                onScan = {},
                onJoin = { joined = it },
                onResolveManualCode = { code ->
                    if (code == "4821") expected else null
                },
            )
        }

        compose.onNodeWithTag("nearby_manual_code_input").assertIsDisplayed()
        compose.onNodeWithTag("nearby_join_manual_button").assertIsNotEnabled()

        // Type 4 digits
        compose.onNodeWithTag("nearby_manual_code_input").performTextInput("4821")
        compose.onNodeWithTag("nearby_join_manual_button").assertIsEnabled().performClick()

        assertEquals(expected, joined)
    }

    @Test
    fun manual4DigitCodeShowsErrorWhenNotFound() {
        compose.setContent {
            JoinNearbyScreen(
                initialCode = "",
                onBack = {},
                onOpenWifiSettings = {},
                onScan = {},
                onJoin = {},
                onResolveManualCode = { null },
            )
        }

        compose.onNodeWithTag("nearby_manual_code_input").performTextInput("9999")
        compose.onNodeWithTag("nearby_join_manual_button").performClick()

        compose.onNodeWithTag("nearby_search_error").assertIsDisplayed()
    }

    @Test
    fun hostLobbyDisplays4DigitRoomCode() {
        val lobby = NearbyLobbyState(
            sessionId = "7392",
            joinToken = "7392",
            players = listOf(NearbyPlayer("host", "Host", true, LudoColor.RED, true)),
            roomCode = "7392",
        )
        compose.setContent {
            HostLobbyScreen(
                lobby = lobby,
                hostAddress = "192.168.1.10",
                wifiName = "Home-Wifi",
                onBack = {},
                onOpenWifiSettings = {},
                onStart = {},
            )
        }

        compose.onNodeWithTag("host_room_code").assertIsDisplayed()
        compose.onNodeWithText("7392").assertIsDisplayed()
        compose.onNodeWithText("Ask players to scan QR or enter 4-digit code").assertIsDisplayed()
    }

    private fun lobby(vararg players: NearbyPlayer) = NearbyLobbyState(
        sessionId = "session-123",
        joinToken = "join-token-123",
        players = players.toList(),
    )
}
