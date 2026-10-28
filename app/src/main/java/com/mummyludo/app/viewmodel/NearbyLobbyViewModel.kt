package com.mummyludo.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mummyludo.app.game.GameState
import com.mummyludo.app.game.LudoColor
import com.mummyludo.app.game.LudoEngine
import com.mummyludo.app.nearby.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

open class NearbyLobbyViewModel(
    val coordinator: NearbyLobbyCoordinator = NearbyLobbyCoordinator(),
    private val externalScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope
        get() = externalScope ?: viewModelScope

    var hostLobby by mutableStateOf<NearbyLobbyState?>(null)
        internal set

    var guestLobby by mutableStateOf<NearbyLobbyState?>(null)
        internal set

    var nearbyError by mutableStateOf<String?>(null)
        internal set

    var hostSession by mutableStateOf<NearbyLanHost?>(null)
        internal set

    var clientSession by mutableStateOf<NearbyLanClient?>(null)
        internal set

    var nearbyEvent by mutableStateOf<NearbyGameEvent?>(null)
        internal set

    var nearbyLocalColor by mutableStateOf<LudoColor?>(null)
        internal set

    var isHost by mutableStateOf(false)
        internal set

    var sequence by mutableLongStateOf(0L)
        internal set

    fun startHosting(
        initialPlayerName: String = "Player 1",
        onStarted: (List<NearbyStartedSeat>) -> Unit,
        onGameAction: (LudoColor?, NearbyGameAction) -> Unit,
    ): Boolean {
        val initial = coordinator.createHost(initialPlayerName)
        hostLobby = initial
        isHost = true
        nearbyLocalColor = LudoColor.RED
        nearbyEvent = null
        nearbyError = null
        hostSession?.close()
        hostSession = null

        val host = runCatching {
            NearbyLanHost(
                initialLobby = initial,
                coordinator = coordinator,
                onLobby = { updated -> scope.launch { hostLobby = updated } },
                onStarted = { seats ->
                    scope.launch {
                        val hostColor = hostLobby?.players?.firstOrNull { it.isHost }?.color ?: LudoColor.RED
                        nearbyLocalColor = hostColor
                        onStarted(seats)
                    }
                },
                onGameAction = { playerId, action ->
                    scope.launch {
                        val playerColor = hostLobby?.players?.firstOrNull { it.id == playerId }?.color
                        onGameAction(playerColor, action)
                    }
                },
            )
        }.onFailure {
            nearbyError = "Could not start the local game server"
        }.getOrNull()

        hostSession = host
        return host != null
    }

    fun startJoining(
        payload: JoinPayload,
        initialName: String = "Player",
        onStarted: (List<NearbyStartedSeat>, LudoColor?) -> Unit,
    ) {
        clientSession?.close()
        guestLobby = null
        nearbyError = null
        isHost = false
        nearbyEvent = null

        lateinit var client: NearbyLanClient
        client = NearbyLanClient(
            payload = payload,
            initialName = initialName,
            onLobby = { updated -> scope.launch { guestLobby = updated } },
            onStarted = { seats ->
                scope.launch {
                    val localColor = guestLobby?.players?.firstOrNull { it.id == client.playerId }?.color
                    nearbyLocalColor = localColor
                    onStarted(seats, localColor)
                }
            },
            onError = { message -> scope.launch { nearbyError = message } },
            onGameEvent = { event -> scope.launch { nearbyEvent = event } },
        )
        clientSession = client
        client.connect()
    }

    fun updateGuestProfile(name: String, color: LudoColor?, ready: Boolean) {
        val current = guestLobby?.players?.firstOrNull { it.id == clientSession?.playerId }
        if (current != null && current.ready && ready && (current.color != color || current.name != name)) {
            return
        }
        clientSession?.updateProfile(name, color, ready)
    }

    fun updateHostProfile(name: String, color: LudoColor?, ready: Boolean) {
        val currentHost = hostLobby?.players?.find { it.isHost }
        if (currentHost != null && currentHost.ready && ready && (currentHost.color != color || currentHost.name != name)) {
            return
        }
        if (color != null && (!ready || currentHost == null || !currentHost.ready)) {
            nearbyLocalColor = color
        }
        hostSession?.updateProfile(name, color, ready)
        hostLobby?.let { current ->
            val hostPlayer = current.players.find { it.isHost } ?: return@let
            if (hostPlayer.ready && ready && (hostPlayer.color != color || hostPlayer.name != name)) {
                return@let
            }
            hostLobby = current.copy(
                players = current.players.map {
                    if (it.id == hostPlayer.id) {
                        if (hostPlayer.ready && !ready) it.copy(name = name, color = color, ready = false)
                        else if (!hostPlayer.ready) it.copy(name = name, color = color, ready = ready)
                        else it
                    } else it
                },
            )
        }
    }

    fun acceptNearbyAction(
        game: GameState?,
        color: LudoColor?,
        action: NearbyGameAction,
        engine: LudoEngine,
    ) {
        val current = game ?: return
        if (!isAuthorizedGameAction(current, color, action)) return
        val event = NearbyGameEvent(
            sequence = ++sequence,
            action = action,
            rollValue = if (action == NearbyGameAction.Roll) engine.generateRoll() else null,
        )
        hostSession?.broadcastGameEvent(event)
        nearbyEvent = event
    }

    fun sendClientAction(action: NearbyGameAction) {
        clientSession?.sendGameAction(action)
    }

    fun closeHost() {
        hostSession?.close()
        hostSession = null
        hostLobby = null
    }

    fun closeGuest() {
        clientSession?.close()
        clientSession = null
        guestLobby = null
    }

    fun closeAllSessions() {
        hostSession?.close()
        clientSession?.close()
        hostSession = null
        clientSession = null
        hostLobby = null
        guestLobby = null
        nearbyLocalColor = null
        nearbyEvent = null
        isHost = false
    }

    public override fun onCleared() {
        super.onCleared()
        closeAllSessions()
    }
}
