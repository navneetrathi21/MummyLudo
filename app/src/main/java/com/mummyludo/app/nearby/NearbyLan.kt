package com.mummyludo.app.nearby

import com.mummyludo.app.game.LudoColor
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

data class NearbyStartedSeat(val color: LudoColor, val name: String, val bot: Boolean = false)

class NearbyLanHost(
    initialLobby: NearbyLobbyState,
    private val coordinator: NearbyLobbyCoordinator,
    private val onLobby: (NearbyLobbyState) -> Unit,
    private val onStarted: (List<NearbyStartedSeat>) -> Unit,
    private val onGameAction: (String, NearbyGameAction) -> Unit = { _, _ -> },
    val port: Int = 42871,
) : Closeable {
    private val server = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(port))
    }
    private val clients = ConcurrentHashMap<String, BufferedWriter>()
    @Volatile private var lobby = initialLobby
    @Volatile private var running = true

    init {
        thread(name = "mummy-ludo-host", isDaemon = true) {
            while (running) runCatching { server.accept() }.onSuccess(::handle)
        }
    }

    private fun handle(socket: Socket) {
        thread(name = "mummy-ludo-client", isDaemon = true) {
            socket.use {
                val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                val writer = BufferedWriter(OutputStreamWriter(it.getOutputStream()))
                val join = reader.readLine()?.split('|') ?: return@thread
                if (join.size >= 2 && join[0] == "CHECK") {
                    val requested = join[1].trim()
                    if (requested.equals(lobby.joinToken.trim(), ignoreCase = true) || requested.equals(lobby.roomCode.trim(), ignoreCase = true)) {
                        writer.send("ROOM|${lobby.roomCode}")
                    } else {
                        writer.send("MISMATCH")
                    }
                    return@thread
                }
                val token = decode(join[1]).trim()
                if (join.size != 4 || join[0] != "JOIN" || (!token.equals(lobby.joinToken.trim(), ignoreCase = true) && !token.equals(lobby.roomCode.trim(), ignoreCase = true))) {
                    writer.send("ERROR|${encode("This game code has expired")}")
                    return@thread
                }
                val playerId = decode(join[2])
                val playerName = decode(join[3])
                synchronized(this@NearbyLanHost) {
                    lobby = coordinator.join(lobby, playerId, playerName)
                    clients[playerId] = writer
                    publishLobby()
                }
                try {
                    while (running) {
                        val parts = reader.readLine()?.split('|') ?: break
                        if (parts.size == 5 && parts[0] == "PROFILE" && decode(parts[1]) == playerId) {
                            val name = decode(parts[2]).trim().take(24).ifBlank { "Player" }
                            val color = parts[3].takeUnless { value -> value == "-" }?.let(LudoColor::valueOf)
                            val ready = parts[4].toBooleanStrictOrNull() ?: false
                            synchronized(this@NearbyLanHost) {
                                lobby = rename(lobby, playerId, name)
                                if (color != null) lobby = coordinator.chooseColor(lobby, playerId, color)
                                lobby = coordinator.setReady(lobby, playerId, ready)
                                publishLobby()
                            }
                        } else if (parts.size == 2 && parts[0] == "ACTION") {
                            NearbyGameActionCodec.decode(decode(parts[1]))?.let { action ->
                                onGameAction(playerId, action)
                            }
                        }
                    }
                } finally {
                    clients.remove(playerId)
                    synchronized(this@NearbyLanHost) {
                        lobby = lobby.copy(players = lobby.players.filterNot { player -> player.id == playerId })
                        publishLobby()
                    }
                }
            }
        }
    }

    @Synchronized
    fun startGame(): Boolean {
        // Only allow starting when the lobby is ready *and* at least one client has joined.
        // This prevents a host‑only game from incorrectly transitioning to playing state.
        if (!lobby.canStart || clients.isEmpty()) return false
        val seats = lobby.players.mapNotNull { player ->
            player.color?.let { NearbyStartedSeat(it, player.name) }
        }.sortedBy { it.color.ordinal }
        val message = "START|${encodeSeats(seats)}"
        // Send the START message to all connected clients.
        clients.values.forEach { writer -> runCatching { writer.send(message) } }
        // Small delay ensures any in‑flight PROFILE updates are processed before we mark game started.
        Thread.sleep(50)
        onStarted(seats)
        return true
    }

    fun broadcastGameEvent(event: NearbyGameEvent) {
        val message = "EVENT|${encode(NearbyGameEventCodec.encode(event))}"
        clients.values.forEach { writer -> runCatching { writer.send(message) } }
    }


    /**
     * Update the host's own profile.  Clients normally send a PROFILE message to the
     * server; in the host case we need to mutate the lobby directly.
     */
    @Synchronized
    fun updateProfile(name: String, color: LudoColor?, ready: Boolean) {
        val hostPlayer = lobby.players.find { it.isHost } ?: return
        lobby = lobby.copy(
            players = lobby.players.map {
                if (it.id == hostPlayer.id)
                    it.copy(name = name, color = color, ready = ready)
                else it
            }
        )
        publishLobby()
    }
    @Synchronized
    private fun publishLobby() {
        onLobby(lobby)
        val message = "LOBBY|${encodeLobby(lobby)}"
        clients.values.forEach { writer -> runCatching { writer.send(message) } }
    }

    override fun close() {
        running = false
        runCatching { server.close() }
        clients.values.forEach { writer -> runCatching { writer.close() } }
        clients.clear()
    }
}

