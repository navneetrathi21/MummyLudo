package com.mummyludo.app.nearby

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private val NearbyInk = Color(0xFF20312B)
private val NearbyGreen = Color(0xFF3F725F)
private val NearbyCream = Color(0xFFF8F4EA)

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
    wifiName: String?,
    onBack: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onStart: () -> Unit,
) {
    val joinCode = hostAddress?.let { JoinPayloadCodec.encode(JoinPayload(lobby.sessionId, it, 42871, lobby.joinToken)) }
    Column(Modifier.fillMaxSize().background(NearbyCream).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Close") }
            Text("Host lobby", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = NearbyInk)
        }
        Spacer(Modifier.height(10.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(wifiName ?: "Connected Wi-Fi", fontWeight = FontWeight.SemiBold)
                Text(
                    if (wifiName == null) "Wi-Fi name is hidden by Android" else "All players must join this Wi-Fi",
                    color = NearbyInk.copy(.58f),
                )
                Spacer(Modifier.height(10.dp))
                if (joinCode != null) {
                    QrCode(joinCode, Modifier.size(190.dp).testTag("host_game_qr"))
                    Text("Ask players to scan this in Mummy Ludo", color = NearbyInk.copy(.65f))
                }
                TextButton(onClick = onOpenWifiSettings) { Text("Share or change Wi-Fi") }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Players", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        lobby.players.forEach { player ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).background(player.color.colorOrMuted(), RoundedCornerShape(7.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(player.name, fontWeight = FontWeight.Medium)
                        Text(player.color?.label ?: "Choosing color", color = NearbyInk.copy(.58f))
                    }
                    Text(if (player.ready) "Ready" else "Waiting", color = if (player.ready) NearbyGreen else NearbyInk.copy(.45f))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onStart, enabled = lobby.canStart, modifier = Modifier.fillMaxWidth().height(58.dp).testTag("host_start_game"), colors = ButtonDefaults.buttonColors(containerColor = NearbyGreen), shape = RoundedCornerShape(18.dp)) {
            Text(if (lobby.canStart) "Start game" else "Waiting for players", fontSize = 17.sp)
        }
    }
}

@Composable
fun JoinNearbyScreen(initialCode: String, onBack: () -> Unit, onOpenWifiSettings: () -> Unit, onScan: () -> Unit, onJoin: (JoinPayload) -> Unit) {
    val parsed = remember(initialCode) { JoinPayloadCodec.decode(initialCode) }
    Column(Modifier.fillMaxSize().background(NearbyCream).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Join nearby", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(48.dp))
        Text("Scan the host's game QR", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("Both phones must be connected to the same Wi-Fi.", color = NearbyInk.copy(.62f))
        Spacer(Modifier.height(28.dp))
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(58.dp).testTag("nearby_scan_camera"), colors = ButtonDefaults.buttonColors(containerColor = NearbyGreen), shape = RoundedCornerShape(18.dp)) {
            Text("Scan with camera", fontSize = 18.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenWifiSettings, modifier = Modifier.fillMaxWidth()) { Text("Open Wi-Fi settings") }
        if (parsed != null) {
            Spacer(Modifier.height(28.dp))
            Text("Game found", color = NearbyGreen, fontWeight = FontWeight.SemiBold)
            Button(onClick = { onJoin(parsed) }, modifier = Modifier.fillMaxWidth().testTag("nearby_join_confirm")) { Text("Join game") }
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

private fun com.mummyludo.app.game.LudoColor?.colorOrMuted(): Color = when (this) {
    com.mummyludo.app.game.LudoColor.RED -> Color(0xFFEF5B67)
    com.mummyludo.app.game.LudoColor.GREEN -> Color(0xFF49B675)
    com.mummyludo.app.game.LudoColor.YELLOW -> Color(0xFFF6CB3D)
    com.mummyludo.app.game.LudoColor.BLUE -> Color(0xFF438FE7)
    null -> Color(0xFFD9DDD8)
}
