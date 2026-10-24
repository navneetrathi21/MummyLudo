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
        Text("${state.ranking.first().label} wins!", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("result_winner"))
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

@Composable
internal fun RulesScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text("How to play", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        val rules = listOf(
            "Roll a 6 to enter a token. Three consecutive 6s are discarded, then the same player rolls again.",
            "Land on an arrow tail to move to its head and earn an extra roll.",
            "Capturing earns one extra roll. An arrow capture therefore earns two.",
            "Two same-color tokens can move as a double on even rolls, travelling half the die value.",
            "A single cannot capture a double. A double captures two from a triple and all four from a four-token stack.",
            "Safe squares cannot be captured. Exact movement is required to finish.",
        )
        rules.forEachIndexed { index, rule ->
            Row(Modifier.padding(vertical = 11.dp)) {
                Surface(shape = CircleShape, color = Sage, modifier = Modifier.size(28.dp)) { Box(contentAlignment = Alignment.Center) { Text("${index + 1}", color = Color.White) } }
                Spacer(Modifier.width(12.dp)); Text(rule, fontSize = 16.sp, lineHeight = 23.sp)
            }
        }
    }
}
