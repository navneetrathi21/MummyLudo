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
    val roll: Int? = null,
    val ranking: List<LudoColor> = emptyList(),
    val turn: Int = 1,
    val message: String = "Roll the dice",
    val completed: Boolean = false,
)

data class MoveResult(val state: GameState, val captured: Int, val arrow: Boolean)

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
        check(state.roll == null && !state.completed)
        val value = forced ?: generateRoll()
        require(value in 1..6)
        val streak = if (value == 6) state.consecutiveSixes + 1 else 0
        if (streak == 3) {
            return advance(state.copy(
                consecutiveSixes = 0,
                pendingBonus = 0,
                message = "Third six cancelled",
            ))
        }
        val bonus = state.pendingBonus + if (value == 6) 1 else 0
        val rolled = state.copy(roll = value, consecutiveSixes = streak, pendingBonus = bonus)
        val moves = legalMoves(rolled)
        return if (moves.isEmpty()) finishRoll(rolled.copy(message = "No legal move"))
        else rolled.copy(message = if (moves.size == 1) "One legal move" else "Choose a token")
    }

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
            when {
                current == YARD && die == 6 -> UnitMove(unit.map(Token::id), 0, 0, enters = true)
                current == YARD -> null
                current + steps > FINISHED -> null
                else -> UnitMove(unit.map(Token::id), current + steps, steps)
            }
        }
    }

    fun move(state: GameState, move: UnitMove): MoveResult {
        require(move in legalMoves(state)) { "Illegal move: $move" }
        val moving = state.tokens.filter { it.id in move.tokenIds }
        val color = moving.first().color
        var destination = move.destination
        var arrow = false
        if (destination == 1 || destination == 4) {
            destination += 1
            arrow = true
        }
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
            pendingBonus = state.pendingBonus + (if (arrow) 1 else 0) + (if (capturedIds.isNotEmpty()) 1 else 0),
            message = buildString {
                append(color.label)
                if (capturedIds.isNotEmpty()) append(" captured ${capturedIds.size}")
                else if (destination == FINISHED) append(" finished")
                else append(" moved")
                if (arrow) append(" via arrow")
            },
        )
        next = updateRanking(next)
        next = finishRoll(next)
        return MoveResult(next, capturedIds.size, arrow)
    }

    fun chooseBest(state: GameState): UnitMove = legalMoves(state).maxWith(
        compareBy<UnitMove> {
            val color = state.seats[state.activeSeat].color
            val global = globalTrack(color, if (it.destination <= 51) it.destination else -1)
            val opponents = state.tokens.count { token ->
                token.color != color && token.progress in 0..51 && globalTrack(token.color, token.progress) == global
            }
            when {
                it.destination == FINISHED -> 10_000
                opponents > 0 -> 8_000 + opponents
                it.destination > 51 -> 5_000 + it.destination
                isProtected(color, it.destination) -> 3_000
                it.enters -> 2_000
                else -> it.destination
            }
        }.thenBy { it.tokenIds.joinToString() },
    )

    fun autoStep(state: GameState, forcedRoll: Int? = null): GameState {
        if (state.completed) return state
        val rolled = commitRoll(state, forcedRoll)
        if (rolled.roll == null) return rolled
        val moves = legalMoves(rolled)
        return if (moves.isEmpty()) rolled else move(rolled, chooseBest(rolled)).state
    }

    fun simulate(initial: GameState, seed: Int, maxTurns: Int = 50_000): GameState {
        val random = Random(seed)
        var state = initial
        var steps = 0
        while (!state.completed && steps++ < maxTurns) state = autoStep(state, random.nextInt(1, 7))
        check(state.completed) { "Simulation exceeded $maxTurns rolls" }
        return state
    }

    private fun capturedAt(
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

    private fun finishRoll(state: GameState): GameState {
        if (state.completed) return state.copy(roll = null, message = "${state.ranking.first().label} wins!")
        return if (state.pendingBonus > 0) state.copy(
            roll = null,
            pendingBonus = state.pendingBonus - 1,
            message = "Bonus roll",
        ) else advance(state.copy(roll = null))
    }

    private fun advance(state: GameState): GameState {
        var next = state.activeSeat
        do next = (next + 1) % state.seats.size
        while (state.seats[next].color in state.ranking)
        return state.copy(activeSeat = next, turn = state.turn + 1, message = "Roll the dice")
    }
}

fun globalTrack(color: LudoColor, progress: Int): Int = if (progress in 0..51) {
    ((offsets.getValue(color) + progress) % 52)
} else -1

fun isProtected(color: LudoColor, progress: Int): Boolean =
    progress in 0..51 && globalTrack(color, progress) in protectedTrack
