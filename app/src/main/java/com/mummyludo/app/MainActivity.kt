package com.mummyludo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mummyludo.app.game.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val qaValues = if (BuildConfig.DEBUG) {
            intent.getStringExtra("qaDiceSequence")
                ?.split(',')
                ?.mapNotNull(String::toIntOrNull)
                ?.filter { it in 1..6 }
                ?.let(::ArrayDeque)
        } else null
        val engine = qaValues?.let { values ->
            LudoEngine { values.removeFirstOrNull() ?: kotlin.random.Random.nextInt(1, 7) }
        }
        setContent { MummyLudoTheme { MummyLudoApp(engine) } }
    }
}

private val Cream = Color(0xFFF7F3EA)
private val Ink = Color(0xFF26352F)
private val Sage = Color(0xFF3E6655)
private val Red = Color(0xFFE85C5C)
private val Green = Color(0xFF58A976)
private val Yellow = Color(0xFFF4CF45)
private val Blue = Color(0xFF4F83D1)

@Composable
private fun MummyLudoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Sage,
            onPrimary = Color.White,
            background = Cream,
            surface = Color.White,
            onSurface = Ink,
        ),
        content = content,
    )
}

private enum class Page { HOME, SETUP, GAME, RESULTS, RULES }

@Composable
fun MummyLudoApp(providedEngine: LudoEngine? = null) {
    var page by remember { mutableStateOf(Page.HOME) }
    var humanCount by remember { mutableIntStateOf(1) }
    var botCount by remember { mutableIntStateOf(1) }
    var reverseDuel by remember { mutableStateOf(false) }
    var game by remember { mutableStateOf<GameState?>(null) }
    var simulationSeed by remember { mutableIntStateOf(1001) }
    val engine = providedEngine ?: remember { LudoEngine() }

    Surface(Modifier.fillMaxSize(), color = Cream) {
        when (page) {
            Page.HOME -> HomeScreen(
                onNewGame = { page = Page.SETUP },
                onRules = { page = Page.RULES },
            )
            Page.SETUP -> SetupScreen(
                humanCount = humanCount,
                botCount = botCount,
                reverseDuel = reverseDuel,
                onHumans = {
                    humanCount = it
                    if (humanCount + botCount > 4) botCount = 4 - humanCount
                },
                onBots = {
                    botCount = it
                    if (humanCount + botCount > 4) humanCount = 4 - botCount
                },
                onReverse = { reverseDuel = !reverseDuel },
                onBack = { page = Page.HOME },
                onStart = {
                    val seats = createSeats(humanCount, botCount, reverseDuel)
                    game = engine.newGame(seats)
                    page = Page.GAME
                },
            )
            Page.GAME -> GameScreen(
                state = game!!,
                engine = engine,
                onState = { updated ->
                    game = updated
                    if (updated.completed) page = Page.RESULTS
                },
                onAutoPlay = {
                    game = engine.simulate(game!!, simulationSeed++)
                    page = Page.RESULTS
                },
                onClose = { game = null; page = Page.HOME },
            )
            Page.RESULTS -> ResultScreen(
                state = game!!,
                onAgain = { page = Page.SETUP },
                onHome = { game = null; page = Page.HOME },
            )
            Page.RULES -> RulesScreen { page = Page.HOME }
        }
    }
}

private fun createSeats(humans: Int, bots: Int, reverseDuel: Boolean): List<Seat> {
    val colors = if (humans + bots == 2) {
        if (reverseDuel) listOf(LudoColor.YELLOW, LudoColor.RED) else listOf(LudoColor.RED, LudoColor.YELLOW)
    } else LudoColor.entries.take(humans + bots)
    return colors.mapIndexed { index, color ->
        if (index < humans) Seat(color, PlayerType.HUMAN, "Player ${index + 1}")
        else Seat(color, PlayerType.BOT, "Bot ${index - humans + 1}")
    }
}

