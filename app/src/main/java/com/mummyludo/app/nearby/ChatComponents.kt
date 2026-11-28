package com.mummyludo.app.nearby

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun chatColorOf(color: LudoColor): Color = when (color) {
    LudoColor.RED -> Color(0xFFEF5B67)
    LudoColor.GREEN -> Color(0xFF49B675)
    LudoColor.YELLOW -> Color(0xFFF6CB3D)
    LudoColor.BLUE -> Color(0xFF438FE7)
}

val QuickChatReactions = listOf(
    "🎲 Roll a 6!",
    "😂 Haha",
    "GG 👏",
    "Oops! 🙈",
    "🔥 Nice",
    "Good luck! 🍀",
)

@Composable
fun ChatButtonWithBadge(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .height(36.dp)
                .testTag("chat_toggle_button"),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF20312B),
            ),
        ) {
            Text("💬 Chat", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .offset(x = 4.dp, y = (-4).dp)
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .background(Color(0xFFEF5B67), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("chat_unread_badge"),
                )
            }
        }
    }
}

@Composable
fun FloatingChatToast(
    message: NearbyChatMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (message == null) return

    LaunchedEffect(message.id) {
        delay(3500)
        onDismiss()
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .testTag("floating_chat_toast"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(chatColorOf(message.color), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = message.senderName.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = message.senderName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF20312B),
                            )
                            if (message.team != null) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "T${message.team}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = chatColorOf(message.color),
                                )
                            }
                            if (message.isTeamOnly) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "[Team]",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2563EB),
                                )
                            }
                        }
                        Text(
                            text = message.text,
                            fontSize = 13.sp,
                            color = Color(0xFF20312B),
                            maxLines = 2,
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("floating_chat_dismiss"),
                ) {
                    Text("✕", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ChatBottomSheet(
    messages: List<NearbyChatMessage>,
    localPlayerTeam: Int?,
    onDismiss: () -> Unit,
    onSend: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isTeamTab by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val filteredMessages = if (isTeamTab && localPlayerTeam != null) {
        messages.filter { it.isTeamOnly && it.team == localPlayerTeam }
    } else {
        messages.filter { !it.isTeamOnly || it.team == localPlayerTeam }
    }

    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty()) {
            listState.animateScrollToItem(filteredMessages.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss)
            .navigationBarsPadding()
            .imePadding()
            .testTag("chat_bottom_sheet"),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .clickable(enabled = false) {}, // Prevent dismiss when tapping inside card
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F4EA)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 10.dp, bottom = 12.dp),
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Column(modifier = Modifier.align(Alignment.CenterStart)) {
                        Text(
                            text = "Room Chat",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF20312B),
                        )
                        Text(
                            text = "Local Wi-Fi LAN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF3F725F),
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .testTag("chat_close_button"),
                    ) {
                        Text("✕", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF20312B))
                    }
                }

                // Channel Tabs (All vs Team) if localPlayer has a team
                if (localPlayerTeam != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = !isTeamTab,
                            onClick = { isTeamTab = false },
                            label = { Text("All Players") },
                            modifier = Modifier.testTag("chat_tab_all"),
                        )
                        FilterChip(
                            selected = isTeamTab,
                            onClick = { isTeamTab = true },
                            label = { Text("Team $localPlayerTeam Only 🛡️") },
                            modifier = Modifier.testTag("chat_tab_team"),
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF20312B).copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

                // Messages List
                if (filteredMessages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("chat_message_list"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isTeamTab) "No team messages yet." else "No messages yet. Say hello!",
                            fontSize = 13.sp,
                            color = Color(0xFF20312B).copy(alpha = 0.5f),
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("chat_message_list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredMessages, key = { it.id }) { msg ->
                            ChatMessageBubble(msg = msg)
                        }
                    }
                }

                // Quick Reaction Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    QuickChatReactions.forEachIndexed { index, reaction ->
                        Surface(
                            modifier = Modifier
                                .clickable { onSend(reaction, isTeamTab) }
                                .testTag("chat_quick_reaction_$index"),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            shadowElevation = 1.dp,
                        ) {
                            Text(
                                text = reaction,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF20312B),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                // Text Input Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it.take(120) },
                        placeholder = { Text(if (isTeamTab) "Message Team $localPlayerTeam..." else "Type a message...", fontSize = 13.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        shape = RoundedCornerShape(20.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                        ),
                    )
                    Button(
                        onClick = {
                            val trimmed = inputText.trim()
                            if (trimmed.isNotBlank()) {
                                onSend(trimmed, isTeamTab)
                                inputText = ""
                            }
                        },
                        modifier = Modifier.testTag("chat_send_button"),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F725F)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text("Send", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(msg: NearbyChatMessage, modifier: Modifier = Modifier) {
    val timeFormatted = remember(msg.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
    }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(chatColorOf(msg.color), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = msg.senderName.take(1).uppercase(),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = msg.senderName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF20312B),
                )
                if (msg.team != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Team ${msg.team}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = chatColorOf(msg.color),
                    )
                }
                if (msg.isTeamOnly) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "[Team]",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = timeFormatted,
                    fontSize = 10.sp,
                    color = Color(0xFF20312B).copy(alpha = 0.4f),
                )
            }
            Spacer(Modifier.height(2.dp))
            Surface(
                shape = RoundedCornerShape(topStart = 2.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                color = Color.White,
                shadowElevation = 1.dp,
            ) {
                Text(
                    text = msg.text,
                    fontSize = 13.sp,
                    color = Color(0xFF20312B),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
fun LobbyChatSection(
    messages: List<NearbyChatMessage>,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var inputText by remember { mutableStateOf("") }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("lobby_chat_section"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "💬 Room Chat",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF20312B),
                )
                Text(
                    text = "${messages.size} msgs",
                    fontSize = 11.sp,
                    color = Color(0xFF20312B).copy(alpha = 0.5f),
                )
            }

            Spacer(Modifier.height(6.dp))

            // Show last 3 messages
            val recent = messages.takeLast(3)
            if (recent.isEmpty()) {
                Text(
                    text = "No messages yet. Chat with players here!",
                    fontSize = 11.sp,
                    color = Color(0xFF20312B).copy(alpha = 0.45f),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    recent.forEach { msg ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${msg.senderName}: ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = chatColorOf(msg.color),
                            )
                            Text(
                                text = msg.text,
                                fontSize = 12.sp,
                                color = Color(0xFF20312B),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it.take(120) },
                    placeholder = { Text("Send a chat...", fontSize = 13.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lobby_chat_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                    ),
                )
                Button(
                    onClick = {
                        val trimmed = inputText.trim()
                        if (trimmed.isNotBlank()) {
                            onSend(trimmed)
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("lobby_chat_send"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F725F)),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Text("Send", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
