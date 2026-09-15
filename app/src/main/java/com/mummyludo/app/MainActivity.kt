package com.mummyludo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mummyludo.app.game.*
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var appUpdateManager: AppUpdateManager
    private var updateReadyToInstall by mutableStateOf(false)
    private val updateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        // If the user cancels the Play update flow, gameplay continues normally.
    }
    private val updateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            updateReadyToInstall = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.registerListener(updateListener)
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
        setContent {
            MummyLudoTheme {
                Box(Modifier.fillMaxSize()) {
                    MummyLudoApp(engine)
                    if (updateReadyToInstall) {
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text("Update ready") },
                            text = { Text("A new version has been downloaded from Play Store.") },
                            confirmButton = {
                                TextButton(onClick = { appUpdateManager.completeUpdate() }) {
                                    Text("Restart")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { updateReadyToInstall = false }) {
                                    Text("Later")
                                }
                            },
                        )
                    }
                }
            }
        }
        checkForPlayStoreUpdate()
    }

    override fun onResume() {
        super.onResume()
        if (::appUpdateManager.isInitialized) {
            checkForDownloadedUpdate()
        }
    }

    override fun onDestroy() {
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.unregisterListener(updateListener)
        }
        super.onDestroy()
    }

    private fun checkForPlayStoreUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                )
            }
        }
    }

    private fun checkForDownloadedUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                updateReadyToInstall = true
            }
        }
    }
}

private val Cream = Color(0xFFF7F3EA)
private val Ink = Color(0xFF26352F)
private val Sage = Color(0xFF3E6655)
private val Red = Color(0xFFEF5B67)
private val Green = Color(0xFF49B675)
private val Yellow = Color(0xFFF6CB3D)
private val Blue = Color(0xFF438FE7)

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

private enum class Page { HOME, SETUP, GAME, RULES }

