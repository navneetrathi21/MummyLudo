package com.mummyludo.app.game

import kotlin.random.Random

enum class LudoColor(val label: String) { RED("Red"), GREEN("Green"), YELLOW("Yellow"), BLUE("Blue") }
enum class PlayerType { HUMAN, BOT }

data class Seat(val color: LudoColor, val type: PlayerType, val name: String)
data class Token(val color: LudoColor, val index: Int, val progress: Int = YARD) {
    val id: String get() = "${color.name.first()}$index"
}
data class UnitMove(
    val tokenIds: List<String>,
    val destination: Int,
    val routeSteps: Int,
    val enters: Boolean = false,
)
data class GameState(
    val seats: List<Seat>,
    val tokens: List<Token>,
    val pairs: Set<String> = emptySet(),
    val activeSeat: Int = 0,
    val pendingBonus: Int = 0,
    val consecutiveSixes: Int = 0,
    val diceBank: List<Int> = emptyList(),
    val awaitingRoll: Boolean = true,
    val roll: Int? = null,
    val ranking: List<LudoColor> = emptyList(),
    val turn: Int = 1,
    val message: String = "Roll the dice",
    val completed: Boolean = false,
)

data class MoveResult(
    val state: GameState,
    val captured: Int,
    val arrow: Boolean,
    val baseDestination: Int,
    val finalDestination: Int,
    val movedTokenIds: List<String>,
)

const val YARD = -1
const val FINISHED = 57
private val protectedTrack = setOf(0, 8, 13, 21, 26, 34, 39, 47)
private val offsets = mapOf(
    LudoColor.RED to 0,
    LudoColor.GREEN to 13,
    LudoColor.YELLOW to 26,
    LudoColor.BLUE to 39,
)

fun pairKey(ids: List<String>): String = ids.sorted().joinToString(":")

class LudoEngine(private val dice: () -> Int = { Random.nextInt(1, 7) }) {
    fun generateRoll(): Int = dice().also { require(it in 1..6) }

    fun newGame(seats: List<Seat>): GameState {
        require(seats.size in 2..4)
        require(seats.map { it.color }.distinct().size == seats.size)
        val ordered = LudoColor.entries.mapNotNull { color -> seats.find { it.color == color } }
        return GameState(
            seats = ordered,
            tokens = ordered.flatMap { seat -> List(4) { Token(seat.color, it) } },
        )
    }

    fun commitRoll(state: GameState, forced: Int? = null): GameState {
        check(state.roll == null && state.awaitingRoll && !state.completed)
        val value = forced ?: generateRoll()
        require(value in 1..6)
        // A six continuation is taken before any queued arrow/capture credits.
        // Otherwise this roll consumes one queued credit at commit time.
        val consumesBonus = state.pendingBonus > 0 && state.consecutiveSixes == 0
        val remainingBonus = state.pendingBonus - if (consumesBonus) 1 else 0
        val streak = if (value == 6) state.consecutiveSixes + 1 else 0
        if (streak == 3) {
            // The three consecutive sixes are void, but the participant keeps
            // the turn. Preserve any earlier non-six bank values and credits.
            return state.copy(
                consecutiveSixes = 0,
                pendingBonus = remainingBonus,
                diceBank = state.diceBank.dropLast(2),
                awaitingRoll = true,
                message = "Three sixes cancelled · roll again",
            )
        }
        val bank = state.diceBank + value
        return state.copy(
            diceBank = bank,
            pendingBonus = remainingBonus,
            consecutiveSixes = streak,
            awaitingRoll = value == 6 || remainingBonus > 0,
            message = when {
                value == 6 -> "Six rolled · roll again"
                remainingBonus > 0 -> "Bonus roll"
                else -> "Choose a dice value"
            },
        )
    }

