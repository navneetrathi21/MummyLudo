package com.mummyludo.app

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mummyludo.app.game.*
import com.mummyludo.app.nearby.*
import com.mummyludo.app.storage.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

private enum class Page { HOME, SETUP, GAME, RULES, NEARBY, HOST_LOBBY, JOIN_NEARBY, GUEST_LOBBY }

@Composable
fun MummyLudoApp(
    providedEngine: LudoEngine? = null,
    providedRepository: GameRepository? = null,
    providedNearbyCoordinator: NearbyLobbyCoordinator? = null,
    onOpenWifiSettings: () -> Unit = {},
    wifiName: String? = null,
    scannedGameCode: String = "",
    onRequestWifiPermission: () -> Unit = {},
    onScanGameCode: () -> Unit = {},
    onExitApp: () -> Unit = {},
) {
    val appScope = rememberCoroutineScope()
    var page by remember { mutableStateOf(Page.HOME) }
    var humanCount by remember { mutableIntStateOf(1) }
    var botCount by remember { mutableIntStateOf(1) }
    var reverseDuel by remember { mutableStateOf(false) }
    var game by remember { mutableStateOf<GameState?>(null) }
    var simulationSeed by remember { mutableIntStateOf(1001) }
    var autoPlayRunning by remember { mutableStateOf(false) }
    val nearbyCoordinator = providedNearbyCoordinator ?: remember { NearbyLobbyCoordinator() }
    var nearbyLobby by remember { mutableStateOf<NearbyLobbyState?>(null) }
    var clientLobby by remember { mutableStateOf<NearbyLobbyState?>(null) }
    var nearbyError by remember { mutableStateOf<String?>(null) }
    var hostSession by remember { mutableStateOf<NearbyLanHost?>(null) }
    var clientSession by remember { mutableStateOf<NearbyLanClient?>(null) }
    var nearbyEvent by remember { mutableStateOf<NearbyGameEvent?>(null) }
    var nearbyLocalColor by remember { mutableStateOf<LudoColor?>(null) }
    var nearbyIsHost by remember { mutableStateOf(false) }
    var nearbySequence by remember { mutableLongStateOf(0L) }
    var lastHomeBackAt by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    val engine = providedEngine ?: remember { LudoEngine() }
    val repository = providedRepository ?: remember { SqliteGameRepository(context) }
    var hasSavedGame by remember { mutableStateOf(repository.hasActiveGame()) }

    LaunchedEffect(page) {
        if (page != Page.HOME) {
            lastHomeBackAt = 0L
        } else {
            hasSavedGame = repository.hasActiveGame()
        }
    }

    fun acceptNearbyAction(color: LudoColor?, action: NearbyGameAction) {
        val current = game ?: return
        if (!isAuthorizedGameAction(current, color, action)) return
        val event = NearbyGameEvent(++nearbySequence, action, if (action == NearbyGameAction.Roll) engine.generateRoll() else null)
        hostSession?.broadcastGameEvent(event)
        nearbyEvent = event
    }

    BackHandler(enabled = page != Page.GAME) {
        when (page) {
            Page.HOME -> {
                val now = SystemClock.elapsedRealtime()
                if (lastHomeBackAt != 0L && now - lastHomeBackAt <= 2_000L) {
                    onExitApp()
                } else {
                    lastHomeBackAt = now
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
            Page.SETUP, Page.RULES, Page.NEARBY -> page = Page.HOME
            Page.HOST_LOBBY -> {
                hostSession?.close(); hostSession = null; nearbyLobby = null; page = Page.NEARBY
            }
            Page.JOIN_NEARBY -> page = Page.NEARBY
            Page.GUEST_LOBBY -> {
                clientSession?.close(); clientSession = null; clientLobby = null; page = Page.NEARBY
            }
            Page.GAME -> Unit
        }
    }

    Surface(Modifier.fillMaxSize(), color = Cream) {
        when (page) {
            Page.HOME -> HomeScreen(
                hasSavedGame = hasSavedGame,
                onContinueGame = {
                    val saved = repository.loadActiveGame()
                    if (saved != null && !saved.completed) {
                        game = saved
                        page = Page.GAME
                    } else {
                        hasSavedGame = false
                    }
                },
                onNewGame = { page = Page.SETUP },
                onNearby = { page = Page.NEARBY },
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
                    val newGame = engine.newGame(seats)
                    game = newGame
                    repository.saveActiveGame(newGame)
                    hasSavedGame = true
                    page = Page.GAME
                },
            )
            Page.GAME -> GameScreen(
                state = game!!,
                engine = engine,
                onState = { updated ->
                    game = updated
                    if (nearbyLocalColor == null) {
                        if (updated.completed) {
                            repository.clearActiveGame()
                            hasSavedGame = false
                        } else {
                            repository.saveActiveGame(updated)
                            hasSavedGame = true
                        }
                    }
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
                                if (nearbyLocalColor == null) {
                                    if (it.completed) {
                                        repository.clearActiveGame()
                                        hasSavedGame = false
                                    } else {
                                        repository.saveActiveGame(it)
                                        hasSavedGame = true
                                    }
                                }
                            }.onFailure {
                                game = snapshot.copy(message = "Could not finish automatically")
                            }
                            autoPlayRunning = false
                        }
                    }
                },
                autoPlayRunning = autoPlayRunning,
                nearbyEvent = nearbyEvent,
                localColor = nearbyLocalColor,
                onNearbyAction = when {
                    nearbyLocalColor == null -> null
                    nearbyIsHost -> { action ->
                        val activeColor = game?.seats?.getOrNull(game?.activeSeat ?: -1)?.color
                        acceptNearbyAction(activeColor, action)
                    }
                    else -> { action -> clientSession?.sendGameAction(action) }
                },
                onClose = {
                    hostSession?.close(); clientSession?.close()
                    hostSession = null; clientSession = null; game = null; nearbyLocalColor = null
                    repository.clearActiveGame()
                    hasSavedGame = false
                    page = Page.HOME
                },
            )
            Page.RULES -> RulesScreen { page = Page.HOME }
            Page.NEARBY -> NearbyEntryScreen(
                onBack = { page = Page.HOME },
                onHost = {
                    onRequestWifiPermission()
                    val initial = nearbyCoordinator.createHost("Player 1")
                    nearbyLobby = initial
                    nearbyIsHost = true
                    nearbyLocalColor = LudoColor.RED
                    nearbyEvent = null
                    hostSession?.close()
                    hostSession = runCatching {
                        NearbyLanHost(
                            initialLobby = initial,
                            coordinator = nearbyCoordinator,
                            onLobby = { updated -> appScope.launch { nearbyLobby = updated } },
                            onStarted = { seats -> appScope.launch { game = engine.newGame(seats.toGameSeats()); page = Page.GAME } },
                            onGameAction = { playerId, action -> appScope.launch {
                                acceptNearbyAction(nearbyLobby?.players?.firstOrNull { it.id == playerId }?.color, action)
                            } },
                        )
                    }.onFailure { nearbyError = "Could not start the local game server" }.getOrNull()
                    page = Page.HOST_LOBBY
                },
                onJoin = { page = Page.JOIN_NEARBY },
            )
            Page.HOST_LOBBY -> HostLobbyScreen(
                lobby = nearbyLobby ?: nearbyCoordinator.createHost("Player 1").also { nearbyLobby = it },
                hostAddress = remember(page) { localWifiAddress() },
                wifiName = wifiName,
                onBack = { hostSession?.close(); hostSession = null; nearbyLobby = null; page = Page.NEARBY },
                onOpenWifiSettings = onOpenWifiSettings,
                onStart = { hostSession?.startGame() },
            )
            Page.JOIN_NEARBY -> JoinNearbyScreen(
                initialCode = scannedGameCode,
                onBack = { page = Page.NEARBY },
                onOpenWifiSettings = onOpenWifiSettings,
                onScan = onScanGameCode,
                onJoin = { payload ->
                    clientSession?.close(); clientLobby = null; nearbyError = null; nearbyIsHost = false; nearbyEvent = null
                    lateinit var client: NearbyLanClient
                    client = NearbyLanClient(
                        payload = payload,
                        initialName = "Player",
                        onLobby = { updated -> appScope.launch { clientLobby = updated } },
                        onStarted = { seats -> appScope.launch {
                            nearbyLocalColor = clientLobby?.players?.firstOrNull { it.id == client.playerId }?.color
                            game = engine.newGame(seats.toGameSeats()); page = Page.GAME
                        } },
                        onError = { message -> appScope.launch { nearbyError = message } },
                        onGameEvent = { event -> appScope.launch { nearbyEvent = event } },
                    )
                    clientSession = client; page = Page.GUEST_LOBBY; client.connect()
                },
            )
            Page.GUEST_LOBBY -> GuestLobbyScreen(
                lobby = clientLobby,
                playerId = clientSession?.playerId.orEmpty(),
                error = nearbyError,
                onBack = { clientSession?.close(); clientSession = null; clientLobby = null; page = Page.NEARBY },
                onProfile = { name, color, ready -> clientSession?.updateProfile(name, color, ready) },
            )
        }
    }
}

private fun List<NearbyStartedSeat>.toGameSeats(): List<Seat> = map {
    Seat(it.color, if (it.bot) PlayerType.BOT else PlayerType.HUMAN, it.name)
}

private fun localWifiAddress(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>().firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress
}.getOrNull()

private fun createSeats(humans: Int, bots: Int, reverseDuel: Boolean): List<Seat> {
    val colors = if (humans + bots == 2) {
        if (reverseDuel) listOf(LudoColor.YELLOW, LudoColor.RED) else listOf(LudoColor.RED, LudoColor.YELLOW)
    } else LudoColor.entries.take(humans + bots)
    return colors.mapIndexed { index, color ->
        if (index < humans) Seat(color, PlayerType.HUMAN, "Player ${index + 1}")
        else Seat(color, PlayerType.BOT, "Bot ${index - humans + 1}")
    }
}
