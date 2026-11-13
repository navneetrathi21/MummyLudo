package com.mummyludo.app.nearby

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.mummyludo.app.game.LudoColor
import kotlinx.coroutines.launch

internal val NearbyInk = Color(0xFF20312B)
internal val NearbyGreen = Color(0xFF3F725F)
internal val NearbyCream = Color(0xFFF8F4EA)

@Composable
fun NearbyEntryScreen(onBack: () -> Unit, onHost: () -> Unit, onJoin: () -> Unit) {
    Column(Modifier.fillMaxSize().background(NearbyCream).padding(24.dp)) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Spacer(Modifier.height(20.dp))
        Text("Play nearby", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = NearbyInk)
        Text("Play together on phones connected to the same Wi-Fi.", color = NearbyInk.copy(.65f))
        Spacer(Modifier.height(36.dp))
        Button(onClick = onHost, modifier = Modifier.fillMaxWidth().height(58.dp).testTag("nearby_host"), colors = ButtonDefaults.buttonColors(containerColor = NearbyGreen), shape = RoundedCornerShape(18.dp)) {
            Text("Host game", fontSize = 18.sp)
        }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onJoin, modifier = Modifier.fillMaxWidth().height(58.dp).testTag("nearby_join"), shape = RoundedCornerShape(18.dp)) {
            Text("Join game", fontSize = 18.sp)
        }
    }
}

@Composable
fun HostLobbyScreen(
    lobby: NearbyLobbyState,
    hostAddress: String?,
    wifiName: String? = null,
    hasLocationPermission: Boolean = true,
    onBack: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenAppSettings: () -> Unit = onOpenWifiSettings,
    onProfile: (String, LudoColor?, Boolean) -> Unit = { _, _, _ -> },
    onStart: () -> Unit,
) {
    HostLobbyScreen(
        lobby = lobby,
        hostAddress = hostAddress,
        wifiName = wifiName,
        hasLocationPermission = hasLocationPermission,
        onBack = onBack,
        onOpenWifiSettings = onOpenWifiSettings,
        onOpenAppSettings = onOpenAppSettings,
        onProfile = { name, color, ready, _ -> onProfile(name, color, ready) },
        onAddBot = {},
        onRemoveBot = {},
        onUpdateBot = { _, _, _ -> },
        onStart = onStart,
    )
}