    fun selectDie(state: GameState, bankIndex: Int): GameState {
        check(!state.awaitingRoll && state.roll == null && bankIndex in state.diceBank.indices)
        val value = state.diceBank[bankIndex]
        val selected = state.copy(
            roll = value,
            diceBank = state.diceBank.toMutableList().also { it.removeAt(bankIndex) },
            message = "Choose a token for $value",
        )
        return if (legalMoves(selected).isEmpty()) finishMove(selected.copy(message = "No legal move for $value"))
        else selected
    }

    fun selectOnlyDie(state: GameState): GameState =
        if (!state.awaitingRoll && state.roll == null && state.diceBank.size == 1) selectDie(state, 0) else state

    fun legalMoves(state: GameState): List<UnitMove> {
        val die = state.roll ?: return emptyList()
        val color = state.seats[state.activeSeat].color
        val own = state.tokens.filter { it.color == color && it.progress != FINISHED }
        val explicitPairs = state.pairs.map { it.split(":").toSet() }
        val pairedIds = explicitPairs.flatten().toSet()
        val units = mutableListOf<List<Token>>()
        explicitPairs.forEach { ids ->
            val pair = own.filter { it.id in ids }
            if (pair.size == 2 && pair.map { it.progress }.distinct().size == 1) units += pair
        }
        own.filter { it.id !in pairedIds }.forEach { units += listOf(it) }

        // Any two unpaired same-color tokens sharing a square may be selected together.
        own.filter { it.id !in pairedIds && it.progress >= 0 }
            .groupBy { it.progress }
            .values
            .forEach { group ->
                if (group.size >= 2) units += group.sortedBy { it.index }.take(2)
                if (group.size == 4) units += group.sortedBy { it.index }.takeLast(2)
            }

        return units.distinctBy { it.map(Token::id).sorted() }.mapNotNull { unit ->
            val isDouble = unit.size == 2
            if (isDouble && die % 2 != 0) return@mapNotNull null
            val steps = if (isDouble) die / 2 else die
            val current = unit.first().progress
            val destination = advanceProgress(current, steps)
            when {
                current == YARD && die == 6 -> UnitMove(unit.map(Token::id), 0, 0, enters = true)
                current == YARD -> null
                destination > FINISHED -> null
                else -> UnitMove(unit.map(Token::id), destination, steps)
            }
        }
    }

    fun move(state: GameState, move: UnitMove): MoveResult {
        require(move in legalMoves(state)) { "Illegal move: $move" }
        val moving = state.tokens.filter { it.id in move.tokenIds }
        val color = moving.first().color
        val baseDestination = move.destination
        val arrowDestination = arrowHead(color, baseDestination)
        val destination = arrowDestination ?: baseDestination
        val arrow = arrowDestination != null
        val movedTokens = state.tokens.map { token ->
            if (token.id in move.tokenIds) token.copy(progress = destination) else token
        }
        var pairs = state.pairs.filterNot { key -> move.tokenIds.any { it in key.split(":") } }.toSet()
        if (move.tokenIds.size == 2 && !isProtected(color, destination) && destination != FINISHED) {
            pairs = pairs + pairKey(move.tokenIds)
        }

        val capturedIds = if (destination in 0..51 && !isProtected(color, destination)) {
            capturedAt(color, destination, move.tokenIds.size, movedTokens, pairs)
        } else emptySet()
        val afterCapture = movedTokens.map { token ->
            if (token.id in capturedIds) token.copy(progress = YARD) else token
        }
        pairs = pairs.filterNot { key -> key.split(":").any { it in capturedIds } }.toSet()

        // A same-color landing away from safety creates deterministic doubles, never triples.
        if (!isProtected(color, destination) && destination in 0 until FINISHED) {
            val colocated = afterCapture.filter { it.color == color && it.progress == destination }
            val alreadyPaired = pairs.flatMap { it.split(":") }.toSet()
            val free = colocated.filter { it.id !in alreadyPaired }.sortedBy { it.index }
            free.chunked(2).filter { it.size == 2 }.forEach { pairs = pairs + pairKey(it.map(Token::id)) }
        }

        var next = state.copy(
            tokens = afterCapture,
            pairs = pairs,
            pendingBonus = state.pendingBonus +
                (if (arrow) 1 else 0) +
                (if (capturedIds.isNotEmpty()) 1 else 0) +
                (if (destination == FINISHED) 1 else 0),
            message = buildString {
                append(color.label)
                if (capturedIds.isNotEmpty()) append(" captured ${capturedIds.size}")
                else if (destination == FINISHED) append(" finished")
                else append(" moved")
                if (arrow) append(" via arrow")
            },
        )
        next = updateRanking(next)
        next = finishMove(next)
        return MoveResult(next, capturedIds.size, arrow, baseDestination, destination, move.tokenIds)
    }

