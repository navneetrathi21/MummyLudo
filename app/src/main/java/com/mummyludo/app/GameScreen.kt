package com.mummyludo.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mummyludo.app.game.*
import com.mummyludo.app.nearby.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun GameScreen(
    state: GameState,
    engine: LudoEngine,
    onState: (GameState) -> Unit,
    onAutoPlay: () -> Unit,
    autoPlayRunning: Boolean,
    nearbyEvent: NearbyGameEvent? = null,
    localColor: LudoColor? = null,
    onNearbyAction: ((NearbyGameAction) -> Unit)? = null,
    onClose: () -> Unit,
    onQuit: () -> Unit = onClose,
) {
    var showClose by remember { mutableStateOf(false) }
    var showQuit by remember { mutableStateOf(false) }
    var rolling by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var animatedFace by remember { mutableIntStateOf(1) }
    var visualProgress by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var dicePath by remember { mutableStateOf(DiceRollPath()) }
    val scope = rememberCoroutineScope()
    val dieTravel = remember { Animatable(0f) }
    val confettiProgress = remember { Animatable(0f) }
    val legal = remember(state) { engine.legalMoves(state) }
    val seat = state.seats[state.activeSeat]

    BackHandler(enabled = !showClose && !showQuit) { showClose = true }

    LaunchedEffect(state.completed) {
        if (state.completed) {
            while (true) {
                confettiProgress.snapTo(0f)
                confettiProgress.animateTo(1f, tween(2600, easing = LinearEasing))
            }
        }
    }

    suspend fun animateRoll(forcedFace: Int? = null) {
        if (!state.awaitingRoll || rolling || moving) return
        val finalFace = forcedFace ?: engine.generateRoll()
        val committedState = engine.commitRoll(state, finalFace)
        dicePath = DiceRollPath.random()
        rolling = true
        dieTravel.snapTo(0f)
        val travel = scope.launch { dieTravel.animateTo(1f, tween(850)) }
        repeat(9) { frame ->
            animatedFace = ((finalFace + frame + 1) % 6) + 1
            delay(90)
        }
        travel.join()
        animatedFace = finalFace
        delay(450)
        rolling = false
        onState(engine.selectOnlyDie(committedState))
    }

    suspend fun animateMove(moveState: GameState, move: UnitMove) {
        if (moving || rolling) return
        moving = true
        val result = engine.move(moveState, move)
        val start = moveState.tokens.first { it.id in move.tokenIds }.progress
        val route = movementAnimationProgresses(
            start = start,
            baseDestination = result.baseDestination,
            finalDestination = result.finalDestination,
            enters = move.enters,
            arrow = result.arrow,
        )
        route.forEach { progress ->
            visualProgress = move.tokenIds.associateWith { progress }
            delay(190)
        }
        delay(100)
        visualProgress = emptyMap()
        moving = false
        onState(result.state)
    }

    LaunchedEffect(nearbyEvent?.sequence) {
        val event = nearbyEvent ?: return@LaunchedEffect
        when (val action = event.action) {
            NearbyGameAction.Roll -> animateRoll(requireNotNull(event.rollValue))
            is NearbyGameAction.SelectDie -> if (!state.awaitingRoll && state.roll == null && action.bankIndex in state.diceBank.indices) {
                onState(engine.selectDie(state, action.bankIndex))
            }
            is NearbyGameAction.Move -> engine.legalMoves(state)
                .firstOrNull { it.tokenIds.toSet() == action.tokenIds.toSet() }?.let { animateMove(state, it) }
        }
    }

    LaunchedEffect(state) {
        if (state.completed || seat.type != PlayerType.BOT || rolling || moving) return@LaunchedEffect
        delay(700)
        when {
            state.awaitingRoll -> if (onNearbyAction != null) onNearbyAction(NearbyGameAction.Roll) else animateRoll()
            state.roll == null && state.diceBank.isNotEmpty() -> {
                val candidates = state.diceBank.indices.map { index -> index to engine.selectDie(state, index) }
                val selected = candidates
                    .filter { engine.legalMoves(it.second).isNotEmpty() }
                    .maxByOrNull { state.diceBank[it.first] }
                    ?: candidates.first()
                if (onNearbyAction != null) onNearbyAction(NearbyGameAction.SelectDie(selected.first)) else onState(selected.second)
            }
            state.roll != null -> {
                val moves = engine.legalMoves(state)
                if (moves.isNotEmpty()) {
                    delay(650)
                    val best = engine.chooseBest(state)
                    if (onNearbyAction != null) onNearbyAction(NearbyGameAction.Move(best.tokenIds)) else animateMove(state, best)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("${seat.name} · ${seat.color.label}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Turn ${state.turn} · ${state.message}", color = Ink.copy(.62f), fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showClose = true }, modifier = Modifier.testTag("game_close_button")) { Text("Close") }
                Spacer(Modifier.width(2.dp))
                TextButton(onClick = { showQuit = true }, modifier = Modifier.testTag("game_quit_button")) {
                    Text("Quit", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val density = LocalDensity.current
            LudoBoard(
                state = state,
                visualProgress = visualProgress,
                legalIds = if (seat.type == PlayerType.HUMAN && !moving && (localColor == null || seat.color == localColor)) legal.flatMap { it.tokenIds }.toSet() else emptySet(),
                onToken = { id ->
                    if (!rolling && !moving && seat.type == PlayerType.HUMAN && (localColor == null || seat.color == localColor)) {
                        legal.firstOrNull { id in it.tokenIds }?.let { move ->
                            if (onNearbyAction != null) onNearbyAction(NearbyGameAction.Move(move.tokenIds))
                            else scope.launch { animateMove(state, move) }
                        }
                    }
                },
            )
            if (rolling) {
                Surface(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = with(density) { dicePath.x(dieTravel.value).dp.toPx() }
                            translationY = with(density) { dicePath.y(dieTravel.value).dp.toPx() }
                            rotationZ = dicePath.rotation(dieTravel.value)
                        }
                        .size(58.dp)
                        .align(Alignment.TopStart)
                        .semantics { contentDescription = "Rolling dice, face $animatedFace" }
                        .testTag("game_dice_overlay"),
                    color = Color.White,
                    shape = RoundedCornerShape(15.dp),
                    shadowElevation = 10.dp,
                ) { DiceFace(animatedFace) }
            }
            if (state.completed) {
                ConfettiOverlay(confettiProgress.value)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            state.seats.forEach { s ->
                val finished = state.tokens.count { it.color == s.color && it.progress == FINISHED }
                Text("${s.color.label.first()} $finished/4", color = colorOf(s.color), fontWeight = FontWeight.SemiBold)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth().height(286.dp).padding(top = 10.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .72f)),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(
                Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.completed) {
                    Text("${state.ranking.first().label} wins!", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = colorOf(state.ranking.first()))
                } else if (state.diceBank.isNotEmpty() || state.roll != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        state.diceBank.forEachIndexed { index, value ->
                            SmallDice(
                                value = value,
                                selected = false,
                                enabled = seat.type == PlayerType.HUMAN && !state.awaitingRoll && state.roll == null && !moving,
                                onClick = {
                                    if (onNearbyAction != null) onNearbyAction(NearbyGameAction.SelectDie(index))
                                    else onState(engine.selectDie(state, index))
                                },
                            )
                        }
                        state.roll?.let { value -> SmallDice(value, selected = true, enabled = false, onClick = {}) }
                    }
                } else {
                    Text(if (seat.type == PlayerType.BOT) "Bot is preparing to roll" else "Roll to begin", color = Ink.copy(.58f))
                }

                if (BuildConfig.DEBUG) {
                    TextButton(onClick = onAutoPlay, enabled = !state.completed && !rolling && !moving && !autoPlayRunning, modifier = Modifier.testTag("game_auto_play")) {
                        Text(if (autoPlayRunning) "Finishing…" else "Finish game automatically")
                    }
                }

                Button(
                    onClick = {
                        if (state.completed) onClose()
                        else if (onNearbyAction != null) onNearbyAction(NearbyGameAction.Roll)
                        else scope.launch { animateRoll() }
                    },
                    enabled = state.completed || (seat.type == PlayerType.HUMAN && (localColor == null || seat.color == localColor) && state.awaitingRoll && !rolling && !moving),
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("game_roll_action"),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        when {
                            state.completed -> "Close game"
                            rolling -> "Rolling…"
                            moving -> "Moving…"
                            seat.type == PlayerType.BOT -> "Bot is playing…"
                            state.awaitingRoll && state.diceBank.isNotEmpty() -> "Roll again"
                            state.awaitingRoll -> "Roll dice"
                            state.roll == null -> "Choose a dice value"
                            else -> "Choose a token"
                        },
                        fontSize = 17.sp,
                    )
                }
            }
        }
    }

    if (showClose) AlertDialog(
        onDismissRequest = { showClose = false },
        title = { Text("Close this game?") },
        text = { Text("Exit to the main menu. This match will be discarded.") },
        confirmButton = {
            Row {
                TextButton(
                    onClick = {
                        showClose = false
                        onClose()
                    },
                    modifier = Modifier.testTag("game_confirm_close_button"),
                ) {
                    Text("Close game")
                }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = {
                        showClose = false
                        onQuit()
                    },
                    modifier = Modifier.testTag("game_quit_app_button"),
                ) {
                    Text("Quit app", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = { TextButton(onClick = { showClose = false }) { Text("Keep playing") } },
    )

    if (showQuit) AlertDialog(
        onDismissRequest = { showQuit = false },
        title = { Text("Quit Mummy Ludo?") },
        text = { Text("Are you sure you want to quit the game and exit the application?") },
        confirmButton = {
            TextButton(
                onClick = {
                    showQuit = false
                    onQuit()
                },
                modifier = Modifier.testTag("game_confirm_quit_button"),
            ) {
                Text("Quit app", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { showQuit = false }) { Text("Cancel") } },
    )
}

@Composable
private fun ConfettiOverlay(progress: Float) {
    Canvas(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Confetti celebration" }
            .testTag("game_confetti"),
    ) {
        drawConfetti(progress)
    }
}

internal fun DrawScope.drawConfetti(progress: Float) {
    confettiPieces(progress, size.width, size.height).forEach { piece ->
        rotate(degrees = piece.rotation, pivot = Offset(piece.x, piece.y)) {
            drawRect(
                color = piece.color.copy(alpha = piece.alpha),
                topLeft = Offset(piece.x - piece.size, piece.y - piece.size * .45f),
                size = Size(piece.size * 2f, piece.size * .9f),
            )
        }
    }
}

internal data class ConfettiPiece(
    val color: Color,
    val x: Float,
    val y: Float,
    val size: Float,
    val rotation: Float,
    val alpha: Float,
)

internal fun confettiPieces(progress: Float, width: Float, height: Float): List<ConfettiPiece> {
    val colors = listOf(Red, Green, Yellow, Blue, Sage)
    return List(70) { index ->
        val lane = ((index * 37) % 100) / 100f
        val speed = .65f + ((index * 17) % 40) / 100f
        val fall = (progress * speed + ((index * 11) % 100) / 100f) % 1f
        val sway = kotlin.math.sin((progress * 6f + index) * Math.PI.toFloat()) * width * .045f
        ConfettiPiece(
            color = colors[index % colors.size],
            x = lane * width + sway,
            y = fall * (height + 70f) - 35f,
            size = 5f + (index % 4) * 2f,
            rotation = progress * 540f + index * 19f,
            alpha = (1f - progress * .25f).coerceIn(.65f, 1f),
        )
    }
}

@Composable
private fun SmallDice(value: Int, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(52.dp)
            .semantics { contentDescription = "Dice value $value${if (selected) ", selected" else ""}" }
            .testTag("dice_value_$value")
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Sage else Color.White,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Ink.copy(.14f)),
        shadowElevation = if (selected) 0.dp else 2.dp,
    ) { DiceFace(value, if (selected) Color.White else Ink) }
}

@Composable
private fun DiceFace(value: Int, pipColor: Color = Ink) {
    val pips = when (value) {
        1 -> setOf(4)
        2 -> setOf(0, 8)
        3 -> setOf(0, 4, 8)
        4 -> setOf(0, 2, 6, 8)
        5 -> setOf(0, 2, 4, 6, 8)
        else -> setOf(0, 2, 3, 5, 6, 8)
    }
    Column(
        Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        repeat(3) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(3) { column ->
                    val index = row * 3 + column
                    Box(
                        Modifier.size(7.dp).clip(CircleShape)
                            .background(if (index in pips) pipColor else Color.Transparent),
                    )
                }
            }
        }
    }
}
