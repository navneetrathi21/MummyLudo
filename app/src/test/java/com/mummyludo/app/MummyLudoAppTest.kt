package com.mummyludo.app

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.mummyludo.app.game.*
import com.mummyludo.app.nearby.JoinPayload
import com.mummyludo.app.nearby.JoinPayloadCodec
import com.mummyludo.app.nearby.NearbyGameAction
import com.mummyludo.app.nearby.NearbyGameEvent
import com.mummyludo.app.nearby.NearbyLanHost
import com.mummyludo.app.nearby.NearbyLanClient
import com.mummyludo.app.nearby.NearbyLobbyCoordinator
import java.security.SecureRandom
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mummyludo.app.storage.GameRepository
import com.mummyludo.app.storage.SharedUserPreferences
import com.mummyludo.app.storage.UserPreferences
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h1200dp")
class MummyLudoAppTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUpPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(SharedUserPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean(SharedUserPreferences.KEY_HAS_COMPLETED_WELCOME, true).putString(SharedUserPreferences.KEY_PLAYER_NAME, "Player 1").commit()
    }

    @Test
    fun homeRulesAndSystemBackNavigationWorkIncludingDoubleBackExit() {
        var exits = 0
        lateinit var backDispatcher: OnBackPressedDispatcher
        compose.setContent {
            backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            MummyLudoTheme {
                MummyLudoApp(onExitApp = { exits++ })
            }
        }

        compose.onNodeWithText("How to play").performClick()
        compose.onNodeWithText("Exact movement is required to finish.", substring = true).assertIsDisplayed()
        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.onNodeWithText("Mummy Ludo").assertIsDisplayed()

        compose.runOnIdle { backDispatcher.onBackPressed() }
        assertEquals(0, exits)
        compose.runOnIdle { backDispatcher.onBackPressed() }
        assertEquals(1, exits)
    }

    @Test
    fun setupEnforcesFourSeatsSupportsReverseDuelAndClosesStartedGame() {
        compose.setContent {
            MummyLudoTheme {
                MummyLudoApp(providedEngine = LudoEngine { 3 })
            }
        }

        compose.onNodeWithTag("home_new_game").performClick()
        compose.onNodeWithText("Player Red · Bot Yellow").assertIsDisplayed()
        compose.onNodeWithText("Swap").performClick()
        compose.onNodeWithText("Player Yellow · Bot Red").assertIsDisplayed()

        compose.onNodeWithTag("setup_bots_3").performClick()
        compose.onNodeWithTag("setup_humans_3").performClick()
        compose.onNodeWithText("4 active colors · 3 human · 1 bot").assertIsDisplayed()
        compose.onNodeWithTag("setup_bots_3").performClick()
        compose.onNodeWithText("4 active colors · 1 human · 3 bot").assertIsDisplayed()

        compose.onNodeWithTag("setup_start").performClick()
        compose.onNodeWithText("Player 1 · Red").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Keep playing").performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Close game").performClick()
        compose.onNodeWithText("Mummy Ludo").assertIsDisplayed()
    }

    @Test
    fun automaticGameCompletionReturnsToHome() {
        var showApp by mutableStateOf(true)
        compose.setContent {
            if (showApp) {
                MummyLudoTheme {
                    MummyLudoApp(providedEngine = LudoEngine { 3 })
                }
            }
        }
        compose.onNodeWithTag("home_new_game").performClick()
        compose.onNodeWithTag("setup_start").performClick()
        compose.onNodeWithText("Player 1 · Red").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Finish game automatically", useUnmergedTree = true).performClick()

        Thread.sleep(2_000)
        compose.runOnUiThread { showApp = false }
        compose.mainClock.advanceTimeByFrame()
        assertFalse(showApp)
    }

    @Test
    fun gameScreenBackPressDoesNotLeavePageGame() {
        lateinit var backDispatcher: OnBackPressedDispatcher
        val repo = RecordingRepository()
        compose.setContent {
            backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            MummyLudoTheme {
                MummyLudoApp(
                    providedEngine = LudoEngine { 3 },
                    providedRepository = repo,
                )
            }
        }
        compose.onNodeWithTag("home_new_game").performClick()
        compose.onNodeWithTag("setup_start").performClick()
        compose.onNodeWithText("Player 1 · Red").assertIsDisplayed()

        // Test back press on Page.GAME (exercises Page.GAME -> Unit)
        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.onNodeWithText("Player 1 · Red").assertIsDisplayed()
    }

    @Test
    fun nearbyHostRequestsWifiShowsLobbyAndClosesCleanly() {
        var wifiRequests = 0
        var wifiSettings = 0
        compose.setContent {
            MummyLudoTheme {
                MummyLudoApp(
                    wifiName = "Family Wi-Fi",
                    onRequestWifiPermission = { wifiRequests++ },
                    onOpenWifiSettings = { wifiSettings++ },
                )
            }
        }

        compose.onNodeWithTag("home_play_nearby").performClick()
        compose.onNodeWithTag("nearby_host").performClick()
        compose.onNodeWithText("Host lobby").assertIsDisplayed()
        compose.onNodeWithText("Same Wi-Fi Network").assertIsDisplayed()
        compose.onNodeWithText("Share or change Wi-Fi").performClick()
        assertEquals(1, wifiRequests)
        assertEquals(1, wifiSettings)
        compose.onNodeWithText("‹ Close").performClick()
        compose.onNodeWithText("Play nearby").assertIsDisplayed()
        compose.onNodeWithText("‹ Back").performClick()
        compose.onNodeWithText("Mummy Ludo").assertIsDisplayed()
    }

    @Test
    fun nearbyJoinInvokesScannerSettingsAndReportsUnreachableHost() {
        var scans = 0
        var wifiSettings = 0
        val code = JoinPayloadCodec.encode(
            JoinPayload(
                sessionId = "session-123",
                hostAddress = "127.0.0.1",
                port = 1,
                joinToken = "join-token-123",
            ),
        )
        compose.setContent {
            MummyLudoTheme {
                MummyLudoApp(
                    scannedGameCode = code,
                    onScanGameCode = { scans++ },
                    onOpenWifiSettings = { wifiSettings++ },
                )
            }
        }

        compose.onNodeWithTag("home_play_nearby").performClick()
        compose.onNodeWithTag("nearby_join").performClick()
        compose.onNodeWithTag("nearby_scan_camera").performClick()
        compose.onNodeWithText("Open Wi-Fi settings").performClick()
        assertEquals(1, scans)
        assertEquals(1, wifiSettings)
        compose.onNodeWithTag("nearby_join_confirm").performClick()
        compose.onNodeWithText("Game lobby").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 8_000) {
            compose.onAllNodesWithText("Could not reach the host on this Wi-Fi").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("‹ Leave").performClick()
        compose.onNodeWithText("Play nearby").assertIsDisplayed()
        assertTrue(scans > 0)
    }

    @Test
    fun continueGameLoadsSavedMatchAndResumesPlay() {
        val seats = listOf(
            Seat(LudoColor.RED, PlayerType.HUMAN, "Player 1"),
            Seat(LudoColor.YELLOW, PlayerType.BOT, "Bot 1"),
        )
        val engine = LudoEngine { 4 }
        val fakeRepo = object : GameRepository {
            var saved: GameState? = engine.newGame(seats).copy(turn = 7, message = "Turn 7 resumed")
            override fun saveActiveGame(state: GameState, revision: Int) { saved = state }
            override fun loadActiveGame(): GameState? = saved
            override fun clearActiveGame() { saved = null }
            override fun hasActiveGame(): Boolean = saved != null && !saved!!.completed
        }

        compose.setContent {
            MummyLudoTheme {
                MummyLudoApp(providedEngine = engine, providedRepository = fakeRepo)
            }
        }

        compose.onNodeWithTag("home_continue_game").assertIsDisplayed()
        compose.onNodeWithTag("home_continue_game").performClick()
        compose.onNodeWithText("Turn 7 · Turn 7 resumed").assertIsDisplayed()
    }

    @Test
    fun invalidSavedGameRemovesContinueAction() {
        val repository = RecordingRepository(loadResult = null, reportsActive = true)
        compose.setContent {
            MummyLudoTheme {
                MummyLudoApp(providedRepository = repository)
            }
        }

        compose.onNodeWithTag("home_continue_game").performClick()
        compose.onNodeWithTag("home_continue_game").assertDoesNotExist()
        compose.onNodeWithTag("home_new_game").assertIsDisplayed()
    }

    @Test
    fun localRollPersistsUpdatedUnfinishedGame() {
        val repository = RecordingRepository()
        compose.setContent {
            MummyLudoTheme {
                MummyLudoApp(
                    providedEngine = LudoEngine { 6 },
                    providedRepository = repository,
                )
            }
        }

        compose.onNodeWithTag("home_new_game").performClick()
        compose.onNodeWithTag("setup_start").performClick()
        val savesAfterStart = repository.savedStates.size
        compose.onNodeWithTag("game_roll_action").performClick()
        compose.waitUntil(timeoutMillis = 8_000) {
            repository.savedStates.size > savesAfterStart
        }

        assertTrue(repository.savedStates.last().diceBank.contains(6))
        assertTrue(repository.reportsActive)
    }

    @Test
    fun successfulNearbyJoinReceivesLobbyStartAndGameEvent() {
        val coordinator = NearbyLobbyCoordinator()
        val initial = coordinator.createHost("Host")
        val latestLobby = AtomicReference(initial)
        val host = NearbyLanHost(
            initialLobby = initial,
            coordinator = coordinator,
            onLobby = latestLobby::set,
            onStarted = {},
        )
        try {
            val code = JoinPayloadCodec.encode(
                JoinPayload(initial.sessionId, "127.0.0.1", 42871, initial.joinToken),
            )
            compose.setContent {
                MummyLudoTheme {
                    MummyLudoApp(
                        providedRepository = RecordingRepository(),
                        scannedGameCode = code,
                    )
                }
            }

            compose.onNodeWithTag("home_play_nearby").performClick()
            compose.onNodeWithTag("nearby_join").performClick()
            compose.onNodeWithTag("nearby_join_confirm").performClick()
            compose.waitUntil(timeoutMillis = 8_000) {
                latestLobby.get().players.size == 2
            }
            compose.onNodeWithTag("guest_color_yellow").performClick()
            compose.waitUntil(timeoutMillis = 8_000) {
                latestLobby.get().players.any { !it.isHost && it.color == LudoColor.YELLOW }
            }
            compose.onNodeWithTag("guest_ready").performClick()
            compose.waitUntil(timeoutMillis = 8_000) { latestLobby.get().canStart }

            assertTrue(host.startGame())
            compose.waitUntil(timeoutMillis = 8_000) {
                compose.onAllNodesWithText("Host · Red").fetchSemanticsNodes().isNotEmpty()
            }
            host.broadcastGameEvent(NearbyGameEvent(1, NearbyGameAction.Roll, 3))
            Thread.sleep(2_000)
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithText("Close game").performClick()
            compose.onNodeWithText("Mummy Ludo").assertIsDisplayed()
        } finally {
            host.close()
        }
    }

    @Test
    fun hostAuthorizesRollAndReceivesGuestAction() {
        val coordinator = NearbyLobbyCoordinator(FixedSecureRandom())
        val sessionId = "01".repeat(12)
        val joinToken = "01".repeat(24)
        val guestLobby = AtomicReference<com.mummyludo.app.nearby.NearbyLobbyState?>(null)
        val guestEvent = AtomicReference<NearbyGameEvent?>(null)
        val client = NearbyLanClient(
            payload = JoinPayload(sessionId, "127.0.0.1", 42871, joinToken),
            initialName = "Guest",
            onLobby = guestLobby::set,
            onStarted = {},
            onError = {},
            onGameEvent = guestEvent::set,
        )
        try {
            compose.setContent {
                MummyLudoTheme {
                    MummyLudoApp(
                        providedEngine = LudoEngine { 4 },
                        providedRepository = RecordingRepository(),
                        providedNearbyCoordinator = coordinator,
                    )
                }
            }
            compose.onNodeWithTag("home_play_nearby").performClick()
            compose.onNodeWithTag("nearby_host").performClick()
            client.connect()
            compose.waitUntil(timeoutMillis = 8_000) { guestLobby.get()?.players?.size == 2 }
            client.updateProfile("Guest", LudoColor.YELLOW, true)
            compose.waitUntil(timeoutMillis = 8_000) { guestLobby.get()?.canStart == true }
            compose.onNodeWithTag("host_start_game").assertIsEnabled().performClick()
            compose.waitUntil(timeoutMillis = 8_000) {
                compose.onAllNodesWithText("Player 1 · Red").fetchSemanticsNodes().isNotEmpty()
            }

            client.sendGameAction(NearbyGameAction.Roll)
            compose.onNodeWithTag("game_roll_action").performClick()
            compose.waitUntil(timeoutMillis = 8_000) { guestEvent.get()?.rollValue == 4 }
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithText("Close game").performClick()
            compose.onNodeWithText("Mummy Ludo").assertIsDisplayed()
        } finally {
            client.close()
        }
    }

    @Test
    fun systemBackClosesHostJoinAndGuestSubpages() {
        lateinit var backDispatcher: OnBackPressedDispatcher
        val code = JoinPayloadCodec.encode(
            JoinPayload("session-123", "127.0.0.1", 1, "join-token-123"),
        )
        compose.setContent {
            backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            MummyLudoTheme {
                MummyLudoApp(
                    providedRepository = RecordingRepository(),
                    scannedGameCode = code,
                )
            }
        }

        compose.onNodeWithTag("home_play_nearby").performClick()
        compose.onNodeWithTag("nearby_host").performClick()
        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.onNodeWithText("Play nearby").assertIsDisplayed()

        compose.onNodeWithTag("nearby_join").performClick()
        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.onNodeWithText("Play nearby").assertIsDisplayed()

        compose.onNodeWithTag("nearby_join").performClick()
        compose.onNodeWithTag("nearby_join_confirm").performClick()
        compose.onNodeWithText("Game lobby").assertIsDisplayed()
        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.onNodeWithText("Play nearby").assertIsDisplayed()
    }

    @Test
    fun firstLaunchShowsWelcomeScreenAndCompletingItSavesNameAndNavigatesToHome() {
        val userPrefs = FakeUserPreferences(name = "", completed = false)
        compose.setContent {
            MummyLudoTheme {
                MummyLudoApp(
                    providedUserPreferences = userPrefs,
                )
            }
        }

        compose.onNodeWithTag("welcome_screen").assertIsDisplayed()
        compose.onNodeWithTag("welcome_name_input").performTextInput("Cleopatra")
        compose.onNodeWithTag("welcome_continue_button").performClick()

        // Should now be on Home with greeting
        compose.onNodeWithText("Mummy Ludo").assertIsDisplayed()
        compose.onNodeWithTag("home_user_greeting").assertIsDisplayed()
        compose.onNodeWithText("Welcome, Cleopatra!").assertIsDisplayed()

        assertTrue(userPrefs.hasCompletedWelcome())
        assertEquals("Cleopatra", userPrefs.getPlayerName())
    }

    @Test
    fun welcomeScreenBackPressExitsApp() {
        var exits = 0
        lateinit var backDispatcher: OnBackPressedDispatcher
        val userPrefs = FakeUserPreferences(name = "", completed = false)
        compose.setContent {
            backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            MummyLudoTheme {
                MummyLudoApp(
                    providedUserPreferences = userPrefs,
                    onExitApp = { exits++ },
                )
            }
        }

        compose.onNodeWithTag("welcome_screen").assertIsDisplayed()
        compose.runOnIdle { backDispatcher.onBackPressed() }
        assertEquals(1, exits)
    }

    @Test
    fun joinNearbyAllowsManual4DigitCodeEntry() {
        var clearCount = 0
        compose.setContent {
            MummyLudoTheme {
                MummyLudoApp(
                    providedRepository = RecordingRepository(),
                    onClearScannedCode = { clearCount++ },
                )
            }
        }

        compose.onNodeWithTag("home_play_nearby").performClick()
        compose.onNodeWithTag("nearby_join").performClick()
        assertEquals(1, clearCount)
        compose.onNodeWithTag("nearby_manual_code_input").assertIsDisplayed()
        compose.onNodeWithTag("nearby_join_manual_button").assertIsNotEnabled()

        compose.onNodeWithTag("nearby_manual_code_input").performTextInput("1234")
        compose.onNodeWithTag("nearby_join_manual_button").assertIsEnabled()
    }

    private class FakeUserPreferences(
        var name: String = "",
        var completed: Boolean = false,
    ) : UserPreferences {
        override fun getPlayerName(): String = name
        override fun setPlayerName(name: String) { this.name = name.trim() }
        override fun hasCompletedWelcome(): Boolean = completed
        override fun setCompletedWelcome(completed: Boolean) { this.completed = completed }
    }

    private class RecordingRepository(
        private val loadResult: GameState? = null,
        var reportsActive: Boolean = false,
    ) : GameRepository {
        val savedStates = mutableListOf<GameState>()

        override fun saveActiveGame(state: GameState, revision: Int) {
            savedStates += state
            reportsActive = !state.completed
        }

        override fun loadActiveGame(): GameState? = loadResult

        override fun clearActiveGame() {
            reportsActive = false
        }

        override fun hasActiveGame(): Boolean = reportsActive
    }

    private class FixedSecureRandom : SecureRandom() {
        override fun nextBytes(bytes: ByteArray) {
            bytes.fill(1)
        }
    }
}