    fun chooseBest(state: GameState): UnitMove = legalMoves(state).maxWith(
        compareBy<UnitMove> {
            val color = state.seats[state.activeSeat].color
            val resolvedDestination = arrowHead(color, it.destination) ?: it.destination
            val global = globalTrack(color, if (resolvedDestination <= 51) resolvedDestination else -1)
            val opponents = state.tokens.count { token ->
                token.color != color && token.progress in 0..51 && globalTrack(token.color, token.progress) == global
            }
            when {
                resolvedDestination == FINISHED -> 10_000
                opponents > 0 -> 8_000 + opponents
                resolvedDestination > 51 -> 5_000 + resolvedDestination
                isProtected(color, resolvedDestination) -> 3_000
                it.enters -> 2_000
                else -> resolvedDestination
            }
        }.thenBy { it.tokenIds.joinToString() },
    )

    fun autoStep(state: GameState, forcedRoll: Int? = null): GameState {
        if (state.completed) return state
        if (state.awaitingRoll) return commitRoll(state, forcedRoll)
        if (state.roll == null && state.diceBank.isNotEmpty()) {
            val bestSelection = state.diceBank.indices.map { index -> index to selectDie(state, index) }
                .maxBy { (_, selected) ->
                    val moves = legalMoves(selected)
                    if (moves.isEmpty()) Int.MIN_VALUE else scoreMove(selected, chooseBest(selected))
                }
            val selected = bestSelection.second
            return if (selected.roll == null) selected else move(selected, chooseBest(selected)).state
        }
        return state
    }

    fun simulate(initial: GameState, seed: Int, maxTurns: Int = 50_000): GameState {
        val random = Random(seed)
        var state = initial
        var steps = 0
        while (!state.completed && steps++ < maxTurns) state = autoStep(state, random.nextInt(1, 7))
        check(state.completed) { "Simulation exceeded $maxTurns rolls" }
        return state
    }

    internal fun capturedAt(
        attacker: LudoColor,
        destination: Int,
        attackingCount: Int,
        tokens: List<Token>,
        pairs: Set<String>,
    ): Set<String> {
        val targetGlobal = globalTrack(attacker, destination)
        val opponents = tokens.filter {
            it.color != attacker && it.progress in 0..51 && globalTrack(it.color, it.progress) == targetGlobal
        }
        if (opponents.isEmpty()) return emptySet()
        val pairSets = pairs.map { it.split(":").toSet() }
        val paired = opponents.filter { token -> pairSets.any { token.id in it } }
        val singles = opponents.filter { token -> token !in paired }
        return when (attackingCount) {
            1 -> if (opponents.size == 1) setOf(opponents.first().id)
                else if (opponents.size == 3 && singles.isNotEmpty()) setOf(singles.first().id)
                else emptySet()
            2 -> when {
                opponents.size >= 4 -> opponents.take(4).map(Token::id).toSet()
                opponents.size == 3 && paired.size >= 2 -> paired.take(2).map(Token::id).toSet()
                opponents.size >= 2 -> opponents.take(2).map(Token::id).toSet()
                else -> opponents.map(Token::id).toSet()
            }
            else -> emptySet()
        }
    }