@Composable
fun MummyLudoApp(providedEngine: LudoEngine? = null) {
    val appScope = rememberCoroutineScope()
    var page by remember { mutableStateOf(Page.HOME) }
    var humanCount by remember { mutableIntStateOf(1) }
    var botCount by remember { mutableIntStateOf(1) }
    var reverseDuel by remember { mutableStateOf(false) }
    var game by remember { mutableStateOf<GameState?>(null) }
    var simulationSeed by remember { mutableIntStateOf(1001) }
    var autoPlayRunning by remember { mutableStateOf(false) }
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
                },
                onAutoPlay = {
                    if (!autoPlayRunning) {
                        autoPlayRunning = true
                        val snapshot = game!!
                        val seed = simulationSeed++
                        appScope.launch {
                            val completed = withContext(Dispatchers.Default) {
                                runCatching { engine.simulate(snapshot, seed) }
                            }
                            completed.onSuccess {
                                game = it
                            }.onFailure {
                                game = snapshot.copy(message = "Could not finish automatically")
                            }
                            autoPlayRunning = false
                        }
                    }
                },
                autoPlayRunning = autoPlayRunning,
                onClose = { game = null; page = Page.HOME },
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
    autoPlayRunning: Boolean,
    onClose: () -> Unit,
) {
    var showClose by remember { mutableStateOf(false) }
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

    LaunchedEffect(state.completed) {
        if (state.completed) {
            while (true) {
                confettiProgress.snapTo(0f)
                confettiProgress.animateTo(1f, tween(2600, easing = LinearEasing))
            }
        }
    }

    suspend fun animateRoll() {
        if (!state.awaitingRoll || rolling || moving) return
        val finalFace = engine.generateRoll()
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
            val density = LocalDensity.current
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
                                onClick = { onState(engine.selectDie(state, index)) },
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
                        else scope.launch { animateRoll() }
                    },
                    enabled = state.completed || (seat.type == PlayerType.HUMAN && state.awaitingRoll && !rolling && !moving),
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
        text = { Text("This match will be discarded.") },
        confirmButton = { TextButton(onClick = onClose) { Text("Close game") } },
        dismissButton = { TextButton(onClick = { showClose = false }) { Text("Keep playing") } },
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
        val colors = listOf(Red, Green, Yellow, Blue, Sage)
        repeat(70) { index ->
            val lane = ((index * 37) % 100) / 100f
            val speed = .65f + ((index * 17) % 40) / 100f
            val fall = (progress * speed + ((index * 11) % 100) / 100f) % 1f
            val sway = kotlin.math.sin((progress * 6f + index) * Math.PI.toFloat()) * size.width * .045f
            val x = lane * size.width + sway
            val y = fall * (size.height + 70f) - 35f
            val piece = 5f + (index % 4) * 2f
            rotate(degrees = progress * 540f + index * 19f, pivot = Offset(x, y)) {
                drawRect(
                    color = colors[index % colors.size].copy(alpha = (1f - progress * .25f).coerceIn(.65f, 1f)),
                    topLeft = Offset(x - piece, y - piece * .45f),
                    size = Size(piece * 2f, piece * .9f),
                )
            }
        }
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

@Composable
private fun LudoBoard(
    state: GameState,
    visualProgress: Map<String, Int>,
    legalIds: Set<String>,
    onToken: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color.White).testTag("ludo_board")) {
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
    // The approved inset home occupies 1.1..4.9 cells. These values place the
    // pawn centers at 2.2/3.8 after LudoBoard adds the standard half-cell offset.
    val spots = listOf(1.7f to 1.7f, 3.3f to 1.7f, 1.7f to 3.3f, 3.3f to 3.3f)
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
    val yardColors = listOf(
        Color(0xFFF1727C), Color(0xFF63C086), Color(0xFFF7D04A), Color(0xFF5D9CE0),
    )
    rect(0, 0, 6, 6, yardColors[0]); rect(9, 0, 6, 6, yardColors[1])
    rect(9, 9, 6, 6, yardColors[2]); rect(0, 9, 6, 6, yardColors[3])
    listOf(0 to 0, 9 to 0, 9 to 9, 0 to 9).forEachIndexed { index, (x, y) ->
        drawRoundRect(
            Color.White,
            Offset((x + 1.1f) * c, (y + 1.1f) * c),
            androidx.compose.ui.geometry.Size(3.8f * c, 3.8f * c),
            CornerRadius(c * .62f),
        )
        drawRoundRect(
            yardColors[index].copy(alpha = .24f),
            Offset((x + 1.1f) * c, (y + 1.1f) * c),
            androidx.compose.ui.geometry.Size(3.8f * c, 3.8f * c),
            CornerRadius(c * .62f),
            style = Stroke(c * .035f),
        )
        val localCenters = listOf(2.2f to 2.2f, 3.8f to 2.2f, 2.2f to 3.8f, 3.8f to 3.8f)
        localCenters.forEach { (localX, localY) ->
            drawCircle(
                yardColors[index].copy(alpha = .13f),
                c * .48f,
                Offset((x + localX) * c, (y + localY) * c),
            )
            drawCircle(
                yardColors[index].copy(alpha = .32f),
                c * .48f,
                Offset((x + localX) * c, (y + localY) * c),
                style = Stroke(c * .035f),
            )
        }
    }
    track.forEach { (x, y) ->
        drawRect(Color.White, Offset(x * c, y * c), androidx.compose.ui.geometry.Size(c, c))
        drawRect(Ink.copy(.13f), Offset(x * c, y * c), androidx.compose.ui.geometry.Size(c, c), style = Stroke(1f))
    }
    for (i in 1..5) {
        rect(i, 7, 1, 1, Color(0xFFF7A3AA)); rect(7, i, 1, 1, Color(0xFF9AD9B4))
        rect(14 - i, 7, 1, 1, Color(0xFFF9DF86)); rect(7, 14 - i, 1, 1, Color(0xFF9BC7F1))
    }
    listOf(
        Triple(track[0], Color(0xFFF36E78), Red),
        Triple(track[13], Color(0xFF79CA9A), Green),
        Triple(track[26], Color(0xFFF9D966), Yellow),
        Triple(track[39], Color(0xFF72A8EC), Blue),
    ).forEach { (cell, fill, _) -> rect(cell.first, cell.second, 1, 1, fill) }
    val center = Offset(7.5f * c, 7.5f * c)
    fun triangle(a: Offset, b: Offset, color: Color) {
        drawPath(Path().apply { moveTo(center.x, center.y); lineTo(a.x, a.y); lineTo(b.x, b.y); close() }, color)
    }
    triangle(Offset(6*c,6*c), Offset(6*c,9*c), Red)
    triangle(Offset(6*c,6*c), Offset(9*c,6*c), Green)
    triangle(Offset(9*c,6*c), Offset(9*c,9*c), Yellow)
    triangle(Offset(6*c,9*c), Offset(9*c,9*c), Blue)
    listOf(8, 21, 34, 47).forEach { idx ->
        val (x,y) = track[idx]
        val centerPoint = Offset((x + .5f) * c, (y + .5f) * c)
        drawCircle(Ink.copy(.08f), c * .34f, centerPoint)
        val star = Path()
        repeat(10) { point ->
            val angle = -Math.PI / 2 + point * Math.PI / 5
            val radius = if (point % 2 == 0) c * .25f else c * .11f
            val px = centerPoint.x + kotlin.math.cos(angle).toFloat() * radius
            val py = centerPoint.y + kotlin.math.sin(angle).toFloat() * radius
            if (point == 0) star.moveTo(px, py) else star.lineTo(px, py)
        }
        star.close()
        drawPath(star, Ink.copy(alpha = .48f))
    }
    // Thin, restrained direction arrows on the short post-start hops.
    listOf(1 to 2, 4 to 5).forEach { (from, to) ->
        LudoColor.entries.forEach { color ->
            val a = track[(from + listOf(0,13,26,39)[color.ordinal]) % 52]
            val b = track[(to + listOf(0,13,26,39)[color.ordinal]) % 52]
            val p1 = Offset((a.first+.5f)*c,(a.second+.5f)*c)
            val p2 = Offset((b.first+.5f)*c,(b.second+.5f)*c)
            drawLine(Ink.copy(.46f), p1, p2, c*.052f)
            val d = (p2-p1); val n = Offset(-d.y,d.x)
            drawLine(Ink.copy(.46f), p2, p2-d*.23f+n*.14f, c*.052f)
            drawLine(Ink.copy(.46f), p2, p2-d*.23f-n*.14f, c*.052f)
        }
    }
    // Exact four-block paths from the approved reference (tail and head included).
    val homeArrowCells = listOf(
        listOf(1 to 8, 0 to 8, 0 to 7, 1 to 7),
        listOf(6 to 1, 6 to 0, 7 to 0, 7 to 1),
        listOf(13 to 6, 14 to 6, 14 to 7, 13 to 7),
        listOf(8 to 13, 8 to 14, 7 to 14, 7 to 13),
    )
    homeArrowCells.forEach { cells ->
        val points = cells.map { (x, y) -> Offset((x + .5f) * c, (y + .5f) * c) }
        points.zipWithNext().forEach { (start, end) ->
            drawLine(Ink.copy(.30f), start, end, c * .032f)
        }
        val end = points.last()
        val previous = points[points.lastIndex - 1]
        val d = end - previous
        val n = Offset(-d.y, d.x)
        drawLine(Ink.copy(.34f), end, end - d * .16f + n * .09f, c * .032f)
        drawLine(Ink.copy(.34f), end, end - d * .16f - n * .09f, c * .032f)
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
            "Roll a 6 to enter a token. Three consecutive 6s are discarded, then the same player rolls again.",
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
