package com.mummyludo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun WelcomeScreen(
    initialName: String = "",
    onContinue: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val isValid = name.trim().isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 36.dp)
            .testTag("welcome_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.25f))
        WelcomeLogo()
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Welcome to Mummy Ludo",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "A fair, tactical board game for everyone.\nPlease enter your name to get started.",
            fontSize = 15.sp,
            color = Ink.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.height(36.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(20) },
            label = { Text("Your Name") },
            placeholder = { Text("e.g. Cleo, Alex, Ramses") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (isValid) onContinue(name.trim())
                },
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("welcome_name_input"),
        )

        Spacer(Modifier.weight(0.75f))

        Button(
            onClick = { onContinue(name.trim()) },
            enabled = isValid,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .testTag("welcome_continue_button"),
        ) {
            Text("Continue", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WelcomeLogo() {
    Box(Modifier.size(110.dp).clip(RoundedCornerShape(28.dp)).background(Color.White)) {
        Box(Modifier.size(44.dp).align(Alignment.TopStart).background(Red))
        Box(Modifier.size(44.dp).align(Alignment.TopEnd).background(Green))
        Box(Modifier.size(44.dp).align(Alignment.BottomEnd).background(Yellow))
        Box(Modifier.size(44.dp).align(Alignment.BottomStart).background(Blue))
        Box(Modifier.size(30.dp).align(Alignment.Center).clip(CircleShape).background(Cream))
    }
}
