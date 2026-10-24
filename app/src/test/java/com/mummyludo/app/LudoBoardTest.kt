package com.mummyludo.app

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mummyludo.app.game.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h1200dp")
class LudoBoardTest {

    @get:Rule
    val compose = createComposeRule()

    private val allColors = LudoColor.entries

    private fun createFullState(tokens: List<Token>): GameState {
        val seats = allColors.map { Seat(it, PlayerType.HUMAN, it.label) }
        return GameState(
            seats = seats,
            tokens = tokens,
            turn = 1,
            completed = false,
        )
    }

    @Test
    fun board_renders_all_yard_tokens_for_all_four_colors() {
        val yardTokens = allColors.flatMap { color ->
            (0..3).map { index -> Token(color, index, progress = YARD) }
        }
        val state = createFullState(yardTokens)

        compose.setContent {
            MummyLudoTheme {
                LudoBoard(
                    state = state,
                    visualProgress = emptyMap(),
                    legalIds = emptySet(),
                    onToken = {},
                )
            }
        }

        compose.onNodeWithTag("ludo_board").assertIsDisplayed()
    }

    @Test
    fun board_renders_track_and_home_lane_tokens_for_all_colors() {
        val tokens = mutableListOf<Token>()
        // One token on track for each color
        allColors.forEachIndexed { i, color ->
            tokens.add(Token(color, 0, progress = i * 13))
        }
        // One token in home lane for each color (progress 52..56)
        tokens.add(Token(LudoColor.RED, 1, progress = 52))
        tokens.add(Token(LudoColor.GREEN, 1, progress = 53))
        tokens.add(Token(LudoColor.YELLOW, 1, progress = 54))
        tokens.add(Token(LudoColor.BLUE, 1, progress = 55))

        // Tokens that are finished (should be filtered out)
        tokens.add(Token(LudoColor.RED, 2, progress = FINISHED))
        tokens.add(Token(LudoColor.GREEN, 2, progress = FINISHED))

        // Fill remaining with yard tokens
        tokens.add(Token(LudoColor.RED, 3, progress = YARD))
        tokens.add(Token(LudoColor.GREEN, 3, progress = YARD))
        tokens.add(Token(LudoColor.YELLOW, 2, progress = YARD))
        tokens.add(Token(LudoColor.YELLOW, 3, progress = YARD))
        tokens.add(Token(LudoColor.BLUE, 2, progress = YARD))
        tokens.add(Token(LudoColor.BLUE, 3, progress = YARD))

        val state = createFullState(tokens)

        compose.setContent {
            MummyLudoTheme {
                LudoBoard(
                    state = state,
                    visualProgress = mapOf("R3" to 10), // visualProgress override
                    legalIds = emptySet(),
                    onToken = {},
                )
            }
        }

        compose.onNodeWithTag("ludo_board").assertIsDisplayed()
    }

    @Test
    fun multi_token_spreads_are_exercised_for_two_three_and_four_co_located_tokens() {
        val tokens = listOf(
            // 2 co-located Red tokens at progress 5
            Token(LudoColor.RED, 0, progress = 5),
            Token(LudoColor.RED, 1, progress = 5),
            Token(LudoColor.RED, 2, progress = YARD),
            Token(LudoColor.RED, 3, progress = YARD),

            // 3 co-located Green tokens at progress 10
            Token(LudoColor.GREEN, 0, progress = 10),
            Token(LudoColor.GREEN, 1, progress = 10),
            Token(LudoColor.GREEN, 2, progress = 10),
            Token(LudoColor.GREEN, 3, progress = YARD),

            // 4 co-located Yellow tokens at progress 15
            Token(LudoColor.YELLOW, 0, progress = 15),
            Token(LudoColor.YELLOW, 1, progress = 15),
            Token(LudoColor.YELLOW, 2, progress = 15),
            Token(LudoColor.YELLOW, 3, progress = 15),

            // Single Blue token (siblings.size == 1)
            Token(LudoColor.BLUE, 0, progress = 20),
            Token(LudoColor.BLUE, 1, progress = YARD),
            Token(LudoColor.BLUE, 2, progress = YARD),
            Token(LudoColor.BLUE, 3, progress = YARD),
        )

        val state = createFullState(tokens)

        compose.setContent {
            MummyLudoTheme {
                LudoBoard(
                    state = state,
                    visualProgress = emptyMap(),
                    legalIds = emptySet(),
                    onToken = {},
                )
            }
        }

        compose.onNodeWithTag("ludo_board").assertIsDisplayed()
    }