@Composable
private fun HomeScreen(onNewGame: () -> Unit, onRules: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.35f))
        MiniLogo()
        Spacer(Modifier.height(22.dp))
        Text("Mummy Ludo", fontSize = 36.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text("A fair game for everyone", color = Ink.copy(alpha = .62f), fontSize = 16.sp)
        Spacer(Modifier.weight(0.65f))
        Button(
            onClick = onNewGame,
            modifier = Modifier.fillMaxWidth().height(58.dp).testTag("home_new_game"),
            shape = RoundedCornerShape(18.dp),
        ) { Text("New game", fontSize = 18.sp) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRules, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
            Text("How to play")
        }
    }
}

@Composable
private fun MiniLogo() {
    Box(Modifier.size(110.dp).clip(RoundedCornerShape(28.dp)).background(Color.White)) {
        Box(Modifier.size(44.dp).align(Alignment.TopStart).background(Red))
        Box(Modifier.size(44.dp).align(Alignment.TopEnd).background(Green))
        Box(Modifier.size(44.dp).align(Alignment.BottomEnd).background(Yellow))
        Box(Modifier.size(44.dp).align(Alignment.BottomStart).background(Blue))
        Box(Modifier.size(30.dp).align(Alignment.Center).clip(CircleShape).background(Cream))
    }
}

@Composable
private fun SetupScreen(
    humanCount: Int,
    botCount: Int,
    reverseDuel: Boolean,
    onHumans: (Int) -> Unit,
    onBots: (Int) -> Unit,
    onReverse: () -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Match setup", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(28.dp))
        CountSelector("Human players", humanCount, 1..3, onHumans, "humans")
        Spacer(Modifier.height(20.dp))
        CountSelector("Bots", botCount, 1..3, onBots, "bots")
        Spacer(Modifier.height(22.dp))
        if (humanCount + botCount == 2) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onReverse).padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Opposite colors", fontWeight = FontWeight.Medium)
                        Text(if (reverseDuel) "Player Yellow · Bot Red" else "Player Red · Bot Yellow", color = Ink.copy(.65f))
                    }
                    Text("Swap", color = Sage)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("${humanCount + botCount} active colors · ${humanCount} human · ${botCount} bot", color = Ink.copy(.65f))
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStart,
            enabled = humanCount + botCount in 2..4,
            modifier = Modifier.fillMaxWidth().height(58.dp).testTag("setup_start"),
            shape = RoundedCornerShape(18.dp),
        ) { Text("Start game", fontSize = 18.sp) }
    }
}

