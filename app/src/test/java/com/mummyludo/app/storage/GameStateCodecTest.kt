package com.mummyludo.app.storage

import com.mummyludo.app.game.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameStateCodecTest {

    private val engine = LudoEngine()

    @Test
    fun `initial game state round trips through codec`() {
        val seats = listOf(
            Seat(LudoColor.RED, PlayerType.HUMAN, "Alice"),
            Seat(LudoColor.YELLOW, PlayerType.BOT, "Bot 1"),
        )
        val initial = engine.newGame(seats)
        val json = GameStateCodec.encode(initial)
        val decoded = GameStateCodec.decode(json)

        assertNotNull(decoded)
        assertEquals(initial.seats, decoded?.seats)
        assertEquals(initial.tokens, decoded?.tokens)
        assertEquals(initial.pairs, decoded?.pairs)
        assertEquals(initial.activeSeat, decoded?.activeSeat)
        assertEquals(initial.pendingBonus, decoded?.pendingBonus)
        assertEquals(initial.consecutiveSixes, decoded?.consecutiveSixes)
        assertEquals(initial.diceBank, decoded?.diceBank)
        assertEquals(initial.awaitingRoll, decoded?.awaitingRoll)
        assertEquals(initial.roll, decoded?.roll)
        assertEquals(initial.ranking, decoded?.ranking)
        assertEquals(initial.turn, decoded?.turn)
        assertEquals(initial.message, decoded?.message)
        assertEquals(initial.completed, decoded?.completed)
    }

    @Test
    fun `mid game state with dice bank and pairs round trips`() {
        val seats = listOf(
            Seat(LudoColor.RED, PlayerType.HUMAN, "Alice"),
            Seat(LudoColor.GREEN, PlayerType.HUMAN, "Bob"),
            Seat(LudoColor.YELLOW, PlayerType.BOT, "Bot 1"),
            Seat(LudoColor.BLUE, PlayerType.BOT, "Bot 2"),
        )
        val state = GameState(
            seats = seats,
            tokens = listOf(
                Token(LudoColor.RED, 0, 15),
                Token(LudoColor.RED, 1, 15),
                Token(LudoColor.RED, 2, 54),
                Token(LudoColor.RED, 3, FINISHED),
                Token(LudoColor.GREEN, 0, YARD),
                Token(LudoColor.GREEN, 1, 0),
                Token(LudoColor.GREEN, 2, 20),
                Token(LudoColor.GREEN, 3, YARD),
                Token(LudoColor.YELLOW, 0, YARD),
                Token(LudoColor.YELLOW, 1, YARD),
                Token(LudoColor.YELLOW, 2, YARD),
                Token(LudoColor.YELLOW, 3, YARD),
                Token(LudoColor.BLUE, 0, YARD),
                Token(LudoColor.BLUE, 1, YARD),
                Token(LudoColor.BLUE, 2, YARD),
                Token(LudoColor.BLUE, 3, YARD),
            ),
            pairs = setOf("R0:R1"),
            activeSeat = 1,
            pendingBonus = 2,
            consecutiveSixes = 1,
            diceBank = listOf(6, 4),
            awaitingRoll = false,
            roll = 2,
            ranking = listOf(LudoColor.RED),
            turn = 8,
            message = "Choose a token for 2",
            completed = false,
        )

        val json = GameStateCodec.encode(state)
        val decoded = GameStateCodec.decode(json)

        assertNotNull(decoded)
        assertEquals(state, decoded)
    }

    @Test
    fun `completed game state round trips`() {
        val seats = listOf(
            Seat(LudoColor.RED, PlayerType.HUMAN, "Alice"),
            Seat(LudoColor.YELLOW, PlayerType.BOT, "Bot"),
        )
        val state = GameState(
            seats = seats,
            tokens = seats.flatMap { seat -> List(4) { Token(seat.color, it, FINISHED) } },
            ranking = listOf(LudoColor.RED, LudoColor.YELLOW),
            turn = 42,
            completed = true,
            message = "Red wins!",
            awaitingRoll = false,
        )

        val json = GameStateCodec.encode(state)
        val decoded = GameStateCodec.decode(json)

        assertNotNull(decoded)
        assertEquals(state, decoded)
        assertTrue(decoded!!.completed)
    }

    @Test
    fun `corrupt and malformed JSON returns null safely`() {
        assertNull(GameStateCodec.decode(""))
        assertNull(GameStateCodec.decode("not a json"))
        assertNull(GameStateCodec.decode("{}"))
        assertNull(GameStateCodec.decode("""{"seats": []}"""))
        // Seats missing fields
        assertNull(GameStateCodec.decode("""{"seats": [{"color": "RED"}], "tokens": []}"""))
        // Invalid progress (> FINISHED)
        assertNull(GameStateCodec.decode(
            """{
                "seats": [{"color": "RED", "type": "HUMAN", "name": "P1"}, {"color": "YELLOW", "type": "BOT", "name": "B1"}],
                "tokens": [{"color": "RED", "index": 0, "progress": 999}],
                "activeSeat": 0, "pendingBonus": 0, "consecutiveSixes": 0, "awaitingRoll": true, "turn": 1
            }"""
        ))
        // Invalid progress (< YARD)
        assertNull(GameStateCodec.decode(
            """{
                "seats": [{"color": "RED", "type": "HUMAN", "name": "P1"}, {"color": "YELLOW", "type": "BOT", "name": "B1"}],
                "tokens": [{"color": "RED", "index": 0, "progress": -5}],
                "activeSeat": 0, "pendingBonus": 0, "consecutiveSixes": 0, "awaitingRoll": true, "turn": 1
            }"""
        ))
        // Invalid activeSeat out of range
        val validSeats = """[{"color": "RED", "type": "HUMAN", "name": "P1"}, {"color": "YELLOW", "type": "BOT", "name": "B1"}]"""
        val validTokens = """[
            {"color": "RED", "index": 0, "progress": -1}, {"color": "RED", "index": 1, "progress": -1},
            {"color": "RED", "index": 2, "progress": -1}, {"color": "RED", "index": 3, "progress": -1},
            {"color": "YELLOW", "index": 0, "progress": -1}, {"color": "YELLOW", "index": 1, "progress": -1},
            {"color": "YELLOW", "index": 2, "progress": -1}, {"color": "YELLOW", "index": 3, "progress": -1}
        ]"""
        assertNull(GameStateCodec.decode(
            """{"seats": $validSeats, "tokens": $validTokens, "activeSeat": 5, "pendingBonus": 0, "consecutiveSixes": 0, "awaitingRoll": true, "turn": 1}"""
        ))
        // Invalid roll out of range (e.g. 7)
        assertNull(GameStateCodec.decode(
            """{"seats": $validSeats, "tokens": $validTokens, "activeSeat": 0, "roll": 7, "pendingBonus": 0, "consecutiveSixes": 0, "awaitingRoll": false, "turn": 1}"""
        ))
        // Invalid diceBank value out of range (e.g. 0)
        assertNull(GameStateCodec.decode(
            """{"seats": $validSeats, "tokens": $validTokens, "activeSeat": 0, "diceBank": [0], "pendingBonus": 0, "consecutiveSixes": 0, "awaitingRoll": false, "turn": 1}"""
        ))
        // Token count mismatch (e.g. only 4 tokens for 2 seats instead of 8)
        assertNull(GameStateCodec.decode(
            """{"seats": $validSeats, "tokens": [{"color": "RED", "index": 0, "progress": -1}], "activeSeat": 0, "pendingBonus": 0, "consecutiveSixes": 0, "awaitingRoll": true, "turn": 1}"""
        ))
    }

    @Test
    fun `decode handles optional and omitted json arrays gracefully`() {
        val validSeats = """[{"color": "RED", "type": "HUMAN", "name": "P1"}, {"color": "YELLOW", "type": "BOT", "name": "B1"}]"""
        val validTokens = """[
            {"color": "RED", "index": 0, "progress": -1}, {"color": "RED", "index": 1, "progress": -1},
            {"color": "RED", "index": 2, "progress": -1}, {"color": "RED", "index": 3, "progress": -1},
            {"color": "YELLOW", "index": 0, "progress": -1}, {"color": "YELLOW", "index": 1, "progress": -1},
            {"color": "YELLOW", "index": 2, "progress": -1}, {"color": "YELLOW", "index": 3, "progress": -1}
        ]"""
        val minimalJson = """{
            "seats": $validSeats,
            "tokens": $validTokens,
            "activeSeat": 0,
            "pendingBonus": 0,
            "consecutiveSixes": 0,
            "awaitingRoll": true,
            "turn": 1
        }"""
        val decoded = GameStateCodec.decode(minimalJson)
        assertNotNull(decoded)
        assertEquals(emptySet<String>(), decoded?.pairs)
        assertEquals(emptyList<Int>(), decoded?.diceBank)
        assertEquals(emptyList<LudoColor>(), decoded?.ranking)
        assertNull(decoded?.roll)
        assertEquals("Roll the dice", decoded?.message)
        assertFalse(decoded!!.completed)
    }
}
