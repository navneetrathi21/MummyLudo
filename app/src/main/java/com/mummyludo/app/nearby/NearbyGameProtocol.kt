package com.mummyludo.app.nearby

import com.mummyludo.app.game.GameState
import com.mummyludo.app.game.LudoColor
import com.mummyludo.app.game.LudoEngine

sealed interface NearbyGameAction {
    data object Roll : NearbyGameAction
    data class SelectDie(val bankIndex: Int) : NearbyGameAction
    data class Move(val tokenIds: List<String>) : NearbyGameAction
}

data class NearbyGameEvent(
    val sequence: Long,
    val action: NearbyGameAction,
    val rollValue: Int? = null,
)

object NearbyGameEventCodec {
    fun encode(event: NearbyGameEvent): String = listOf(
        event.sequence.toString(),
        event.rollValue?.toString() ?: "-",
        NearbyGameActionCodec.encode(event.action),
    ).joinToString("~")

    fun decode(raw: String): NearbyGameEvent? = runCatching {
        val parts = raw.split('~', limit = 3)
        require(parts.size == 3)
        val action = requireNotNull(NearbyGameActionCodec.decode(parts[2]))
        val roll = parts[1].takeUnless { it == "-" }?.toInt()
        require((action == NearbyGameAction.Roll) == (roll != null))
        if (roll != null) require(roll in 1..6)
        NearbyGameEvent(parts[0].toLong(), action, roll)
    }.getOrNull()
}

object NearbyGameActionCodec {
    fun encode(action: NearbyGameAction): String = when (action) {
        NearbyGameAction.Roll -> "ROLL"
        is NearbyGameAction.SelectDie -> "DIE|${action.bankIndex}"
        is NearbyGameAction.Move -> "MOVE|${action.tokenIds.sorted().joinToString(",")}" 
    }

    fun decode(raw: String): NearbyGameAction? = runCatching {
        val parts = raw.split('|', limit = 2)
        when (parts[0]) {
            "ROLL" -> NearbyGameAction.Roll.also { require(parts.size == 1) }
            "DIE" -> NearbyGameAction.SelectDie(parts.getOrElse(1) { error("Missing die") }.toInt())
            "MOVE" -> NearbyGameAction.Move(
                parts.getOrElse(1) { error("Missing tokens") }
                    .split(',').filter(String::isNotBlank).distinct(),
            ).also { require(it.tokenIds.size in 1..2) }
            else -> error("Unknown action")
        }
    }.getOrNull()
}

/** Host-side authorization. Dice values never arrive from a guest. */
fun isAuthorizedGameAction(
    state: GameState,
    playerColor: LudoColor?,
    action: NearbyGameAction,
): Boolean {
    if (state.completed || playerColor == null) return false
    if (state.seats[state.activeSeat].color != playerColor) return false
    return when (action) {
        NearbyGameAction.Roll -> state.awaitingRoll && state.roll == null
        is NearbyGameAction.SelectDie ->
            !state.awaitingRoll && state.roll == null && action.bankIndex in state.diceBank.indices
        is NearbyGameAction.Move -> {
            val requested = action.tokenIds.toSet()
            requested.isNotEmpty() && requested.size <= 2 &&
                state.tokens.filter { it.id in requested }.all { it.color == playerColor } &&
                LudoEngine().legalMoves(state).any { it.tokenIds.toSet() == requested }
        }
    }
}
