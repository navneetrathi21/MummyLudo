package com.mummyludo.app.nearby

import com.mummyludo.app.game.LudoColor
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

const val NEARBY_PROTOCOL_VERSION = 1

fun cleanWifiSsid(raw: String?): String? = raw
    ?.trim()
    ?.trim('"')
    ?.takeUnless { it.isBlank() || it.equals("<unknown ssid>", ignoreCase = true) }

fun requiredWifiPermissions(sdkInt: Int): List<String> = buildList {
    add("android.permission.ACCESS_FINE_LOCATION")
    add("android.permission.ACCESS_COARSE_LOCATION")
    if (sdkInt >= 33) add("android.permission.NEARBY_WIFI_DEVICES")
}

data class JoinPayload(
    val sessionId: String,
    val hostAddress: String,
    val port: Int,
    val joinToken: String,
    val protocolVersion: Int = NEARBY_PROTOCOL_VERSION,
)

object JoinPayloadCodec {
    fun encode(payload: JoinPayload): String = buildString {
        append("mummyludo://join?")
        append("v=${payload.protocolVersion}")
        append("&session=${payload.sessionId.urlEncode()}")
        append("&host=${payload.hostAddress.urlEncode()}")
        append("&port=${payload.port}")
        append("&token=${payload.joinToken.urlEncode()}")
    }

    fun decode(raw: String): JoinPayload? = runCatching {
        val uri = URI(raw.trim())
        require(uri.scheme == "mummyludo" && uri.host == "join")
        val values = uri.rawQuery.orEmpty()
            .split('&')
            .filter { it.contains('=') }
            .associate { part ->
                val (key, value) = part.split('=', limit = 2)
                key to URLDecoder.decode(value, StandardCharsets.UTF_8.name())
            }
        val version = values.getValue("v").toInt()
        require(version == NEARBY_PROTOCOL_VERSION)
        val port = values.getValue("port").toInt()
        require(port in 1..65535)
        JoinPayload(
            sessionId = values.getValue("session").takeIf { it.length in 4..80 } ?: error("Invalid session"),
            hostAddress = values.getValue("host").takeIf { it.length in 3..255 } ?: error("Invalid host"),
            port = port,
            joinToken = values.getValue("token").takeIf { it.length in 4..200 } ?: error("Invalid token"),
            protocolVersion = version,
        )
    }.getOrNull()

    private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}

enum class NearbyConnectionState { CONNECTED, DISCONNECTED }

data class NearbyPlayer(
    val id: String,
    val name: String,
    val isHost: Boolean,
    val color: LudoColor?,
    val ready: Boolean,
    val connection: NearbyConnectionState = NearbyConnectionState.CONNECTED,
    val team: Int? = null,
    val isBot: Boolean = false,
)

data class NearbyLobbyState(
    val sessionId: String,
    val joinToken: String,
    val players: List<NearbyPlayer>,
    val botColors: Set<LudoColor> = emptySet(),
    val roomCode: String = sessionId.take(4),
) {
    val canStart: Boolean
        get() = players.size in 2..4 &&
            players.all { it.color != null && (it.ready || it.isBot) } &&
            players.mapNotNull { it.color }.distinct().size == players.size &&
            players.any { it.isHost }
}

class NearbyLobbyCoordinator(private val random: SecureRandom = SecureRandom()) {
    fun createHost(name: String): NearbyLobbyState {
        val sessionId = randomHex(12)
        val joinToken = randomHex(24)
        val code = generateRoomCode(joinToken)
        return NearbyLobbyState(
            sessionId = sessionId,
            joinToken = joinToken,
            players = listOf(
                NearbyPlayer(
                    id = "host-$sessionId",
                    name = name.trim().ifBlank { "Host" },
                    isHost = true,
                    color = LudoColor.RED,
                    ready = true,
                    team = null,
                ),
            ),
            roomCode = code,
        )
    }

    fun generateRoomCode(seed: String? = null): String {
        val num = if (seed != null) {
            Math.abs(seed.hashCode() % 10000)
        } else {
            random.nextInt(10000)
        }
        return String.format(java.util.Locale.US, "%04d", num)
    }

    fun join(state: NearbyLobbyState, playerId: String, name: String): NearbyLobbyState {
        if (state.players.any { it.id == playerId } || state.players.size >= 4) return state
        return state.copy(
            players = state.players + NearbyPlayer(
                id = playerId,
                name = name.trim().ifBlank { "Player ${state.players.size + 1}" },
                isHost = false,
                color = null,
                ready = false,
                team = 1,
            ),
        )
    }

    fun addBot(state: NearbyLobbyState, color: LudoColor? = null, team: Int? = null): NearbyLobbyState {
        if (state.players.size >= 4) return state
        val takenColors = state.players.mapNotNull { it.color }.toSet()
        val botColor = color?.takeIf { it !in takenColors }
            ?: LudoColor.entries.firstOrNull { it !in takenColors }
            ?: return state
        val botCount = state.players.count { it.isBot } + 1
        val newBot = NearbyPlayer(
            id = "bot-${state.sessionId}-$botCount",
            name = "Bot $botCount",
            isHost = false,
            color = botColor,
            ready = true,
            team = team ?: 2,
            isBot = true,
        )
        return state.copy(players = state.players + newBot)
    }

    fun removeBot(state: NearbyLobbyState, botId: String): NearbyLobbyState {
        return state.copy(players = state.players.filterNot { it.id == botId && it.isBot })
    }

    fun updateBot(state: NearbyLobbyState, botId: String, color: LudoColor?, team: Int?): NearbyLobbyState {
        val bot = state.players.firstOrNull { it.id == botId && it.isBot } ?: return state
        val takenColors = state.players.filter { it.id != botId }.mapNotNull { it.color }.toSet()
        val newColor = if (color != null && color !in takenColors) color else bot.color
        return state.copy(players = state.players.map {
            if (it.id == botId) it.copy(color = newColor, team = team) else it
        })
    }

    fun updateTeam(state: NearbyLobbyState, playerId: String, team: Int?): NearbyLobbyState {
        return state.copy(players = state.players.map {
            if (it.id == playerId) it.copy(team = team) else it
        })
    }

    fun chooseColor(state: NearbyLobbyState, playerId: String, color: LudoColor): NearbyLobbyState {
        val player = state.players.firstOrNull { it.id == playerId } ?: return state
        if (player.ready && !player.isBot) return state
        if (state.players.any { it.id != playerId && it.color == color }) return state
        return state.copy(players = state.players.map {
            if (it.id == playerId) it.copy(color = color, ready = if (it.isBot) true else false) else it
        })
    }

    fun setReady(state: NearbyLobbyState, playerId: String, ready: Boolean): NearbyLobbyState =
        state.copy(players = state.players.map {
            if (it.id == playerId && it.color != null && !it.isBot) it.copy(ready = ready) else it
        })

    private fun randomHex(bytes: Int): String = ByteArray(bytes)
        .also(random::nextBytes)
        .joinToString("") { "%02x".format(it) }
}
