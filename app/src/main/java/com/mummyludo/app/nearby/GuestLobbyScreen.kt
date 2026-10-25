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

private val NearbyInk = Color(0xFF20312B)
private val NearbyGreen = Color(0xFF3F725F)
private val NearbyCream = Color(0xFFF8F4EA)

@Composable
fun GuestLobbyScreen(
    lobby: NearbyLobbyState?,
    playerId: String,
    error: String? = null,
    onBack: () -> Unit,
    onProfile: (String, LudoColor?, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val me = lobby?.players?.firstOrNull { it.id == playerId }
    var name by remember(me?.name) { mutableStateOf(me?.name ?: "Player") }
    val selected = me?.color
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
                        selected?.label ?: "None selected",
                        color = NearbyInk.copy(.58f),
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    LudoColor.entries.forEach { color ->
                        val enabled = color !in unavailable
                        Box(
                            Modifier
                                .size(50.dp)
                                .background(color.uiColor().copy(alpha = if (enabled) 1f else .25f), CircleShape)
                                .clickable(enabled = enabled) { onProfile(name, color, false) }
                                .testTag("guest_color_${color.name.lowercase()}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected == color) {
                                Text("✓", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Players", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = NearbyInk)
        lobby.players.forEach { player ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).background(player.color?.uiColor() ?: NearbyInk.copy(.2f), RoundedCornerShape(7.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            player.name,
                            fontWeight = FontWeight.Medium,
                            color = NearbyInk,
                        )
                        Text(player.color?.label ?: "Choosing color", color = NearbyInk.copy(.58f), fontSize = 13.sp)
                    }
                    Text(
                        if (player.ready) "Ready" else "Waiting",
                        color = if (player.ready) NearbyGreen else NearbyInk.copy(.45f),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onProfile(name, selected, !(me?.ready ?: false)) },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().height(58.dp).testTag("guest_ready"),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (me?.ready == true) NearbyInk.copy(.7f) else NearbyGreen,
            ),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(if (me?.ready == true) "Not ready" else "I'm ready", fontSize = 17.sp)
        }
    }
}

private fun LudoColor.uiColor(): Color = when (this) {
    LudoColor.RED -> Color(0xFFEF5B67)
    LudoColor.GREEN -> Color(0xFF49B675)
    LudoColor.YELLOW -> Color(0xFFF6CB3D)
    LudoColor.BLUE -> Color(0xFF438FE7)
}