    @Test
    fun legal_token_highlighting_and_click_dispatches_correct_id() {
        var clickedId: String? = null
        val tokens = listOf(
            Token(LudoColor.RED, 0, progress = YARD),
            Token(LudoColor.RED, 1, progress = 4),
            Token(LudoColor.RED, 2, progress = YARD),
            Token(LudoColor.RED, 3, progress = YARD),
            Token(LudoColor.YELLOW, 0, progress = YARD),
            Token(LudoColor.YELLOW, 1, progress = YARD),
            Token(LudoColor.YELLOW, 2, progress = YARD),
            Token(LudoColor.YELLOW, 3, progress = YARD),
        )
        val seats = listOf(
            Seat(LudoColor.RED, PlayerType.HUMAN, "Player 1"),
            Seat(LudoColor.YELLOW, PlayerType.BOT, "Bot 1"),
        )
        val state = GameState(seats = seats, tokens = tokens)

        compose.setContent {
            MummyLudoTheme {
                LudoBoard(
                    state = state,
                    visualProgress = emptyMap(),
                    legalIds = setOf("R0", "R1"),
                    onToken = { clickedId = it },
                )
            }
        }

        // Test clicking yard token
        compose.onNodeWithTag("token_red_0").assertIsDisplayed().performClick()
        assertEquals("R0", clickedId)

        // Test clicking track token
        compose.onNodeWithTag("token_red_1").assertIsDisplayed().performClick()
        assertEquals("R1", clickedId)
    }

    @Test
    fun board_and_tokens_drawing_functions_render_completely() {
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(android.graphics.Canvas(bitmap))
        val drawScope = CanvasDrawScope()

        // 1. Exercise drawBoard
        drawScope.draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas,
            size = Size(600f, 600f),
        ) {
            drawBoard()
        }

        // 2. Exercise drawPawn
        drawScope.draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas,
            size = Size(600f, 600f),
        ) {
            drawPawn(Offset(300f, 300f), 40f, Color.Red, 1f)
            drawPawn(Offset(300f, 300f), 40f, Color.Blue, 0.72f)
        }

        // 3. Exercise drawTokens with yard, track, home lane, FINISHED tokens, visual progress, legal highlights, and 1, 2, 3, 4 co-located tokens
        val tokens = listOf(
            // Co-located 2 Red tokens
            Token(LudoColor.RED, 0, progress = 10),
            Token(LudoColor.RED, 1, progress = 10),
            // Co-located 3 Green tokens
            Token(LudoColor.GREEN, 0, progress = 20),
            Token(LudoColor.GREEN, 1, progress = 20),
            Token(LudoColor.GREEN, 2, progress = 20),
            // Co-located 4 Yellow tokens
            Token(LudoColor.YELLOW, 0, progress = 30),
            Token(LudoColor.YELLOW, 1, progress = 30),
            Token(LudoColor.YELLOW, 2, progress = 30),
            Token(LudoColor.YELLOW, 3, progress = 30),
            // Blue tokens: yard, single on track, home lane, and finished
            Token(LudoColor.BLUE, 0, progress = YARD),
            Token(LudoColor.BLUE, 1, progress = 40),
            Token(LudoColor.BLUE, 2, progress = 54),
            Token(LudoColor.BLUE, 3, progress = FINISHED),
        )
        val state = createFullState(tokens)

        drawScope.draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas,
            size = Size(600f, 600f),
        ) {
            drawTokens(
                state = state,
                visualProgress = mapOf("B0" to YARD, "B1" to 42),
                legalIds = setOf("R0", "B0"),
            )
        }
    }

    @Test
    fun tokenLocation_covers_track_and_all_home_lanes() {
        // Track 0..51
        val (t0x, t0y) = tokenLocation(Token(LudoColor.RED, 0, progress = 0))
        assertTrue(t0x >= 0f && t0y >= 0f)

        // Home lanes for all 4 colors (progress 52..56)
        val (rx, ry) = tokenLocation(Token(LudoColor.RED, 0, progress = 53))
        assertEquals(2f, rx, 0.01f)
        assertEquals(7f, ry, 0.01f)

        val (gx, gy) = tokenLocation(Token(LudoColor.GREEN, 0, progress = 54))
        assertEquals(7f, gx, 0.01f)
        assertEquals(3f, gy, 0.01f)

        val (yx, yy) = tokenLocation(Token(LudoColor.YELLOW, 0, progress = 55))
        assertEquals(10f, yx, 0.01f)
        assertEquals(7f, yy, 0.01f)

        val (bx, by) = tokenLocation(Token(LudoColor.BLUE, 0, progress = 56))
        assertEquals(7f, bx, 0.01f)
        assertEquals(9f, by, 0.01f)
    }

    @Test
    fun yardTokenLocation_covers_all_colors_and_all_indices() {
        for (color in LudoColor.entries) {
            for (index in 0..3) {
                val (x, y) = yardTokenLocation(Token(color, index, progress = YARD))
                assertTrue(x in 0f..14f)
                assertTrue(y in 0f..14f)
            }
        }
    }

    @Test
    fun colorOf_maps_all_enum_values_to_correct_theme_colors() {
        assertEquals(Red, colorOf(LudoColor.RED))
        assertEquals(Green, colorOf(LudoColor.GREEN))
        assertEquals(Yellow, colorOf(LudoColor.YELLOW))
        assertEquals(Blue, colorOf(LudoColor.BLUE))
    }
}
