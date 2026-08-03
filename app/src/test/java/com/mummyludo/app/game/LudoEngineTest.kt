package com.mummyludo.app.game

import org.junit.Assert.*
import org.junit.Test

class LudoEngineTest {
    private val red = Seat(LudoColor.RED, PlayerType.HUMAN, "Player")
    private val yellow = Seat(LudoColor.YELLOW, PlayerType.BOT, "Bot")

    @Test fun generatedRollUsesInjectedDiceSourceWithoutSubstitution() {
        val values = ArrayDeque(listOf(1, 2, 3, 4, 5, 6))
        val engine = LudoEngine { values.removeFirst() }
        assertEquals(listOf(1, 2, 3, 4, 5, 6), List(6) { engine.generateRoll() })
    }

    @Test fun sixIsBankedAndPlayerChoosesItsOrderAfterRollingStops() {
        val engine = LudoEngine()
        var state = engine.commitRoll(engine.newGame(listOf(red, yellow)), 6)
        assertEquals(listOf(6), state.diceBank)
        assertTrue(state.awaitingRoll)
        state = engine.commitRoll(state, 2)
        assertEquals(listOf(6, 2), state.diceBank)
        assertFalse(state.awaitingRoll)
        val selected = engine.selectDie(state, 0)
        assertEquals(4, engine.legalMoves(selected).size)
        val moved = engine.move(selected, engine.legalMoves(selected).first()).state
        assertEquals(0, moved.activeSeat)
        assertEquals(listOf(2), moved.diceBank)
        assertEquals("Choose another dice value", moved.message)
    }

    @Test fun thirdConsecutiveSixCancelsTurnAndCredits() {
        val engine = LudoEngine()
        var state = engine.newGame(listOf(red, yellow))
        repeat(2) { state = engine.commitRoll(state, 6) }
        state = engine.commitRoll(state, 6)
        assertEquals(1, state.activeSeat)
        assertNull(state.roll)
        assertEquals(0, state.pendingBonus)
        assertTrue(state.diceBank.isEmpty())
        assertEquals("Third six cancelled · turn ended", state.message)
    }

    @Test fun shortArrowMovesToHeadAndAddsCredit() {
        val engine = LudoEngine()
        val start = engine.newGame(listOf(red, yellow)).copy(
            tokens = listOf(Token(LudoColor.RED, 0, 0)) +
                List(3) { Token(LudoColor.RED, it + 1) } +
                List(4) { Token(LudoColor.YELLOW, it) },
        )
        val rolled = engine.selectDie(engine.commitRoll(start, 1), 0)
        val result = engine.move(rolled, engine.legalMoves(rolled).single())
        assertEquals(2, result.state.tokens.first().progress)
        assertTrue(result.arrow)
        assertEquals(1, result.baseDestination)
        assertEquals(2, result.finalDestination)
        assertEquals("Bonus roll", result.state.message)
    }

    @Test fun homeEntryArrowTraversesFromTailToFirstLaneCell() {
        val engine = LudoEngine()
        val start = engine.newGame(listOf(red, yellow)).copy(
            tokens = listOf(Token(LudoColor.RED, 0, 47)) +
                List(3) { Token(LudoColor.RED, it + 1) } +
                List(4) { Token(LudoColor.YELLOW, it) },
        )
        val selected = engine.selectDie(engine.commitRoll(start, 1), 0)
        val result = engine.move(selected, engine.legalMoves(selected).single())
        assertTrue(result.arrow)
        assertEquals(48, result.baseDestination)
        assertEquals(52, result.finalDestination)
        assertEquals(52, result.state.tokens.first().progress)
    }

    @Test fun doubleUsesEvenRollAtHalfDistance() {
        val engine = LudoEngine()
        val tokens = listOf(Token(LudoColor.RED, 0, 10), Token(LudoColor.RED, 1, 10)) +
            List(2) { Token(LudoColor.RED, it + 2) } + List(4) { Token(LudoColor.YELLOW, it) }
        val start = engine.newGame(listOf(red, yellow)).copy(tokens = tokens, pairs = setOf("R0:R1"))
        val odd = engine.selectDie(engine.commitRoll(start, 3), 0)
        assertTrue(engine.legalMoves(odd).isEmpty())
        val rolled = engine.selectDie(engine.commitRoll(start, 4), 0)
        val move = engine.legalMoves(rolled).single()
        assertEquals(2, move.routeSteps)
        assertEquals(12, move.destination)
    }

    @Test fun doubleCapturesPairFromTripleAndLeavesSingle() {
        val engine = LudoEngine()
        // Yellow relative progress 38 and Red progress 12 map to the same global track cell.
        val tokens = listOf(
            Token(LudoColor.RED, 0, 11), Token(LudoColor.RED, 1, 11),
            Token(LudoColor.RED, 2), Token(LudoColor.RED, 3),
            Token(LudoColor.YELLOW, 0, 38), Token(LudoColor.YELLOW, 1, 38), Token(LudoColor.YELLOW, 2, 38), Token(LudoColor.YELLOW, 3),
        )
        val start = engine.newGame(listOf(red, yellow)).copy(tokens = tokens, pairs = setOf("R0:R1", "Y0:Y1"))
        val rolled = engine.selectDie(engine.commitRoll(start, 2), 0)
        val result = engine.move(rolled, engine.legalMoves(rolled).single())
        assertEquals(2, result.captured)
        assertEquals(YARD, result.state.tokens.first { it.id == "Y0" }.progress)
        assertEquals(38, result.state.tokens.first { it.id == "Y2" }.progress)
    }

    @Test fun fiveRequestedMatchShapesCompleteLegally() {
        val engine = LudoEngine()
        val shapes = listOf(1 to 1, 2 to 2, 1 to 3, 3 to 1, 1 to 1)
        shapes.forEachIndexed { index, (humans, bots) ->
            val seats = LudoColor.entries.take(humans + bots).mapIndexed { seatIndex, color ->
                Seat(color, if (seatIndex < humans) PlayerType.HUMAN else PlayerType.BOT, "Seat $seatIndex")
            }
            val result = engine.simulate(engine.newGame(seats), 4100 + index)
            assertTrue("shape $humans v $bots", result.completed)
            assertEquals(seats.size, result.ranking.size)
            // The final participant is ranked when only one remains, so its tokens need not all finish.
            assertTrue(result.tokens.count { it.progress == FINISHED } >= (seats.size - 1) * 4)
        }
    }
}