class NearbyLanClient(
    private val payload: JoinPayload,
    private val initialName: String,
    private val onLobby: (NearbyLobbyState) -> Unit,
    private val onStarted: (List<NearbyStartedSeat>) -> Unit,
    private val onError: (String) -> Unit,
    private val onGameEvent: (NearbyGameEvent) -> Unit = {},
) : Closeable {
    val playerId: String = UUID.randomUUID().toString()
    @Volatile private var socket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null

    fun connect() {
        thread(name = "mummy-ludo-join", isDaemon = true) {
            runCatching {
                val connected = Socket().apply { connect(InetSocketAddress(payload.hostAddress, payload.port), 5_000) }
                socket = connected
                val output = BufferedWriter(OutputStreamWriter(connected.getOutputStream()))
                writer = output
                output.send("JOIN|${encode(payload.joinToken)}|${encode(playerId)}|${encode(initialName)}")
                val reader = BufferedReader(InputStreamReader(connected.getInputStream()))
                while (!connected.isClosed) {
                    val parts = reader.readLine()?.split('|', limit = 2) ?: break
                    when (parts[0]) {
                        "LOBBY" -> onLobby(decodeLobby(payload, parts.getOrElse(1) { "" }))
                        "START" -> onStarted(decodeSeats(parts.getOrElse(1) { "" }))
                        "ERROR" -> onError(decode(parts.getOrElse(1) { "" }))
                        "EVENT" -> NearbyGameEventCodec.decode(decode(parts.getOrElse(1) { "" }))?.let(onGameEvent)
                    }
                }
            }.onFailure { onError("Could not reach the host on this Wi-Fi") }
        }
    }

    fun updateProfile(name: String, color: LudoColor?, ready: Boolean) {
        writer?.let { output ->
            runCatching {
                output.send("PROFILE|${encode(playerId)}|${encode(name.take(24))}|${color?.name ?: "-"}|$ready")
            }.onFailure { onError("Connection to the host was lost") }
        }
    }

    fun sendGameAction(action: NearbyGameAction) {
        writer?.let { output ->
            runCatching { output.send("ACTION|${encode(NearbyGameActionCodec.encode(action))}") }
                .onFailure { onError("Connection to the host was lost") }
        }
    }

    override fun close() {
        runCatching { socket?.close() }
        writer = null
    }
}

private fun rename(state: NearbyLobbyState, playerId: String, name: String): NearbyLobbyState =
    state.copy(players = state.players.map { if (it.id == playerId) it.copy(name = name, ready = false) else it })

private fun encodeLobby(state: NearbyLobbyState): String = state.players.joinToString(";") { player ->
    listOf(encode(player.id), encode(player.name), player.isHost, player.color?.name ?: "-", player.ready).joinToString(",")
}

private fun decodeLobby(payload: JoinPayload, raw: String): NearbyLobbyState = NearbyLobbyState(
    sessionId = payload.sessionId,
    joinToken = payload.joinToken,
    players = raw.split(';').filter { it.isNotBlank() }.map { record ->
        val fields = record.split(',')
        NearbyPlayer(
            id = decode(fields[0]),
            name = decode(fields[1]),
            isHost = fields[2].toBoolean(),
            color = fields[3].takeUnless { it == "-" }?.let(LudoColor::valueOf),
            ready = fields[4].toBoolean(),
        )
    },
)

private fun encodeSeats(seats: List<NearbyStartedSeat>): String = seats.joinToString(";") {
    "${it.color.name},${encode(it.name)},${it.bot}"
}

private fun decodeSeats(raw: String): List<NearbyStartedSeat> = raw.split(';').filter { it.isNotBlank() }.map { record ->
    val fields = record.split(',')
    NearbyStartedSeat(LudoColor.valueOf(fields[0]), decode(fields[1]), fields[2].toBoolean())
}

private fun encode(value: Any): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(value.toString().toByteArray(Charsets.UTF_8))

private fun decode(value: String): String = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)

private fun BufferedWriter.send(message: String) {
    synchronized(this) {
        write(message)
        newLine()
        flush()
    }
}

suspend fun discoverHostByRoomCode(
    roomCode: String,
    localIp: String? = null,
    port: Int = 42871,
    subnetRange: IntProgression = 1..254,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): JoinPayload? = withContext(ioDispatcher) {
    val cleanCode = roomCode.trim()
    if (cleanCode.length != 4 || !cleanCode.all { it.isDigit() }) return@withContext null

    val priorityIps = listOfNotNull("127.0.0.1".takeIf { localIp == null || localIp == "127.0.0.1" }, localIp).distinct()
    for (ip in priorityIps) {
        if (checkHost(ip, port, cleanCode)) {
            return@withContext JoinPayload(
                sessionId = cleanCode,
                hostAddress = ip,
                port = port,
                joinToken = cleanCode,
            )
        }
    }

    if (localIp != null && localIp.count { it == '.' } == 3) {
        val subnetPrefix = localIp.substringBeforeLast('.') + "."
        val foundIp = subnetRange.map { i -> "$subnetPrefix$i" }
            .filterNot { it in priorityIps }
            .map { ip ->
                async {
                    if (checkHost(ip, port, cleanCode, timeoutMs = 200)) ip else null
                }
            }
            .awaitAll()
            .firstOrNull { it != null }

        if (foundIp != null) {
            return@withContext JoinPayload(
                sessionId = cleanCode,
                hostAddress = foundIp,
                port = port,
                joinToken = cleanCode,
            )
        }
    }

    null
}

internal fun checkHost(ip: String, port: Int, roomCode: String, timeoutMs: Int = 200): Boolean = runCatching {
    Socket().use { socket ->
        socket.setSoLinger(true, 0)
        socket.connect(InetSocketAddress(ip, port), timeoutMs)
        socket.soTimeout = timeoutMs
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        writer.write("CHECK|$roomCode\n")
        writer.flush()
        val response = reader.readLine()
        response != null && response.trim() == "ROOM|$roomCode"
    }
}.getOrDefault(false)