@Composable
private fun CountSelector(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit, tag: String) {
    Text(label, fontWeight = FontWeight.Medium, fontSize = 17.sp)
    Spacer(Modifier.height(9.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        range.forEach { count ->
            val selected = count == value
            Surface(
                modifier = Modifier.weight(1f).height(50.dp).clickable { onChange(count) }.testTag("setup_${tag}_$count"),
                color = if (selected) Sage else Color.White,
                contentColor = if (selected) Color.White else Ink,
                shape = RoundedCornerShape(15.dp),
            ) { Box(contentAlignment = Alignment.Center) { Text(count.toString(), fontWeight = FontWeight.SemiBold) } }
        }
    }
}

@Composable
private fun GameScreen(
    state: GameState,
    engine: LudoEngine,
    onState: (GameState) -> Unit,
    onAutoPlay: () -> Unit,
    onClose: () -> Unit,
) {
    var showClose by remember { mutableStateOf(false) }
    var rolling by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var animatedFace by remember { mutableIntStateOf(1) }
    var visualProgress by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val dieTravel = remember { Animatable(0f) }
    val legal = remember(state) { engine.legalMoves(state) }
    val seat = state.seats[state.activeSeat]

    suspend fun animateRoll() {
        if (!state.awaitingRoll || rolling || moving) return
        val finalFace = engine.generateRoll()
        val committedState = engine.commitRoll(state, finalFace)
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
        onState(committedState)
    }

    suspend fun animateMove(moveState: GameState, move: UnitMove) {
        if (moving || rolling) return
        moving = true
        val result = engine.move(moveState, move)
        val start = moveState.tokens.first { it.id in move.tokenIds }.progress
        val route = when {
            move.enters -> listOf(0)
            result.baseDestination > start -> (start + 1..result.baseDestination).toList()
            else -> emptyList()
        }
        route.forEach { progress ->
            visualProgress = move.tokenIds.associateWith { progress }
            delay(190)
        }
        if (result.arrow) {
            delay(180)
            (result.baseDestination + 1..result.finalDestination).forEach { progress ->
                visualProgress = move.tokenIds.associateWith { progress }
                delay(165)
            }
        }
        delay(100)
        onState(result.state)
        visualProgress = emptyMap()
        moving = false
    }

    LaunchedEffect(state) {
        if (state.completed || seat.type != PlayerType.BOT || rolling || moving) return@LaunchedEffect
        delay(700)
        when {
            state.awaitingRoll -> animateRoll()
            state.roll == null && state.diceBank.isNotEmpty() -> {
                val candidates = state.diceBank.indices.map { index -> index to engine.selectDie(state, index) }
                val selected = candidates
                    .filter { engine.legalMoves(it.second).isNotEmpty() }
                    .maxByOrNull { state.diceBank[it.first] }
                    ?: candidates.first()
                onState(selected.second)
            }
            state.roll != null -> {
                val moves = engine.legalMoves(state)
                if (moves.isNotEmpty()) {
                    delay(650)
                    animateMove(state, engine.chooseBest(state))
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
            TextButton(onClick = { showClose = true }) { Text("Close") }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            LudoBoard(
                state = state,
                visualProgress = visualProgress,
                legalIds = if (seat.type == PlayerType.HUMAN && !moving) legal.flatMap { it.tokenIds }.toSet() else emptySet(),
                onToken = { id ->
                    if (!rolling && !moving && seat.type == PlayerType.HUMAN) {
                        legal.firstOrNull { id in it.tokenIds }?.let { move -> scope.launch { animateMove(state, move) } }
                    }
                },
            )
            if (rolling) {
                Surface(
                    modifier = Modifier
                        .offset(x = (dieTravel.value * 210).dp, y = (70 + kotlin.math.sin(dieTravel.value * 10) * 45).dp)
                        .size(58.dp)
                        .align(Alignment.TopStart)
                        .semantics { contentDescription = "Rolling dice, face $animatedFace" }
                        .testTag("game_dice_overlay"),
                    color = Color.White,
                    shape = RoundedCornerShape(15.dp),
                    shadowElevation = 10.dp,
                ) { DiceFace(animatedFace) }
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
                if (state.diceBank.isNotEmpty() || state.roll != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        state.diceBank.forEachIndexed { index, value ->
                            SmallDice(
                                value = value,
                                selected = false,
                                enabled = seat.type == PlayerType.HUMAN && !state.awaitingRoll && state.roll == null && !moving,
                                onClick = { onState(engine.selectDie(state, index)) },
                            )
                        }
                        state.roll?.let { value -> SmallDice(value, selected = true, enabled = false, onClick = {}) }
                    }
                } else {
                    Text(if (seat.type == PlayerType.BOT) "Bot is preparing to roll" else "Roll to begin", color = Ink.copy(.58f))
                }

                if (BuildConfig.DEBUG) {
                    TextButton(onClick = onAutoPlay, enabled = !rolling && !moving, modifier = Modifier.testTag("game_auto_play")) {
                        Text("Finish game automatically")
                    }
                }

                Button(
                    onClick = { scope.launch { animateRoll() } },
                    enabled = seat.type == PlayerType.HUMAN && state.awaitingRoll && !rolling && !moving,
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("game_roll_action"),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        when {
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
        text = { Text("This match will be discarded.") },
        confirmButton = { TextButton(onClick = onClose) { Text("Close game") } },
        dismissButton = { TextButton(onClick = { showClose = false }) { Text("Keep playing") } },
    )
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

@Composable
private fun LudoBoard(
    state: GameState,
    visualProgress: Map<String, Int>,
    legalIds: Set<String>,
    onToken: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Color.White).testTag("ludo_board")) {
        val cellDp = maxWidth / 15
        Canvas(Modifier.fillMaxSize()) { drawBoard() }
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.width / 15f
            val shownTokens = state.tokens.filter { it.progress != FINISHED }.map { token ->
                token.copy(progress = visualProgress[token.id] ?: token.progress)
            }
            shownTokens.forEach { shown ->
                val (cx, cy) = if (shown.progress == YARD) yardTokenLocation(shown) else tokenLocation(shown)
                val siblings = if (shown.progress == YARD) listOf(shown) else shownTokens.filter {
                    it.color == shown.color && it.progress == shown.progress
                }
                val slot = siblings.sortedBy { it.index }.indexOfFirst { it.id == shown.id }
                val spread = when (siblings.size) {
                    1 -> Offset.Zero
                    2 -> listOf(Offset(-.17f, 0f), Offset(.17f, 0f))[slot]
                    3 -> listOf(Offset(-.17f, .13f), Offset(.17f, .13f), Offset(0f, -.16f))[slot]
                    else -> listOf(Offset(-.16f, -.14f), Offset(.16f, -.14f), Offset(-.16f, .16f), Offset(.16f, .16f))[slot.coerceAtMost(3)]
                }
                val center = Offset((cx + .5f + spread.x) * cell, (cy + .5f + spread.y) * cell)
                if (shown.id in legalIds) drawCircle(Sage.copy(alpha = .25f), cell * .45f, Offset((cx + .5f) * cell, (cy + .5f) * cell))
                drawPawn(center, cell, colorOf(shown.color), if (siblings.size == 1) 1f else .72f)
            }
        }
        state.tokens.filter { it.id in legalIds }.forEach { token ->
            val (cx, cy) = if (token.progress == YARD) yardTokenLocation(token) else tokenLocation(token)
            Box(
                Modifier
                    .offset(x = cellDp * cx, y = cellDp * cy)
                    .size(cellDp)
                    .clickable { onToken(token.id) }
                    .semantics { contentDescription = "${token.color.label} token ${token.index + 1}" }
                    .testTag("token_${token.color.name.lowercase()}_${token.index}"),
            )
        }
    }
}

private fun DrawScope.drawPawn(center: Offset, cell: Float, color: Color, scale: Float) {
    drawOval(
        color = Ink.copy(alpha = .16f),
        topLeft = center + Offset(-cell * .24f * scale, cell * .06f * scale),
        size = androidx.compose.ui.geometry.Size(cell * .48f * scale, cell * .30f * scale),
    )
    drawOval(
        color = color,
        topLeft = center + Offset(-cell * .22f * scale, cell * .01f * scale),
        size = androidx.compose.ui.geometry.Size(cell * .44f * scale, cell * .29f * scale),
    )
    drawCircle(Color.White, cell * .23f * scale, center - Offset(0f, cell * .13f * scale))
    drawCircle(color, cell * .19f * scale, center - Offset(0f, cell * .13f * scale))
    drawCircle(Color.White.copy(alpha = .75f), cell * .055f * scale, center - Offset(cell * .065f * scale, cell * .19f * scale))
}

private val track = listOf(
    1 to 6, 2 to 6, 3 to 6, 4 to 6, 5 to 6, 6 to 5, 6 to 4, 6 to 3, 6 to 2, 6 to 1, 6 to 0,
    7 to 0, 8 to 0, 8 to 1, 8 to 2, 8 to 3, 8 to 4, 8 to 5, 9 to 6, 10 to 6, 11 to 6, 12 to 6,
    13 to 6, 14 to 6, 14 to 7, 14 to 8, 13 to 8, 12 to 8, 11 to 8, 10 to 8, 9 to 8, 8 to 9, 8 to 10,
    8 to 11, 8 to 12, 8 to 13, 8 to 14, 7 to 14, 6 to 14, 6 to 13, 6 to 12, 6 to 11, 6 to 10,
    6 to 9, 5 to 8, 4 to 8, 3 to 8, 2 to 8, 1 to 8, 0 to 8, 0 to 7, 0 to 6,
)

private fun tokenLocation(token: Token): Pair<Float, Float> {
    if (token.progress in 0..51) {
        val (x, y) = track[globalTrack(token.color, token.progress)]
        return x.toFloat() to y.toFloat()
    }
    val lane = (token.progress - 51).coerceIn(1, 5)
    return when (token.color) {
        LudoColor.RED -> lane.toFloat() to 7f
        LudoColor.GREEN -> 7f to lane.toFloat()
        LudoColor.YELLOW -> (14 - lane).toFloat() to 7f
        LudoColor.BLUE -> 7f to (14 - lane).toFloat()
    }
}

private fun yardTokenLocation(token: Token): Pair<Float, Float> {
    val spots = listOf(2f to 2f, 4f to 2f, 2f to 4f, 4f to 4f)
    val (x, y) = spots[token.index]
    return when (token.color) {
        LudoColor.RED -> x to y
        LudoColor.GREEN -> (14 - x) to y
        LudoColor.YELLOW -> (14 - x) to (14 - y)
        LudoColor.BLUE -> x to (14 - y)
    }
}

private fun DrawScope.drawBoard() {
    val c = size.width / 15f
    fun rect(x: Int, y: Int, w: Int, h: Int, color: Color) = drawRect(color, Offset(x * c, y * c), androidx.compose.ui.geometry.Size(w * c, h * c))
    rect(0, 0, 6, 6, Red.copy(.92f)); rect(9, 0, 6, 6, Green.copy(.92f))
    rect(9, 9, 6, 6, Yellow.copy(.95f)); rect(0, 9, 6, 6, Blue.copy(.92f))
    listOf(0 to 0, 9 to 0, 9 to 9, 0 to 9).forEach { (x, y) ->
        drawRoundRect(Color.White.copy(.88f), Offset((x + 1.1f) * c, (y + 1.1f) * c), androidx.compose.ui.geometry.Size(3.8f * c, 3.8f * c), CornerRadius(c * .45f))
    }
    track.forEach { (x, y) ->
        drawRect(Color.White, Offset(x * c, y * c), androidx.compose.ui.geometry.Size(c, c))
        drawRect(Ink.copy(.13f), Offset(x * c, y * c), androidx.compose.ui.geometry.Size(c, c), style = Stroke(1f))
    }
    for (i in 1..5) {
        rect(i, 7, 1, 1, Red.copy(.78f)); rect(7, i, 1, 1, Green.copy(.78f))
        rect(14 - i, 7, 1, 1, Yellow.copy(.88f)); rect(7, 14 - i, 1, 1, Blue.copy(.78f))
    }
    val center = Offset(7.5f * c, 7.5f * c)
    fun triangle(a: Offset, b: Offset, color: Color) {
        drawPath(Path().apply { moveTo(center.x, center.y); lineTo(a.x, a.y); lineTo(b.x, b.y); close() }, color)
    }
    triangle(Offset(6*c,6*c), Offset(6*c,9*c), Red)
    triangle(Offset(6*c,6*c), Offset(9*c,6*c), Green)
    triangle(Offset(9*c,6*c), Offset(9*c,9*c), Yellow)
    triangle(Offset(6*c,9*c), Offset(9*c,9*c), Blue)
    listOf(0,8,13,21,26,34,39,47).forEach { idx ->
        val (x,y) = track[idx]
        drawCircle(Ink.copy(.24f), c*.17f, Offset((x+.5f)*c,(y+.5f)*c), style = Stroke(c*.06f))
    }
    // Thin, restrained direction arrows on the short post-start hops.
    listOf(1 to 2, 4 to 5).forEach { (from, to) ->
        LudoColor.entries.forEach { color ->
            val a = track[(from + listOf(0,13,26,39)[color.ordinal]) % 52]
            val b = track[(to + listOf(0,13,26,39)[color.ordinal]) % 52]
            val p1 = Offset((a.first+.5f)*c,(a.second+.5f)*c)
            val p2 = Offset((b.first+.5f)*c,(b.second+.5f)*c)
            drawLine(colorOf(color).copy(.65f), p1, p2, c*.07f)
            val d = (p2-p1); val n = Offset(-d.y,d.x)
            drawLine(colorOf(color).copy(.65f), p2, p2-d*.23f+n*.18f, c*.07f)
            drawLine(colorOf(color).copy(.65f), p2, p2-d*.23f-n*.18f, c*.07f)
        }
    }
    // The long home-entry arrow follows every route cell from the post-safe tail into the lane.
    LudoColor.entries.forEach { color ->
        val points = (48..52).map { progress ->
            val (x, y) = tokenLocation(Token(color, 0, progress))
            Offset((x + .5f) * c, (y + .5f) * c)
        }
        points.zipWithNext().forEach { (start, end) ->
            drawLine(colorOf(color).copy(.28f), start, end, c * .032f)
        }
        val end = points.last()
        val previous = points[points.lastIndex - 1]
        val d = end - previous
        val n = Offset(-d.y, d.x)
        drawLine(colorOf(color).copy(.32f), end, end - d * .16f + n * .09f, c * .032f)
        drawLine(colorOf(color).copy(.32f), end, end - d * .16f - n * .09f, c * .032f)
    }
}

private fun colorOf(color: LudoColor): Color = when (color) {
    LudoColor.RED -> Red; LudoColor.GREEN -> Green; LudoColor.YELLOW -> Yellow; LudoColor.BLUE -> Blue
}

@Composable
private fun ResultScreen(state: GameState, onAgain: () -> Unit, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(50.dp))
        Text("Game complete", fontSize = 18.sp, color = Ink.copy(.62f))
        Text("${state.ranking.first().label} wins!", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("result_winner"))
        Spacer(Modifier.height(30.dp))
        state.ranking.forEachIndexed { index, color ->
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(45.dp))
                    Box(Modifier.size(18.dp).clip(CircleShape).background(colorOf(color)))
                    Spacer(Modifier.width(12.dp)); Text(color.label, fontSize = 18.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onAgain, Modifier.fillMaxWidth().height(56.dp).testTag("result_play_again"), shape = RoundedCornerShape(18.dp)) { Text("Play again") }
        TextButton(onClick = onHome) { Text("Home") }
    }
}

@Composable
private fun RulesScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text("How to play", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        val rules = listOf(
            "Roll a 6 to enter a token. Sixes stay in your dice bank while you roll again; a third 6 in a row cancels that turn.",
            "Land on an arrow tail to move to its head and earn an extra roll.",
            "Capturing earns one extra roll. An arrow capture therefore earns two.",
            "Two same-color tokens can move as a double on even rolls, travelling half the die value.",
            "A single cannot capture a double. A double captures two from a triple and all four from a four-token stack.",
            "Safe squares cannot be captured. Exact movement is required to finish.",
        )
        rules.forEachIndexed { index, rule ->
            Row(Modifier.padding(vertical = 11.dp)) {
                Surface(shape = CircleShape, color = Sage, modifier = Modifier.size(28.dp)) { Box(contentAlignment = Alignment.Center) { Text("${index + 1}", color = Color.White) } }
                Spacer(Modifier.width(12.dp)); Text(rule, fontSize = 16.sp, lineHeight = 23.sp)
            }
        }
    }
}
