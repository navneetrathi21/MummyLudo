package com.mummyludo.app.nearby

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mummyludo.app.game.LudoColor

@Composable
fun GuestLobbyScreen(
    lobby: NearbyLobbyState?,
    playerId: String,
    error: String? = null,
    onBack: () -> Unit,
    onProfile: (String, LudoColor?, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    GuestLobbyScreen(
        lobby = lobby,
        playerId = playerId,
        error = error,
        onBack = onBack,
        onProfile = { name, color, ready, _ -> onProfile(name, color, ready) },
        modifier = modifier,
    )
}

@Composable
fun GuestLobbyScreen(
    lobby: NearbyLobbyState?,
    playerId: String,
    error: String? = null,
    onBack: () -> Unit,
    onProfile: (String, LudoColor?, Boolean, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val me = lobby?.players?.firstOrNull { it.id == playerId }
    val isReady = me?.ready == true
    var name by remember(me?.name) { mutableStateOf(me?.name ?: "Player") }
    val selected = me?.color
    val myTeam = me?.team
    val unavailable = lobby?.players.orEmpty().filter { it.id != playerId }.mapNotNull { it.color }.toSet() + lobby?.botColors.orEmpty()

    Column(
        modifier
            .fillMaxSize()
            .background(NearbyCream)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Leave") }
            Text("Game lobby", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = NearbyInk)
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
        }
        if (lobby == null) {
            Spacer(Modifier.height(60.dp))
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            Text("Connecting to host…", Modifier.align(Alignment.CenterHorizontally).padding(12.dp), color = NearbyInk)
            return@Column
        }

        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().testTag("guest_profile_card"),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Your name") },
                    singleLine = true,
                    enabled = !isReady,
                    supportingText = if (isReady) {
                        { Text("Unready to change name or color", color = NearbyInk.copy(.6f), fontSize = 12.sp) }
                    } else null,
                    modifier = Modifier.fillMaxWidth().testTag("guest_name"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NearbyGreen,
                        focusedLabelColor = NearbyGreen,
                    ),
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Choose your color", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = NearbyInk)
                    Text(
                        if (isReady) "Locked (ready)" else (selected?.label ?: "None selected"),
                        color = if (isReady) NearbyGreen else NearbyInk.copy(.58f),
                        fontSize = 13.sp,
                        fontWeight = if (isReady) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    LudoColor.entries.forEach { color ->
                        val isSelectable = !isReady && (color !in unavailable)
                        val isSelected = selected == color
                        Box(
                            Modifier
                                .size(50.dp)
                                .background(
                                    color.uiColor().copy(alpha = if (isSelectable || isSelected) 1f else .25f),
                                    CircleShape,
                                )
                                .clickable(enabled = isSelectable) { onProfile(name, color, false, myTeam) }
                                .testTag("guest_color_${color.name.lowercase()}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Text("✓", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Team", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = NearbyInk.copy(.7f))
                    TeamSelector(
                        selectedTeam = myTeam,
                        enabled = !isReady,
                        onSelectTeam = { onProfile(name, selected, false, it) },
                        tagPrefix = "guest_team",
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Players & Slots", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = NearbyInk)
            Text("${lobby.players.size}/4 Slots", color = NearbyInk.copy(.6f), fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))

        // 4 Slots
        for (slotIndex in 0 until 4) {
            val player = lobby.players.getOrNull(slotIndex)
            if (player != null) {
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        SlotBadge(slotIndex + 1)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(14.dp).background(player.color?.uiColor() ?: NearbyInk.copy(.2f), RoundedCornerShape(7.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (player.isBot) "🤖 ${player.name}" else player.name,
                                    fontWeight = FontWeight.Medium,
                                    color = NearbyInk,
                                )
                                Spacer(Modifier.width(6.dp))
                                if (player.isHost) {
                                    Surface(color = NearbyGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                        Text("HOST", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NearbyGreen, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                } else if (player.isBot) {
                                    Surface(color = Color(0xFF6B7280).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                        Text("BOT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4B5563), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                } else if (player.id == playerId) {
                                    Surface(color = Color(0xFF2563EB).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                        Text("YOU", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                TeamBadge(player.team)
                            }
                            Text(player.color?.label ?: "Choosing color", color = NearbyInk.copy(.58f), fontSize = 13.sp)
                        }
                        Text(
                            if (player.ready) "Ready" else "Waiting",
                            color = if (player.ready) NearbyGreen else NearbyInk.copy(.45f),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            } else {
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        SlotBadge(slotIndex + 1, active = false)
                        Spacer(Modifier.width(10.dp))
                        Text("Slot ${slotIndex + 1} · Open", color = NearbyInk.copy(alpha = 0.5f), fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // Match summary
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("MATCH SETUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NearbyInk.copy(alpha = 0.5f), letterSpacing = 1.sp)
                Spacer(Modifier.height(2.dp))
                Text(describeMatchTeams(lobby.players), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NearbyInk)
            }
        }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onProfile(name, selected, !isReady, myTeam) },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().height(58.dp).testTag("guest_ready"),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isReady) NearbyInk.copy(.7f) else NearbyGreen,
            ),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(if (isReady) "Not ready" else "I'm ready", fontSize = 17.sp)
        }
    }
}

private fun LudoColor.uiColor(): Color = when (this) {
    LudoColor.RED -> Color(0xFFEF5B67)
    LudoColor.GREEN -> Color(0xFF49B675)
    LudoColor.YELLOW -> Color(0xFFF6CB3D)
    LudoColor.BLUE -> Color(0xFF438FE7)
}
