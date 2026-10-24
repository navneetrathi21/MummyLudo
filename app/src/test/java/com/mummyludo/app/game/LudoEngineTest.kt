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

    @Test fun fiftyRandomDiceAnimationPathsStayOnBoardAndVary() {
        val paths = List(50) { DiceRollPath.random() }
        assertTrue(paths.map { it.startX to it.endX }.distinct().size > 1)
        paths.forEach { path ->
            (0..20).forEach { tick ->
                val progress = tick / 20f
                assertTrue("x ${path.x(progress)}", path.x(progress) in -24f..282f)
                assertTrue("y ${path.y(progress)}", path.y(progress) in -64f..190f)
            }
        }
    }

    @Test fun aSingleBankedValueIsSelectedAutomatically() {
        val engine = LudoEngine()
        val initial = engine.newGame(listOf(red, yellow))
        val committed = engine.commitRoll(initial.copy(
            tokens = initial.tokens.map { if (it.id == "R0") it.copy(progress = 0) else it },
        ), 2)
        val ready = engine.selectOnlyDie(committed)
        assertEquals(2, ready.roll)
        assertTrue(ready.diceBank.isEmpty())
        assertEquals("Choose a token for 2", ready.message)
    }

    @Test fun multipleBankedValuesStillRequireDiceSelection() {
        val engine = LudoEngine()
        var state = engine.commitRoll(engine.newGame(listOf(red, yellow)), 6)
        state = engine.commitRoll(state, 2)
        val ready = engine.selectOnlyDie(state)
        assertNull(ready.roll)
        assertEquals(listOf(6, 2), ready.diceBank)
        assertEquals("Choose a dice value", ready.message)
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

    @Test fun thirdConsecutiveSixDiscardsSixesAndRollsAgainOnSameTurn() {
        val engine = LudoEngine()
        var state = engine.newGame(listOf(red, yellow))
        repeat(2) { state = engine.commitRoll(state, 6) }
        state = engine.commitRoll(state, 6)
        assertEquals(0, state.activeSeat)
        assertNull(state.roll)
        assertEquals(0, state.pendingBonus)
        assertTrue(state.diceBank.isEmpty())
        assertTrue(state.awaitingRoll)
        assertEquals(1, state.turn)
        assertEquals("Three sixes cancelled · roll again", state.message)
        state = engine.commitRoll(state, 2)
        assertEquals(listOf(2), state.diceBank)
        assertFalse(state.awaitingRoll)
    }

    @Test fun thirdConsecutiveSixPreservesEarlierBankValuesAndCredits() {
        val engine = LudoEngine()
        var state = engine.newGame(listOf(red, yellow)).copy(
            diceBank = listOf(2),
            pendingBonus = 2,
        )
        repeat(2) { state = engine.commitRoll(state, 6) }
        state = engine.commitRoll(state, 6)
        assertEquals(listOf(2), state.diceBank)
        assertEquals(1, state.pendingBonus)
        assertTrue(state.awaitingRoll)
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

    @Test fun everyColorHonorsEveryPhysicalShortArrowTail() {
        val engine = LudoEngine()
        val offsets = mapOf(
            LudoColor.RED to 0,
            LudoColor.GREEN to 13,
            LudoColor.YELLOW to 26,
            LudoColor.BLUE to 39,
        )
        val physicalShortArrowTails = listOf(1, 4, 14, 17, 27, 30, 40, 43)
        LudoColor.entries.forEach { color ->
            physicalShortArrowTails.forEach { physicalTail ->
                val tail = (physicalTail - offsets.getValue(color) + 52) % 52
                val opponent = LudoColor.entries.first { it != color }
                val seats = listOf(Seat(color, PlayerType.HUMAN, "Player"), Seat(opponent, PlayerType.BOT, "Bot"))
                val tokens = List(4) { Token(color, it, tail - 1) } + List(4) { Token(opponent, it) }
                val fresh = engine.newGame(seats)
                val start = fresh.copy(tokens = tokens, activeSeat = fresh.seats.indexOfFirst { it.color == color })
                val selected = engine.selectDie(engine.commitRoll(start, 1), 0)
                val result = engine.move(selected, engine.legalMoves(selected).first())
                assertTrue("$color on physical arrow tail $physicalTail", result.arrow)
                assertEquals("$color on physical arrow tail $physicalTail", tail + 1, result.finalDestination)
            }
        }
    }

    @Test fun everyColorHonorsOwnHomeEntryArrow() {
        val engine = LudoEngine()
        LudoColor.entries.forEach { color ->
            val opponent = LudoColor.entries.first { it != color }
            val seats = listOf(Seat(color, PlayerType.HUMAN, "Player"), Seat(opponent, PlayerType.BOT, "Bot"))
            val tokens = List(4) { Token(color, it, 47) } + List(4) { Token(opponent, it) }
            val fresh = engine.newGame(seats)
            val start = fresh.copy(tokens = tokens, activeSeat = fresh.seats.indexOfFirst { it.color == color })
            val selected = engine.selectDie(engine.commitRoll(start, 1), 0)
            val result = engine.move(selected, engine.legalMoves(selected).first())
            assertTrue("$color home-entry arrow", result.arrow)
            assertEquals("$color home-entry arrow", 52, result.finalDestination)
        }
    }

    @Test fun homeEntryArrowAnimationWalksThroughEntryCellsBeforeFirstLaneCell() {
        val route = movementAnimationProgresses(
            start = 47,
            baseDestination = 48,
            finalDestination = 52,
            enters = false,
            arrow = true,
        )
        assertEquals(listOf(48, 49, 50, 52), route)
    }

    @Test fun homeEntryPassThroughAnimationSkipsStartCellAndThenEntersLane() {
        val route = movementAnimationProgresses(
            start = 47,
            baseDestination = 52,
            finalDestination = 52,
            enters = false,
            arrow = false,
        )
        assertEquals(listOf(48, 49, 50, 52), route)
    }

    @Test fun enteringHomeLineDoesNotCountCenterTransitionAsDiceStep() {
        val engine = LudoEngine()
        val tokens = listOf(Token(LudoColor.RED, 0, 50)) +
            List(3) { Token(LudoColor.RED, it + 1) } + List(4) { Token(LudoColor.YELLOW, it) }
        val start = engine.newGame(listOf(red, yellow)).copy(tokens = tokens)

        val one = engine.selectDie(engine.commitRoll(start, 1), 0)
        assertEquals(52, engine.legalMoves(one).single().destination)

        val two = engine.selectDie(engine.commitRoll(start, 2), 0)
        assertEquals(53, engine.legalMoves(two).single().destination)
    }

    @Test fun homeLineStillRequiresExactFinishAfterSkippingTransition() {
        val engine = LudoEngine()
        val tokens = listOf(Token(LudoColor.RED, 0, 50)) +
            List(3) { Token(LudoColor.RED, it + 1) } + List(4) { Token(LudoColor.YELLOW, it) }
        val start = engine.newGame(listOf(red, yellow)).copy(tokens = tokens)

        val six = engine.selectDie(start.copy(diceBank = listOf(6), awaitingRoll = false), 0)
        assertEquals(FINISHED, engine.legalMoves(six).single { "R0" in it.tokenIds }.destination)

        val nearFinish = start.copy(
            tokens = tokens.map { if (it.id == "R0") it.copy(progress = 56) else it },
            diceBank = listOf(2),
            awaitingRoll = false,
        )
        val two = engine.selectDie(nearFinish, 0)
        assertEquals(LudoColor.YELLOW, two.seats[two.activeSeat].color)
        assertEquals("Roll the dice", two.message)
    }

    @Test fun finishingTokenGrantsExtraRollBeforeTurnAdvances() {
        val engine = LudoEngine()
        val tokens = listOf(Token(LudoColor.RED, 0, 56)) +
            List(3) { Token(LudoColor.RED, it + 1) } + List(4) { Token(LudoColor.YELLOW, it) }
        var state = engine.newGame(listOf(red, yellow)).copy(
            tokens = tokens,
            diceBank = listOf(1),
            awaitingRoll = false,
        )

        state = engine.selectDie(state, 0)
        val result = engine.move(state, engine.legalMoves(state).single())
        assertEquals(FINISHED, result.finalDestination)
        assertEquals(1, result.state.pendingBonus)
        assertTrue(result.state.awaitingRoll)
        assertEquals(LudoColor.RED, result.state.seats[result.state.activeSeat].color)

        val bonusRolled = engine.commitRoll(result.state, 3)
        assertEquals(listOf(3), bonusRolled.diceBank)
        assertFalse(bonusRolled.awaitingRoll)
    }

    @Test fun finalWinningMoveCompletesGameInsteadOfWaitingForBonusRoll() {
        val engine = LudoEngine()
        val tokens = List(3) { Token(LudoColor.RED, it, FINISHED) } +
            listOf(Token(LudoColor.RED, 3, 56)) +
            List(4) { Token(LudoColor.YELLOW, it) }
        var state = engine.newGame(listOf(red, yellow)).copy(
            tokens = tokens,
            diceBank = listOf(1),
            awaitingRoll = false,
        )

        state = engine.selectDie(state, 0)
        val result = engine.move(state, engine.legalMoves(state).single())
        assertTrue(result.state.completed)
        assertFalse(result.state.awaitingRoll)
        assertTrue(result.state.diceBank.isEmpty())
        assertEquals("Red wins!", result.state.message)
    }

    @Test fun redTokenHonorsShortArrowInGreenZone() {
        val engine = LudoEngine()
        // Green's first short-arrow tail is global cell 14. For Red this is progress 14.
        val tokens = listOf(Token(LudoColor.RED, 0, 13)) +
            List(3) { Token(LudoColor.RED, it + 1) } + List(4) { Token(LudoColor.YELLOW, it) }
        val start = engine.newGame(listOf(red, yellow)).copy(tokens = tokens)
        val selected = engine.selectDie(engine.commitRoll(start, 1), 0)
        val result = engine.move(selected, engine.legalMoves(selected).single())
        assertTrue(result.arrow)
        assertEquals(14, result.baseDestination)
        assertEquals(15, result.finalDestination)
    }

    @Test fun arrowBonusIsRolledBeforeRemainingBankValuesAreSpent() {
        val engine = LudoEngine()
        val tokens = listOf(Token(LudoColor.RED, 0, 0)) +
            List(3) { Token(LudoColor.RED, it + 1) } + List(4) { Token(LudoColor.YELLOW, it) }
        var state = engine.newGame(listOf(red, yellow)).copy(tokens = tokens, diceBank = listOf(6, 1), awaitingRoll = false)
        state = engine.selectDie(state, 1)
        state = engine.move(state, engine.legalMoves(state).single()).state
        assertTrue(state.awaitingRoll)
        assertEquals(listOf(6), state.diceBank)
        assertEquals(1, state.pendingBonus)

        state = engine.commitRoll(state, 3)
        assertFalse(state.awaitingRoll)
        assertEquals(listOf(6, 3), state.diceBank)
        assertEquals(0, state.pendingBonus)
    }

    @Test fun singleBonusRollAfterArrowAutoSelectsTokenChoice() {
        val engine = LudoEngine()
        val tokens = listOf(Token(LudoColor.RED, 0, 0)) +
            List(3) { Token(LudoColor.RED, it + 1) } + List(4) { Token(LudoColor.YELLOW, it) }
        var state = engine.newGame(listOf(red, yellow)).copy(tokens = tokens, diceBank = listOf(1), awaitingRoll = false)
        state = engine.selectDie(state, 0)
        state = engine.move(state, engine.legalMoves(state).single()).state
        assertTrue(state.awaitingRoll)
        assertTrue(state.diceBank.isEmpty())

        state = engine.selectOnlyDie(engine.commitRoll(state, 3))
        assertEquals(3, state.roll)
        assertTrue(state.diceBank.isEmpty())
        assertEquals("Choose a token for 3", state.message)
    }

    @Test fun arrowAndCaptureCreditsAreBothCollectedBeforeMoveSelection() {
        val engine = LudoEngine()
        // Red arrow head progress 2 and Yellow progress 28 share global track cell 2.
        val tokens = listOf(Token(LudoColor.RED, 0, 0)) +
            List(3) { Token(LudoColor.RED, it + 1) } +
            listOf(Token(LudoColor.YELLOW, 0, 28)) + List(3) { Token(LudoColor.YELLOW, it + 1) }
        var state = engine.newGame(listOf(red, yellow)).copy(tokens = tokens, diceBank = listOf(1), awaitingRoll = false)
        state = engine.selectDie(state, 0)
        val result = engine.move(state, engine.legalMoves(state).single())
        assertEquals(1, result.captured)
        assertEquals(2, result.state.pendingBonus)

        state = engine.commitRoll(result.state, 2)
        assertTrue(state.awaitingRoll)
        assertEquals(1, state.pendingBonus)
        state = engine.commitRoll(state, 4)
        assertFalse(state.awaitingRoll)
        assertEquals(listOf(2, 4), state.diceBank)
    }

    @Test fun botPrefersCaptureAtArrowHead() {
        val engine = LudoEngine()
        val botRed = Seat(LudoColor.RED, PlayerType.BOT, "Bot")
        val humanYellow = Seat(LudoColor.YELLOW, PlayerType.HUMAN, "Player")
        val tokens = listOf(Token(LudoColor.RED, 0, 0), Token(LudoColor.RED, 1, 10), Token(LudoColor.RED, 2), Token(LudoColor.RED, 3)) +
            listOf(Token(LudoColor.YELLOW, 0, 28)) + List(3) { Token(LudoColor.YELLOW, it + 1) }
        val start = engine.newGame(listOf(botRed, humanYellow)).copy(tokens = tokens)
        val selected = engine.selectDie(engine.commitRoll(start, 1), 0)
        val best = engine.chooseBest(selected)
        assertEquals("R0", best.tokenIds.single())
        assertEquals(1, engine.move(selected, best).captured)
    }

    @Test fun botGetsExtraRollOnDifferentColorPhysicalArrow() {
        val engine = LudoEngine()
        val humanRed = Seat(LudoColor.RED, PlayerType.HUMAN, "Player")
        val botGreen = Seat(LudoColor.GREEN, PlayerType.BOT, "Bot")
        // Red's first physical arrow tail is global cell 1. For Green that is progress 40.
        val tokens = List(4) { Token(LudoColor.RED, it) } +
            listOf(Token(LudoColor.GREEN, 0, 39)) + List(3) { Token(LudoColor.GREEN, it + 1) }
        val fresh = engine.newGame(listOf(humanRed, botGreen))
        var state = fresh.copy(
            tokens = tokens,
            activeSeat = fresh.seats.indexOfFirst { it.color == LudoColor.GREEN },
            diceBank = listOf(1),
            awaitingRoll = false,
        )

        state = engine.selectDie(state, 0)
        val result = engine.move(state, engine.legalMoves(state).single())
        assertTrue(result.arrow)
        assertEquals(40, result.baseDestination)
        assertEquals(41, result.finalDestination)
        assertEquals(1, result.state.pendingBonus)
        assertTrue(result.state.awaitingRoll)
        assertEquals(LudoColor.GREEN, result.state.seats[result.state.activeSeat].color)

        val bonusRolled = engine.commitRoll(result.state, 2)
        assertEquals(0, bonusRolled.pendingBonus)
        assertEquals(listOf(2), bonusRolled.diceBank)
        assertFalse(bonusRolled.awaitingRoll)
        assertEquals(LudoColor.GREEN, bonusRolled.seats[bonusRolled.activeSeat].color)
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

    @Test
    fun engineDefaultParametersAndEdgeCasesAreCovered() {
        val engine = LudoEngine { 6 }
        val start = engine.newGame(listOf(red, yellow))

        // commitRoll without forced param (uses default forced = null)
        val rolled = engine.commitRoll(start)
        assertEquals(listOf(6), rolled.diceBank)
        assertTrue(rolled.awaitingRoll)

        // autoStep without forcedRoll param (uses default forcedRoll = null)
        val afterStep = engine.autoStep(start)
        assertEquals(listOf(6), afterStep.diceBank)

        // autoStep fallback return state (not completed, not awaitingRoll, roll != null)
        val notAwaiting = rolled.copy(awaitingRoll = false, roll = 6)
        val fallback = engine.autoStep(notAwaiting)
        assertEquals(notAwaiting, fallback)

        // movementAnimationProgresses arrow with finalDestination <= baseDestination
        val path = movementAnimationProgresses(start = 10, baseDestination = 14, finalDestination = 14, enters = false, arrow = true)
        assertEquals(listOf(11, 12, 13, 14), path)

        // capturedAt with invalid attackingCount returns emptySet
        val captures = engine.capturedAt(
            attacker = LudoColor.RED,
            destination = 0,
            attackingCount = 3,
            tokens = listOf(Token(LudoColor.YELLOW, 0, progress = 39)),
            pairs = emptySet(),
        )
        assertTrue(captures.isEmpty())
    }
}
