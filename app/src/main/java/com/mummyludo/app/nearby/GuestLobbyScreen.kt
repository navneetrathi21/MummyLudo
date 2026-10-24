package com.mummyludo.app.nearby

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val me = lobby?.players?.firstOrNull { it.id == playerId }
    var name by remember(me?.name) { mutableStateOf(me?.name ?: "Player") }
    val selected = me?.color
    val unavailable = lobby?.players.orEmpty().filter { it.id != playerId }.mapNotNull { it.color }.toSet() + lobby?.botColors.orEmpty()
    Column(modifier.fillMaxSize().background(Color(0xFFF8F4EA)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Leave") }
            Text("Game lobby", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
        if (lobby == null) {
            Spacer(Modifier.weight(1f))
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            Text("Connecting to host…", Modifier.align(Alignment.CenterHorizontally).padding(12.dp))
            Spacer(Modifier.weight(1f))
            return@Column
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("guest_name"))
        Spacer(Modifier.height(22.dp))
        Text("Choose your color", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            LudoColor.entries.forEach { color ->
                val enabled = color !in unavailable
                Box(
                    Modifier.size(52.dp).background(color.uiColor().copy(alpha = if (enabled) 1f else .2f), CircleShape)
                        .clickable(enabled = enabled) { onProfile(name, color, false) }
                        .testTag("guest_color_${color.name.lowercase()}"),
                    contentAlignment = Alignment.Center,
                ) { if (selected == color) Text("✓", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Text("Players", fontWeight = FontWeight.SemiBold)
        lobby.players.forEach { player ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(player.color?.uiColor() ?: Color.LightGray, CircleShape))
                Text(player.name, Modifier.padding(start = 10.dp).weight(1f))
                Text(if (player.ready) "Ready" else "Waiting", color = Color(0xFF3F725F))
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onProfile(name, selected, !(me?.ready ?: false)) },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().height(58.dp).testTag("guest_ready"),
            shape = RoundedCornerShape(18.dp),
        ) { Text(if (me?.ready == true) "Not ready" else "I'm ready", fontSize = 17.sp) }
    }
}

private fun LudoColor.uiColor(): Color = when (this) {
    LudoColor.RED -> Color(0xFFEF5B67)
    LudoColor.GREEN -> Color(0xFF49B675)
    LudoColor.YELLOW -> Color(0xFFF6CB3D)
    LudoColor.BLUE -> Color(0xFF438FE7)
}
