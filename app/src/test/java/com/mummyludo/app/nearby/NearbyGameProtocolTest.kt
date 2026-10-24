package com.mummyludo.app.nearby

import com.mummyludo.app.game.LudoColor
import com.mummyludo.app.game.LudoEngine
import com.mummyludo.app.game.PlayerType
import com.mummyludo.app.game.Seat
import org.junit.Assert.*
import org.junit.Test

class NearbyGameProtocolTest {
    private val engine = LudoEngine { 3 }
    private val initial = engine.newGame(
        listOf(
            Seat(LudoColor.RED, PlayerType.HUMAN, "Host"),
            Seat(LudoColor.YELLOW, PlayerType.HUMAN, "Guest"),
        ),
    )

    @Test fun `actions round trip without carrying a dice result`() {
        val actions = listOf(
            NearbyGameAction.Roll,
            NearbyGameAction.SelectDie(1),
            NearbyGameAction.Move(listOf("R0", "R1")),
        )
        actions.forEach { assertEquals(it, NearbyGameActionCodec.decode(NearbyGameActionCodec.encode(it))) }
        assertNull(NearbyGameActionCodec.decode("ROLL|6"))
        assertNull(NearbyGameActionCodec.decode("MOVE|R0,R1,R2"))
    }

    @Test fun `only active color can request an action`() {
        assertTrue(isAuthorizedGameAction(initial, LudoColor.RED, NearbyGameAction.Roll))
        assertFalse(isAuthorizedGameAction(initial, LudoColor.YELLOW, NearbyGameAction.Roll))
        assertFalse(isAuthorizedGameAction(initial, null, NearbyGameAction.Roll))
    }

    @Test fun `resolved events round trip and require host dice value`() {
        val event = NearbyGameEvent(12, NearbyGameAction.Roll, 5)
        assertEquals(event, NearbyGameEventCodec.decode(NearbyGameEventCodec.encode(event)))
        assertNull(NearbyGameEventCodec.decode("12~-~ROLL"))
        assertNull(NearbyGameEventCodec.decode("12~9~ROLL"))
    }

    @Test fun `player cannot request another colors token`() {
        val movePhase = engine.selectDie(engine.commitRoll(engine.commitRoll(initial, 6), 2), 0)
        assertTrue(isAuthorizedGameAction(movePhase, LudoColor.RED, NearbyGameAction.Move(listOf("R0"))))
        assertFalse(isAuthorizedGameAction(movePhase, LudoColor.RED, NearbyGameAction.Move(listOf("Y0"))))
        assertFalse(isAuthorizedGameAction(movePhase, LudoColor.RED, NearbyGameAction.Move(listOf("R0", "R1"))))
    }

    @Test fun `non roll events use the default absent dice value`() {
        val select = NearbyGameEvent(13, NearbyGameAction.SelectDie(0))
        val move = NearbyGameEvent(14, NearbyGameAction.Move(listOf("R1", "R0")))

        assertEquals(select, NearbyGameEventCodec.decode(NearbyGameEventCodec.encode(select)))
        assertEquals(
            NearbyGameEvent(14, NearbyGameAction.Move(listOf("R0", "R1"))),
            NearbyGameEventCodec.decode(NearbyGameEventCodec.encode(move)),
        )
    }

    @Test fun `malformed and contradictory events are rejected`() {
        listOf(
            "", "13~5", "bad~5~ROLL", "13~not-a-number~ROLL", "13~-~ROLL",
            "13~0~ROLL", "13~7~ROLL", "13~4~DIE|0", "13~-~UNKNOWN",
        ).forEach { encoded ->
            assertNull("Accepted event: $encoded", NearbyGameEventCodec.decode(encoded))
        }
    }

    @Test fun `malformed actions are rejected without throwing`() {
        listOf(
            "", "UNKNOWN", "ROLL|6", "DIE", "DIE|nope", "MOVE", "MOVE|",
            "MOVE|R0,R1,R2",
        ).forEach { encoded ->
            assertNull("Accepted action: $encoded", NearbyGameActionCodec.decode(encoded))
        }
    }

    @Test fun `dice selection is authorized only for a valid bank index in selection phase`() {
        val selection = initial.copy(awaitingRoll = false, diceBank = listOf(2, 6))

        assertTrue(isAuthorizedGameAction(selection, LudoColor.RED, NearbyGameAction.SelectDie(0)))
        assertTrue(isAuthorizedGameAction(selection, LudoColor.RED, NearbyGameAction.SelectDie(1)))
        assertFalse(isAuthorizedGameAction(selection, LudoColor.RED, NearbyGameAction.SelectDie(-1)))
        assertFalse(isAuthorizedGameAction(selection, LudoColor.RED, NearbyGameAction.SelectDie(2)))
        assertFalse(isAuthorizedGameAction(selection, LudoColor.YELLOW, NearbyGameAction.SelectDie(0)))
        assertFalse(isAuthorizedGameAction(selection.copy(awaitingRoll = true), LudoColor.RED, NearbyGameAction.SelectDie(0)))
        assertFalse(isAuthorizedGameAction(selection.copy(roll = 2), LudoColor.RED, NearbyGameAction.SelectDie(0)))
        assertFalse(isAuthorizedGameAction(selection.copy(completed = true), LudoColor.RED, NearbyGameAction.SelectDie(0)))
    }

    @Test fun `roll and move requests are rejected outside their legal phases`() {
        val movePhase = engine.selectDie(engine.commitRoll(engine.commitRoll(initial, 6), 2), 0)

        assertFalse(isAuthorizedGameAction(initial.copy(awaitingRoll = false), LudoColor.RED, NearbyGameAction.Roll))
        assertFalse(isAuthorizedGameAction(initial.copy(roll = 6), LudoColor.RED, NearbyGameAction.Roll))
        assertFalse(isAuthorizedGameAction(initial.copy(completed = true), LudoColor.RED, NearbyGameAction.Roll))
        assertFalse(isAuthorizedGameAction(movePhase, LudoColor.RED, NearbyGameAction.Move(emptyList())))
        assertFalse(isAuthorizedGameAction(movePhase, LudoColor.RED, NearbyGameAction.Move(listOf("R0", "R1", "R2"))))
        assertFalse(isAuthorizedGameAction(movePhase, LudoColor.RED, NearbyGameAction.Move(listOf("missing"))))
    }
}