@Composable
fun HostLobbyScreen(
    lobby: NearbyLobbyState,
    hostAddress: String?,
    wifiName: String? = null,
    hasLocationPermission: Boolean = true,
    onBack: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenAppSettings: () -> Unit = onOpenWifiSettings,
    onProfile: (String, LudoColor?, Boolean, Int?) -> Unit,
    onAddBot: () -> Unit = {},
    onRemoveBot: (String) -> Unit = {},
    onUpdateBot: (String, LudoColor?, Int?) -> Unit = { _, _, _ -> },
    onStart: () -> Unit,
) {
    val joinCode = hostAddress?.let { JoinPayloadCodec.encode(JoinPayload(lobby.roomCode, it, 42871, lobby.joinToken)) }
    val hostPlayer = lobby.players.firstOrNull { it.isHost }
    val unavailableColors = lobby.players.filterNot { it.isHost }.mapNotNull { it.color }.toSet() + lobby.botColors

    Column(
        Modifier
            .fillMaxSize()
            .background(NearbyCream)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Close") }
            Text("Host lobby", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = NearbyInk)
        }
        Spacer(Modifier.height(10.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(wifiName ?: "Same Wi-Fi Network", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.testTag("host_wifi_name"))
                if (wifiName != null) {
                    Text(
                        "All players must join this Wi-Fi",
                        color = NearbyInk.copy(.58f),
                        fontSize = 13.sp,
                    )
                } else if (!hasLocationPermission) {
                    Text(
                        "Location permission is needed to show the Wi-Fi name.",
                        color = NearbyInk.copy(.58f),
                        fontSize = 13.sp,
                        modifier = Modifier.testTag("host_location_permission_notice"),
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onOpenAppSettings,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("host_enable_location_button"),
                    ) {
                        Text("Enable location in Settings", fontSize = 13.sp)
                    }
                } else {
                    Text(
                        "All players must join the same Wi-Fi or hotspot",
                        color = NearbyInk.copy(.58f),
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (joinCode != null) {
                    QrCode(joinCode, Modifier.size(175.dp).testTag("host_game_qr"))
                    Spacer(Modifier.height(8.dp))
                    Text("Game Code", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = NearbyInk.copy(.6f))
                    Text(
                        lobby.roomCode,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp,
                        color = NearbyInk,
                        modifier = Modifier.testTag("host_room_code"),
                    )
                    Text("Ask players to scan QR or enter 4-digit code", color = NearbyInk.copy(.65f), fontSize = 13.sp)
                }
                TextButton(onClick = onOpenWifiSettings) { Text("Share or change Wi-Fi") }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Players & Slots", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = NearbyInk)
            Text("${lobby.players.size}/4 Slots", color = NearbyInk.copy(.6f), fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))

        // 4 Dedicated AOE2-Style Slots
        for (slotIndex in 0 until 4) {
            val player = lobby.players.getOrNull(slotIndex)
            if (player != null) {
                if (player.isHost) {
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("host_profile_card"),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SlotBadge(slotIndex + 1)
                                    Spacer(Modifier.width(8.dp))
                                    Box(Modifier.size(14.dp).background(player.color.colorOrMuted(), RoundedCornerShape(7.dp)))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(player.name, fontWeight = FontWeight.Bold, color = NearbyInk)
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                color = NearbyGreen.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp),
                                            ) {
                                                Text("HOST", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NearbyGreen, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                            }
                                        }
                                        Text(player.color?.label ?: "Choosing color", color = NearbyInk.copy(.58f), fontSize = 12.sp)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onProfile(player.name, player.color, !player.ready, player.team) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (player.ready) NearbyGreen else NearbyInk.copy(.6f),
                                    ),
                                    modifier = Modifier.testTag("host_ready_button"),
                                ) {
                                    Text(if (player.ready) "✓ Ready" else "Not ready", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Color", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = NearbyInk.copy(.7f))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LudoColor.entries.forEach { color ->
                                        val enabled = !player.ready && (color !in unavailableColors)
                                        val isSelected = player.color == color
                                        Box(
                                            Modifier
                                                .size(34.dp)
                                                .background(
                                                    color.colorOrMuted().copy(alpha = if (enabled || isSelected) 1f else .25f),
                                                    CircleShape,
                                                )
                                                .clickable(enabled = enabled) { onProfile(player.name, color, player.ready, player.team) }
                                                .testTag("host_color_${color.name.lowercase()}"),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (isSelected) {
                                                Text("✓", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Team", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = NearbyInk.copy(.7f))
                                TeamSelector(
                                    selectedTeam = player.team,
                                    enabled = !player.ready,
                                    onSelectTeam = { onProfile(player.name, player.color, player.ready, it) },
                                    tagPrefix = "host_team",
                                )
                            }
                        }
                    }
                } else if (player.isBot) {
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("slot_bot_${slotIndex + 1}"),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SlotBadge(slotIndex + 1)
                                    Spacer(Modifier.width(8.dp))
                                    Box(Modifier.size(14.dp).background(player.color.colorOrMuted(), RoundedCornerShape(7.dp)))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("🤖 ${player.name}", fontWeight = FontWeight.SemiBold, color = NearbyInk)
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFF6B7280).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp),
                                            ) {
                                                Text("BOT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4B5563), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                            }
                                        }
                                        Text(player.color?.label ?: "Choosing color", color = NearbyInk.copy(.58f), fontSize = 12.sp)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onRemoveBot(player.id) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                    modifier = Modifier.testTag("remove_bot_${player.id}"),
                                ) {
                                    Text("✕ Remove", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            val botUnavailable = lobby.players.filterNot { it.id == player.id }.mapNotNull { it.color }.toSet()
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Color", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = NearbyInk.copy(.7f))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LudoColor.entries.forEach { color ->
                                        val enabled = color !in botUnavailable
                                        val isSelected = player.color == color
                                        Box(
                                            Modifier
                                                .size(30.dp)
                                                .background(
                                                    color.colorOrMuted().copy(alpha = if (enabled || isSelected) 1f else .25f),
                                                    CircleShape,
                                                )
                                                .clickable(enabled = enabled) { onUpdateBot(player.id, color, player.team) }
                                                .testTag("bot_${player.id}_color_${color.name.lowercase()}"),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (isSelected) {
                                                Text("✓", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Team", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = NearbyInk.copy(.7f))
                                TeamSelector(
                                    selectedTeam = player.team,
                                    enabled = true,
                                    onSelectTeam = { onUpdateBot(player.id, player.color, it) },
                                    tagPrefix = "bot_${player.id}_team",
                                )
                            }
                        }
                    }
                } else {
                    // Human guest
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("slot_guest_${slotIndex + 1}"),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            SlotBadge(slotIndex + 1)
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.size(14.dp).background(player.color.colorOrMuted(), RoundedCornerShape(7.dp)))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(player.name, fontWeight = FontWeight.Medium, color = NearbyInk)
                                    Spacer(Modifier.width(6.dp))
                                    TeamBadge(player.team)
                                }
                                Text(player.color?.label ?: "Choosing color", color = NearbyInk.copy(.58f), fontSize = 12.sp)
                            }
                            Text(
                                if (player.ready) "Ready" else "Waiting",
                                color = if (player.ready) NearbyGreen else NearbyInk.copy(.45f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            } else {
                // Empty / Open Slot
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("slot_open_${slotIndex + 1}"),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SlotBadge(slotIndex + 1, active = false)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Slot ${slotIndex + 1} · Open",
                                color = NearbyInk.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                            )
                        }
                        OutlinedButton(
                            onClick = onAddBot,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("add_bot_slot_${slotIndex + 1}"),
                        ) {
                            Text("+ Add Bot", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        // Match Summary Card
        Card(
            Modifier.fillMaxWidth().testTag("match_summary_card"),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("MATCH SETUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NearbyInk.copy(alpha = 0.5f), letterSpacing = 1.sp)
                Spacer(Modifier.height(2.dp))
                Text(describeMatchTeams(lobby.players), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NearbyInk)
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onStart,
            enabled = lobby.canStart,
            modifier = Modifier.fillMaxWidth().height(58.dp).testTag("host_start_game"),
            colors = ButtonDefaults.buttonColors(containerColor = NearbyGreen),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(if (lobby.canStart) "Start game" else "Waiting for players", fontSize = 17.sp)
        }
    }
}

@Composable
fun JoinNearbyScreen(
    initialCode: String,
    onBack: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onScan: () -> Unit,
    onJoin: (JoinPayload) -> Unit,
    onResolveManualCode: (suspend (String) -> JoinPayload?)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val parsed = remember(initialCode) { JoinPayloadCodec.decode(initialCode) }
    var manualCode by remember(parsed) {
        mutableStateOf(parsed?.sessionId?.take(4) ?: "")
    }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(NearbyCream).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Join nearby", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))
        Text("Enter 4-digit game code", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("Both phones must be connected to the same Wi-Fi.", color = NearbyInk.copy(.62f), fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = manualCode,
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }.take(4)
                manualCode = digits
                searchError = null
            },
            label = { Text("Game code") },
            placeholder = { Text("e.g. 4821") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().testTag("nearby_manual_code_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NearbyGreen,
                focusedLabelColor = NearbyGreen,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                if (parsed != null && parsed.sessionId.take(4) == manualCode) {
                    onJoin(parsed)
                } else if (manualCode.length == 4) {
                    searchError = null
                    isSearching = true
                    coroutineScope.launch {
                        val resolver = onResolveManualCode ?: { code -> discoverHostByRoomCode(code) }
                        val resolved = resolver(manualCode)
                        isSearching = false
                        if (resolved != null) {
                            onJoin(resolved)
                        } else {
                            searchError = "No game found with code $manualCode on this Wi-Fi"
                        }
                    }
                }
            },
            enabled = manualCode.length == 4 && !isSearching,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("nearby_join_manual_button"),
            colors = ButtonDefaults.buttonColors(containerColor = NearbyGreen),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(if (isSearching) "Searching for game…" else "Join with code", fontSize = 17.sp)
        }

        if (searchError != null) {
            Spacer(Modifier.height(8.dp))
            Text(searchError!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.testTag("nearby_search_error"))
        }

        Spacer(Modifier.height(20.dp))
        Text("— OR —", color = NearbyInk.copy(.4f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onScan,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("nearby_scan_camera"),
            colors = ButtonDefaults.buttonColors(containerColor = NearbyGreen),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Scan with camera", fontSize = 17.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenWifiSettings, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Text("Open Wi-Fi settings")
        }

        if (parsed != null) {
            Spacer(Modifier.height(20.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Game found", color = NearbyGreen, fontWeight = FontWeight.SemiBold)
                    Text("Code: ${parsed.sessionId}", color = NearbyInk.copy(.6f), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onJoin(parsed) }, modifier = Modifier.fillMaxWidth().testTag("nearby_join_confirm"), colors = ButtonDefaults.buttonColors(containerColor = NearbyGreen)) {
                        Text("Join game")
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCode(content: String, modifier: Modifier = Modifier) {
    val image = remember(content) {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512)
        Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until 512) for (x in 0 until 512) setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }.asImageBitmap()
    }
    Image(image, contentDescription = "Game join QR code", modifier = modifier)
}

internal fun com.mummyludo.app.game.LudoColor?.colorOrMuted(): Color = when (this) {
    com.mummyludo.app.game.LudoColor.RED -> Color(0xFFEF5B67)
    com.mummyludo.app.game.LudoColor.GREEN -> Color(0xFF49B675)
    com.mummyludo.app.game.LudoColor.YELLOW -> Color(0xFFF6CB3D)
    com.mummyludo.app.game.LudoColor.BLUE -> Color(0xFF438FE7)
    null -> Color(0xFFD9DDD8)
}

internal fun teamColor(team: Int?): Color = when (team) {
    1 -> Color(0xFF2563EB)
    2 -> Color(0xFFEA580C)
    3 -> Color(0xFF7C3AED)
    4 -> Color(0xFF0D9488)
    else -> Color(0xFF6B7280)
}

internal fun describeMatchTeams(players: List<NearbyPlayer>): String {
    val teams = players.mapNotNull { it.team }.distinct().sorted()
    return when {
        teams.isEmpty() -> "Individual (Free-For-All)"
        teams.size == 1 -> "Team ${teams[0]} vs Solo"
        else -> teams.joinToString(" vs ") { "Team $it" }
    }
}

@Composable
internal fun SlotBadge(number: Int, active: Boolean = true, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = if (active) NearbyInk.copy(alpha = 0.08f) else NearbyInk.copy(alpha = 0.04f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = "#$number",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) NearbyInk else NearbyInk.copy(alpha = 0.4f),
        )
    }
}

@Composable
internal fun TeamBadge(team: Int?, modifier: Modifier = Modifier) {
    val label = if (team == null) "FFA" else "Team $team"
    val tColor = teamColor(team)
    Surface(
        modifier = modifier,
        color = tColor.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = tColor,
        )
    }
}

@Composable
internal fun TeamSelector(
    selectedTeam: Int?,
    enabled: Boolean = true,
    onSelectTeam: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "team",
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val options = listOf<Int?>(null, 1, 2, 3, 4)
        options.forEach { t ->
            val isSelected = selectedTeam == t
            val label = if (t == null) "None" else "T$t"
            val tColor = teamColor(t)
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = enabled) { onSelectTeam(t) }
                    .testTag("${tagPrefix}_${t ?: "none"}"),
                color = if (isSelected) tColor else Color(0xFFF0EFEB),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else NearbyInk.copy(alpha = 0.8f),
                )
            }
        }
    }
}
