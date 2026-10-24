package com.mummyludo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
internal fun HomeScreen(
    hasSavedGame: Boolean = false,
    onContinueGame: () -> Unit = {},
    onNewGame: () -> Unit,
    onNearby: () -> Unit,
    onRules: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.35f))
        MiniLogo()
        Spacer(Modifier.height(22.dp))
        Text("Mummy Ludo", fontSize = 36.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text("A fair game for everyone", color = Ink.copy(alpha = .62f), fontSize = 16.sp)
        Spacer(Modifier.weight(0.65f))
        if (hasSavedGame) {
            Button(
                onClick = onContinueGame,
                modifier = Modifier.fillMaxWidth().height(58.dp).testTag("home_continue_game"),
                shape = RoundedCornerShape(18.dp),
            ) { Text("Continue game", fontSize = 18.sp) }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onNewGame,
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("home_new_game"),
                shape = RoundedCornerShape(18.dp),
            ) { Text("New game") }
        } else {
            Button(
                onClick = onNewGame,
                modifier = Modifier.fillMaxWidth().height(58.dp).testTag("home_new_game"),
                shape = RoundedCornerShape(18.dp),
            ) { Text("New game", fontSize = 18.sp) }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onNearby, modifier = Modifier.fillMaxWidth().height(54.dp).testTag("home_play_nearby"), shape = RoundedCornerShape(18.dp)) {
            Text("Play nearby")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRules, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
            Text("How to play")
        }
    }
}

@Composable
private fun MiniLogo() {
    Box(Modifier.size(110.dp).clip(RoundedCornerShape(28.dp)).background(Color.White)) {
        Box(Modifier.size(44.dp).align(Alignment.TopStart).background(Red))
        Box(Modifier.size(44.dp).align(Alignment.TopEnd).background(Green))
        Box(Modifier.size(44.dp).align(Alignment.BottomEnd).background(Yellow))
        Box(Modifier.size(44.dp).align(Alignment.BottomStart).background(Blue))
        Box(Modifier.size(30.dp).align(Alignment.Center).clip(CircleShape).background(Cream))
    }
}

@Composable
internal fun SetupScreen(
    humanCount: Int,
    botCount: Int,
    reverseDuel: Boolean,
    onHumans: (Int) -> Unit,
    onBots: (Int) -> Unit,
    onReverse: () -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Match setup", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(28.dp))
        CountSelector("Human players", humanCount, 1..3, onHumans, "humans")
        Spacer(Modifier.height(20.dp))
        CountSelector("Bots", botCount, 1..3, onBots, "bots")
        Spacer(Modifier.height(22.dp))
        if (humanCount + botCount == 2) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onReverse).padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Opposite colors", fontWeight = FontWeight.Medium)
                        Text(if (reverseDuel) "Player Yellow · Bot Red" else "Player Red · Bot Yellow", color = Ink.copy(.65f))
                    }
                    Text("Swap", color = Sage)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("${humanCount + botCount} active colors · ${humanCount} human · ${botCount} bot", color = Ink.copy(.65f))
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStart,
            enabled = humanCount + botCount in 2..4,
            modifier = Modifier.fillMaxWidth().height(58.dp).testTag("setup_start"),
            shape = RoundedCornerShape(18.dp),
        ) { Text("Start game", fontSize = 18.sp) }
    }
}

@Composable
private fun CountSelector(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit, tag: String) {
    Text(label, fontWeight = FontWeight.Medium, fontSize = 17.sp)
    Spacer(Modifier.height(9.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        range.forEach { count ->
            val selected = count == value
            Surface(
                modifier = Modifier.weight(1f).height(50.dp).clickable { onChange(count) }.testTag("setup_${tag}_$count"),
                color = if (selected) Sage else Color.White,
                contentColor = if (selected) Color.White else Ink,
                shape = RoundedCornerShape(15.dp),
            ) { Box(contentAlignment = Alignment.Center) { Text(count.toString(), fontWeight = FontWeight.SemiBold) } }
        }
    }
}
