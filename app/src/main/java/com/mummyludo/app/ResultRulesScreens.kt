package com.mummyludo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mummyludo.app.game.*
import com.mummyludo.app.nearby.*

@Composable
internal fun ResultScreen(state: GameState, onAgain: () -> Unit, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(50.dp))
        Text("Game complete", fontSize = 18.sp, color = Ink.copy(.62f))
        val winnerSeat = state.seats.firstOrNull { it.color == state.ranking.firstOrNull() }
        val winnerText = if (winnerSeat?.team != null) "Team ${winnerSeat.team} wins!" else "${state.ranking.first().label} wins!"
        Text(winnerText, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("result_winner"))
        Spacer(Modifier.height(30.dp))
        state.ranking.forEachIndexed { index, color ->
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(45.dp))
                    Box(Modifier.size(18.dp).clip(CircleShape).background(colorOf(color)))
                    Spacer(Modifier.width(12.dp)); Text(color.label, fontSize = 18.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onAgain, Modifier.fillMaxWidth().height(56.dp).testTag("result_play_again"), shape = RoundedCornerShape(18.dp)) { Text("Play again") }
        TextButton(onClick = onHome) { Text("Home") }
    }
}
