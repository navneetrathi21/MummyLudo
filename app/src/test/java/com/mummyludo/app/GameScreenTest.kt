package com.mummyludo.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mummyludo.app.game.GameState
import com.mummyludo.app.game.LudoColor
import com.mummyludo.app.game.LudoEngine
import com.mummyludo.app.game.PlayerType
import com.mummyludo.app.game.Seat
import com.mummyludo.app.nearby.NearbyGameAction
import com.mummyludo.app.nearby.NearbyGameEvent
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
class GameScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val humanSeats = listOf(
        Seat(LudoColor.RED, PlayerType.HUMAN, "Player"),
        Seat(LudoColor.YELLOW, PlayerType.HUMAN, "Opponent"),
    )

    @Test
    fun humanTokenMovesOneRouteAtATime() {
        val engine = LudoEngine { 6 }
        var state by mutableStateOf(engine.newGame(humanSeats).copy(awaitingRoll = false, roll = 6))
        render(engine, state = { state }, onState = { state = it })

        compose.onNodeWithTag("token_red_0").performClick()
        compose.waitUntil(timeoutMillis = 8_000) {
            state.tokens.first { it.id == "R0" }.progress == 0
        }

        assertTrue(state.tokens.any { it.color == LudoColor.RED && it.progress == 0 })
    }

    @Test
    fun nearbySelectDieEventUpdatesState() {
        val engine = LudoEngine { 4 }
        var state by mutableStateOf(
            engine.newGame(humanSeats).copy(
                awaitingRoll = false,
                diceBank = listOf(6, 2),
            ),
        )
        var event by mutableStateOf<NearbyGameEvent?>(
            NearbyGameEvent(1, NearbyGameAction.SelectDie(0)),
        )
        render(engine, state = { state }, nearbyEvent = { event }, onState = { state = it })

        compose.waitUntil(timeoutMillis = 5_000) { state.roll == 6 }
    }

    @Test
    fun nearbyMoveEventAnimatesAndUpdatesState() {
        val engine = LudoEngine { 4 }
        var state by mutableStateOf(engine.newGame(humanSeats).copy(awaitingRoll = false, roll = 6))
        val event = NearbyGameEvent(1, NearbyGameAction.Move(listOf("R0")))
        render(engine, state = { state }, nearbyEvent = { event }, onState = { state = it })

        compose.waitUntil(timeoutMillis = 8_000) {
            state.tokens.first { it.id == "R0" }.progress == 0
        }
        assertEquals(0, state.tokens.first { it.id == "R0" }.progress)
    }

    @Test
    fun botRequestsRollSelectionAndMoveFromNearbyHost() {
        val engine = LudoEngine { 6 }
        val botSeats = humanSeats.mapIndexed { index, seat ->
            if (index == 0) seat.copy(type = PlayerType.BOT) else seat
        }
        var state by mutableStateOf(engine.newGame(botSeats))
        val actions = mutableListOf<NearbyGameAction>()
        render(
            engine,
            state = { state },
            localColor = LudoColor.RED,
            onNearbyAction = { actions += it },
        )

        compose.waitUntil(timeoutMillis = 5_000) { actions.lastOrNull() == NearbyGameAction.Roll }
        compose.runOnIdle {
            state = state.copy(awaitingRoll = false, diceBank = listOf(2, 6), roll = null)
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            actions.any { it is NearbyGameAction.SelectDie && it.bankIndex == 1 }
        }
        compose.runOnIdle {
            state = state.copy(awaitingRoll = false, diceBank = emptyList(), roll = 6)
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            actions.any { it is NearbyGameAction.Move }
        }
    }

    @Test
    fun localBotFallsBackToFirstDieAndAnimatesBestMove() {
        val engine = LudoEngine { 6 }
        val botSeats = listOf(
            Seat(LudoColor.RED, PlayerType.BOT, "Bot"),
            humanSeats[1],
        )
        var state by mutableStateOf(
            engine.newGame(botSeats).copy(awaitingRoll = false, diceBank = listOf(2)),
        )
        render(engine, state = { state }, onState = { state = it })
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) { state.activeSeat == 1 }

        compose.runOnIdle {
            state = state.copy(activeSeat = 0, awaitingRoll = false, diceBank = emptyList(), roll = 6)
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 8_000) {
            state.tokens.any { it.color == LudoColor.RED && it.progress == 0 }
        }
        assertTrue(state.tokens.any { it.color == LudoColor.RED && it.progress == 0 })
    }

    @Test
    fun localBotRollsDiceAutomatically() {
        val engine = LudoEngine { 6 }
        val seats = listOf(
            Seat(LudoColor.RED, PlayerType.BOT, "Bot"),
            humanSeats[1],
        )
        var state by mutableStateOf(engine.newGame(seats))
        render(engine, state = { state }, onState = { state = it })

        compose.waitUntil(timeoutMillis = 8_000) { state.diceBank == listOf(6) }
        assertTrue(state.awaitingRoll)
    }

    @Test
    fun nearbyHumanControlsDispatchActionsAndCloseGame() {
        val engine = LudoEngine { 1 }
        var state by mutableStateOf(
            engine.newGame(humanSeats).copy(awaitingRoll = false, diceBank = listOf(6, 2)),
        )
        val actions = mutableListOf<NearbyGameAction>()
        var autoPlayed = false
        var closed = false
        compose.setContent {
            GameScreen(
                state = state,
                engine = engine,
                onState = { state = it },
                onAutoPlay = { autoPlayed = true },
                autoPlayRunning = false,
                localColor = LudoColor.RED,
                onNearbyAction = { actions += it },
                onClose = { closed = true },
            )
        }

        compose.onNodeWithTag("dice_value_6").performClick()
        assertTrue(actions.contains(NearbyGameAction.SelectDie(0)))
        compose.runOnIdle {
            state = state.copy(diceBank = emptyList(), roll = 6)
        }
        compose.onNodeWithTag("token_red_0").performClick()
        assertTrue(actions.any { it is NearbyGameAction.Move })
        compose.onNodeWithTag("game_auto_play").performClick()
        assertTrue(autoPlayed)
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Keep playing").performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Close game").performClick()
        assertTrue(closed)
    }

    @Test
    fun diceBankRendersEveryFaceAndAllowsHumanSelection() {
        val engine = LudoEngine { 1 }
        var state by mutableStateOf(
            engine.newGame(humanSeats).copy(
                awaitingRoll = false,
                diceBank = (1..6).toList(),
            ),
        )
        render(engine, state = { state }, onState = { state = it })

        (1..6).forEach { value ->
            compose.onNodeWithTag("dice_value_$value").assertIsDisplayed()
        }
        compose.onNodeWithTag("dice_value_6").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { state.roll == 6 }
        compose.onNodeWithContentDescription("Dice value 6, selected")
            .assertIsDisplayed()
    }

    @Test
    fun completedGameDrawsConfettiAndCloses() {
        val engine = LudoEngine { 1 }
        var closed = false
        var show by mutableStateOf(true)
        val completed = engine.newGame(humanSeats).copy(
            completed = true,
            ranking = listOf(LudoColor.RED, LudoColor.YELLOW),
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            if (show) {
                GameScreen(
                    state = completed,
                    engine = engine,
                    onState = {},
                    onAutoPlay = {},
                    autoPlayRunning = false,
                    onClose = { closed = true },
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithTag("game_confetti").assertIsDisplayed()
        compose.onNodeWithText("Red wins!").assertIsDisplayed()
        compose.onNodeWithTag("game_roll_action").performClick()
        assertTrue(closed)
        compose.runOnUiThread { show = false }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun confettiGeometryIsDeterministicAndBounded() {
        val pieces = confettiPieces(progress = 0.5f, width = 400f, height = 600f)

        assertEquals(70, pieces.size)
        assertTrue(pieces.all { it.size in 5f..11f })
        assertTrue(pieces.all { it.alpha in .65f..1f })
        assertEquals(pieces, confettiPieces(progress = 0.5f, width = 400f, height = 600f))
    }

    @Test
    fun confettiDrawScopeRendersWithoutCrash() {
        val bitmap = android.graphics.Bitmap.createBitmap(400, 600, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = androidx.compose.ui.graphics.Canvas(android.graphics.Canvas(bitmap))
        val drawScope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
        drawScope.draw(
            density = androidx.compose.ui.unit.Density(1f),
            layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
            canvas = canvas,
            size = androidx.compose.ui.geometry.Size(400f, 600f),
        ) {
            drawConfetti(0.5f)
        }
    }

    @Test
    fun gameScreenQuitButtonShowsDialogAndInvokesOnQuit() {
        var quit = false
        val engine = LudoEngine { 1 }
        val state = engine.newGame(humanSeats)
        compose.setContent {
            GameScreen(
                state = state,
                engine = engine,
                onState = {},
                onAutoPlay = {},
                autoPlayRunning = false,
                onClose = {},
                onQuit = { quit = true },
            )
        }

        compose.onNodeWithText("Quit").performClick()
        compose.onNodeWithText("Quit Mummy Ludo?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertFalse(quit)

        compose.onNodeWithText("Quit").performClick()
        compose.onNodeWithTag("game_confirm_quit_button").performClick()
        assertTrue(quit)
    }

    private fun render(
        engine: LudoEngine,
        state: () -> GameState,
        nearbyEvent: () -> NearbyGameEvent? = { null },
        localColor: LudoColor? = null,
        onNearbyAction: ((NearbyGameAction) -> Unit)? = null,
        onState: (GameState) -> Unit = {},
    ) {
        compose.setContent {
            GameScreen(
                state = state(),
                engine = engine,
                onState = onState,
                onAutoPlay = {},
                autoPlayRunning = false,
                nearbyEvent = nearbyEvent(),
                localColor = localColor,
                onNearbyAction = onNearbyAction,
                onClose = {},
            )
        }
    }
}
