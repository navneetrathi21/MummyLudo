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
import com.mummyludo.app.viewmodel.*
import java.net.Inet4Address
import java.net.NetworkInterface

internal enum class Page { WELCOME, HOME, SETUP, GAME, RULES, NEARBY, HOST_LOBBY, JOIN_NEARBY, GUEST_LOBBY }

@Composable
fun MummyLudoApp(
    providedEngine: LudoEngine? = null,
    providedRepository: GameRepository? = null,
    providedNearbyCoordinator: NearbyLobbyCoordinator? = null,
    providedUserPreferences: UserPreferences? = null,
    gameViewModel: GameViewModel? = null,
    nearbyViewModel: NearbyLobbyViewModel? = null,
    onOpenWifiSettings: () -> Unit = {},
    wifiName: String? = null,
    scannedGameCode: String = "",
    onRequestWifiPermission: () -> Unit = {},
    onScanGameCode: () -> Unit = {},
    onExitApp: () -> Unit = {},
) {
    val context = LocalContext.current
    val appScope = rememberCoroutineScope()
    val engine = providedEngine ?: remember { LudoEngine() }
    val repository = providedRepository ?: remember { SqliteGameRepository(context) }
    val coordinator = providedNearbyCoordinator ?: remember { NearbyLobbyCoordinator() }
    val userPrefs = providedUserPreferences ?: remember { SharedUserPreferences(context) }

    val gameVm = gameViewModel ?: remember { GameViewModel(engine, repository, appScope) }
    val nearbyVm = nearbyViewModel ?: remember { NearbyLobbyViewModel(coordinator, appScope) }

    var playerName by remember { mutableStateOf(userPrefs.getPlayerName()) }
    var page by remember { mutableStateOf(if (userPrefs.hasCompletedWelcome()) Page.HOME else Page.WELCOME) }
    var humanCount by remember { mutableIntStateOf(1) }
    var botCount by remember { mutableIntStateOf(1) }
    var reverseDuel by remember { mutableStateOf(false) }
    var lastHomeBackAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(page) {
        if (page != Page.HOME) {
            lastHomeBackAt = 0L
        } else {
            gameVm.refreshSavedGameStatus()
            playerName = userPrefs.getPlayerName()
        }
    }

    BackHandler(enabled = page != Page.GAME) {
        when (page) {
            Page.WELCOME -> onExitApp()
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
                nearbyVm.closeHost()
                page = Page.NEARBY
            }
            Page.JOIN_NEARBY -> page = Page.NEARBY
            Page.GUEST_LOBBY -> {
                nearbyVm.closeGuest()
                page = Page.NEARBY
            }
            Page.GAME -> Unit
        }
    }

    Surface(Modifier.fillMaxSize(), color = Cream) {
        when (page) {
            Page.WELCOME -> WelcomeScreen(
                initialName = playerName,
                onContinue = { enteredName ->
                    userPrefs.setPlayerName(enteredName)
                    userPrefs.setCompletedWelcome(true)
                    playerName = enteredName
                    page = Page.HOME
                },
            )
            Page.HOME -> HomeScreen(
                hasSavedGame = gameVm.hasSavedGame,
                playerName = playerName,
                onContinueGame = {
                    if (gameVm.continueSavedGame()) {
                        page = Page.GAME
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
                primaryPlayerName = playerName,
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
                    val seats = createSeats(humanCount, botCount, reverseDuel, primaryPlayerName = playerName)
                    gameVm.startNewGame(seats)
                    page = Page.GAME
                },
            )
            Page.GAME -> GameScreen(
                state = gameVm.game!!,
                engine = gameVm.engine,
                onState = { updated ->
                    gameVm.updateGameState(updated, isNearby = nearbyVm.nearbyLocalColor != null)
                },
                onAutoPlay = {
                    gameVm.triggerAutoPlay(isNearby = nearbyVm.nearbyLocalColor != null)
                },
                autoPlayRunning = gameVm.autoPlayRunning,
                nearbyEvent = nearbyVm.nearbyEvent,
                localColor = nearbyVm.nearbyLocalColor,
                onNearbyAction = when {
                    nearbyVm.nearbyLocalColor == null -> null
                    nearbyVm.isHost -> { action ->
                        val activeColor = gameVm.game?.seats?.getOrNull(gameVm.game?.activeSeat ?: -1)?.color
                        nearbyVm.acceptNearbyAction(gameVm.game, activeColor, action, gameVm.engine)
                    }
                    else -> { action -> nearbyVm.sendClientAction(action) }
                },
                onClose = {
                    nearbyVm.closeAllSessions()
                    gameVm.clearAndCloseGame()
                    page = Page.HOME
                },
            )
            Page.RULES -> RulesScreen { page = Page.HOME }
            Page.NEARBY -> NearbyEntryScreen(
                onBack = { page = Page.HOME },
                onHost = {
                    onRequestWifiPermission()
                    nearbyVm.startHosting(
                        initialPlayerName = playerName.ifBlank { "Player 1" },
                        onStarted = { seats ->
                            gameVm.game = gameVm.engine.newGame(seats.toGameSeats())
                            page = Page.GAME
                        },
                        onGameAction = { playerColor, action ->
                            nearbyVm.acceptNearbyAction(gameVm.game, playerColor, action, gameVm.engine)
                        },
                    )
                    page = Page.HOST_LOBBY
                },
                onJoin = { page = Page.JOIN_NEARBY },
            )
            Page.HOST_LOBBY -> HostLobbyScreen(
                lobby = nearbyVm.hostLobby ?: coordinator.createHost(playerName.ifBlank { "Player 1" }).also { nearbyVm.hostLobby = it },
                hostAddress = remember(page) { localWifiAddress() },
                wifiName = wifiName,
                onBack = {
                    nearbyVm.closeHost()
                    page = Page.NEARBY
                },
                onOpenWifiSettings = onOpenWifiSettings,
                onStart = { nearbyVm.hostSession?.startGame() },
            )
            Page.JOIN_NEARBY -> JoinNearbyScreen(
                initialCode = scannedGameCode,
                onBack = { page = Page.NEARBY },
                onOpenWifiSettings = onOpenWifiSettings,
                onScan = onScanGameCode,
                onJoin = { payload ->
                    nearbyVm.startJoining(
                        payload = payload,
                        initialName = playerName.ifBlank { "Player" },
                        onStarted = { seats, _ ->
                            gameVm.game = gameVm.engine.newGame(seats.toGameSeats())
                            page = Page.GAME
                        },
                    )
                    page = Page.GUEST_LOBBY
                },
            )
            Page.GUEST_LOBBY -> GuestLobbyScreen(
                lobby = nearbyVm.guestLobby,
                playerId = nearbyVm.clientSession?.playerId.orEmpty(),
                error = nearbyVm.nearbyError,
                onBack = {
                    nearbyVm.closeGuest()
                    page = Page.NEARBY
                },
                onProfile = { name, color, ready -> nearbyVm.updateGuestProfile(name, color, ready) },
            )
        }
    }
}

internal fun List<NearbyStartedSeat>.toGameSeats(): List<Seat> = map {
    Seat(it.color, if (it.bot) PlayerType.BOT else PlayerType.HUMAN, it.name)
}

internal fun localWifiAddress(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>().firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress
}.getOrNull()

internal fun createSeats(
    humans: Int,
    bots: Int,
    reverseDuel: Boolean,
    primaryPlayerName: String = "Player 1",
): List<Seat> {
    val colors = if (humans + bots == 2) {
        if (reverseDuel) listOf(LudoColor.YELLOW, LudoColor.RED) else listOf(LudoColor.RED, LudoColor.YELLOW)
    } else LudoColor.entries.take(humans + bots)
    val p1 = primaryPlayerName.trim().ifBlank { "Player 1" }
    return colors.mapIndexed { index, color ->
        if (index == 0) Seat(color, PlayerType.HUMAN, p1)
        else if (index < humans) Seat(color, PlayerType.HUMAN, "Player ${index + 1}")
        else Seat(color, PlayerType.BOT, "Bot ${index - humans + 1}")
    }
}