    private fun updateRanking(state: GameState): GameState {
        var ranking = state.ranking
        state.seats.forEach { seat ->
            if (seat.color !in ranking && state.tokens.filter { it.color == seat.color }.all { it.progress == FINISHED }) {
                ranking = ranking + seat.color
            }
        }
        if (ranking.size == state.seats.size - 1) {
            ranking = ranking + state.seats.map(Seat::color).first { it !in ranking }
        }
        return state.copy(ranking = ranking, completed = ranking.size == state.seats.size)
    }

    private fun finishMove(state: GameState): GameState {
        if (state.completed) return state.copy(roll = null, diceBank = emptyList(), message = "${state.ranking.first().label} wins!")
        // Arrow/capture credits are rolled into the bank immediately so the
        // player can make one collective decision with every available value.
        if (state.pendingBonus > 0) return state.copy(
            roll = null,
            awaitingRoll = true,
            consecutiveSixes = 0,
            message = "Bonus roll",
        )
        if (state.diceBank.isNotEmpty()) return state.copy(
            roll = null,
            awaitingRoll = false,
            message = "Choose another dice value",
        )
        return advance(state.copy(roll = null, awaitingRoll = false))
    }

    private fun advance(state: GameState): GameState {
        var next = state.activeSeat
        do next = (next + 1) % state.seats.size
        while (state.seats[next].color in state.ranking)
        return state.copy(
            activeSeat = next,
            turn = state.turn + 1,
            diceBank = emptyList(),
            roll = null,
            awaitingRoll = true,
            consecutiveSixes = 0,
            message = "Roll the dice",
        )
    }

    private fun scoreMove(state: GameState, move: UnitMove): Int {
        val color = state.seats[state.activeSeat].color
        val resolvedDestination = arrowHead(color, move.destination) ?: move.destination
        val global = globalTrack(color, if (resolvedDestination <= 51) resolvedDestination else -1)
        val opponents = state.tokens.count { token ->
            token.color != color && token.progress in 0..51 && globalTrack(token.color, token.progress) == global
        }
        return when {
            resolvedDestination == FINISHED -> 10_000
            opponents > 0 -> 8_000 + opponents
            resolvedDestination > 51 -> 5_000 + resolvedDestination
            isProtected(color, resolvedDestination) -> 3_000
            move.enters -> 2_000
            else -> resolvedDestination
        }
    }
}

private val shortArrowTails = setOf(1, 4, 14, 17, 27, 30, 40, 43)

fun arrowHead(color: LudoColor, progress: Int): Int? {
    // The home-entry arrow belongs to the token's own route. Short arrows are
    // physical board features and work for every token color that lands there.
    if (progress == 48) return 52
    return if (progress in 0..51 && globalTrack(color, progress) in shortArrowTails) progress + 1 else null
}

fun globalTrack(color: LudoColor, progress: Int): Int = if (progress in 0..51) {
    ((offsets.getValue(color) + progress) % 52)
} else -1

fun isProtected(color: LudoColor, progress: Int): Boolean =
    progress in 0..51 && globalTrack(color, progress) in protectedTrack

fun advanceProgress(current: Int, steps: Int): Int {
    val rawDestination = current + steps
    return if (current <= 50 && rawDestination >= 51) rawDestination + 1 else rawDestination
}

fun movementAnimationProgresses(
    start: Int,
    baseDestination: Int,
    finalDestination: Int,
    enters: Boolean,
    arrow: Boolean,
): List<Int> {
    if (enters) return listOf(0)
    if (baseDestination <= start) return emptyList()
    val walked = (start + 1..baseDestination).filterNot { !arrow && baseDestination >= 52 && it == 51 }
    if (!arrow) return walked
    val arrowPath = if (finalDestination > baseDestination) {
        (baseDestination + 1..finalDestination).toList()
    } else {
        emptyList()
    }
    return (walked + arrowPath).filterNot { baseDestination == 48 && finalDestination == 52 && it == 51 }
}
