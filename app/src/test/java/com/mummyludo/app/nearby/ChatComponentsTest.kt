package com.mummyludo.app.nearby

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mummyludo.app.game.LudoColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
class ChatComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun chatColorOfReturnsCorrectColorsForAllLudoColors() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFFEF5B67), chatColorOf(LudoColor.RED))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF49B675), chatColorOf(LudoColor.GREEN))
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF6CB3D), chatColorOf(LudoColor.YELLOW))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF438FE7), chatColorOf(LudoColor.BLUE))
    }

    @Test
    fun chatButtonWithBadgeDisplaysCorrectlyAndHandlesClicks() {
        var clicked = false
        compose.setContent {
            ChatButtonWithBadge(
                unreadCount = 0,
                onClick = { clicked = true },
            )
        }

        compose.onNodeWithTag("chat_toggle_button").assertIsDisplayed()
        compose.onNodeWithTag("chat_unread_badge").assertDoesNotExist()

        compose.onNodeWithTag("chat_toggle_button").performClick()
        assertTrue(clicked)
    }

    @Test
    fun chatButtonWithBadgeShowsBadgeForUnreadCounts() {
        compose.setContent {
            ChatButtonWithBadge(
                unreadCount = 3,
                onClick = {},
            )
        }
        compose.onNodeWithTag("chat_unread_badge").assertIsDisplayed().assertTextEquals("3")
    }

    @Test
    fun chatButtonWithBadgeShowsNinePlusForHighCounts() {
        compose.setContent {
            ChatButtonWithBadge(
                unreadCount = 15,
                onClick = {},
            )
        }
        compose.onNodeWithTag("chat_unread_badge").assertIsDisplayed().assertTextEquals("9+")
    }

    @Test
    fun floatingChatToastRendersContentAndHandlesDismiss() {
        var dismissed = false
        val message = NearbyChatMessage(
            id = "msg-1",
            senderId = "p1",
            senderName = "Rhea",
            color = LudoColor.GREEN,
            team = 2,
            text = "Good luck! 🎲",
            isTeamOnly = true,
        )

        compose.setContent {
            FloatingChatToast(
                message = message,
                onDismiss = { dismissed = true },
            )
        }

        compose.onNodeWithTag("floating_chat_toast").assertIsDisplayed()
        compose.onNodeWithText("Rhea").assertIsDisplayed()
        compose.onNodeWithText("T2").assertIsDisplayed()
        compose.onNodeWithText("[Team]").assertIsDisplayed()
        compose.onNodeWithText("Good luck! 🎲").assertIsDisplayed()

        compose.onNodeWithTag("floating_chat_dismiss").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun floatingChatToastDoesNotRenderWhenMessageIsNull() {
        compose.setContent {
            FloatingChatToast(
                message = null,
                onDismiss = {},
            )
        }
        compose.onNodeWithTag("floating_chat_toast").assertDoesNotExist()
    }

    @Test
    fun chatBottomSheetRendersEmptyStateAndCloses() {
        var closed = false
        compose.setContent {
            ChatBottomSheet(
                messages = emptyList(),
                localPlayerTeam = null,
                onDismiss = { closed = true },
                onSend = { _, _ -> },
            )
        }

        compose.onNodeWithTag("chat_bottom_sheet").assertIsDisplayed()
        compose.onNodeWithText("Room Chat").assertIsDisplayed()
        compose.onNodeWithText("No messages yet. Say hello!").assertIsDisplayed()
        compose.onNodeWithTag("chat_tab_all").assertDoesNotExist()

        compose.onNodeWithTag("chat_close_button").performClick()
        assertTrue(closed)
    }

    @Test
    fun chatBottomSheetRendersMessagesAndQuickReactions() {
        var sentText = ""
        var sentIsTeam = false
        val messages = listOf(
            NearbyChatMessage(
                id = "m1",
                senderId = "p1",
                senderName = "Alice",
                color = LudoColor.RED,
                team = 1,
                text = "Hello everyone",
                isTeamOnly = false,
            ),
            NearbyChatMessage(
                id = "m2",
                senderId = "p2",
                senderName = "Bob",
                color = LudoColor.BLUE,
                team = 1,
                text = "Secret team strategy",
                isTeamOnly = true,
            ),
        )

        compose.setContent {
            ChatBottomSheet(
                messages = messages,
                localPlayerTeam = 1,
                onDismiss = {},
                onSend = { text, isTeam ->
                    sentText = text
                    sentIsTeam = isTeam
                },
            )
        }

        compose.onNodeWithText("Alice").assertIsDisplayed()
        compose.onNodeWithText("Hello everyone").assertIsDisplayed()

        // Quick reaction tap
        compose.onNodeWithTag("chat_quick_reaction_0").performClick()
        assertEquals("🎲 Roll a 6!", sentText)
        assertEquals(false, sentIsTeam)

        // Switch to team tab
        compose.onNodeWithTag("chat_tab_team").performClick()
        compose.onNodeWithText("Bob").assertIsDisplayed()
        compose.onNodeWithText("Secret team strategy").assertIsDisplayed()

        // Type custom message and send on team tab
        compose.onNodeWithTag("chat_input_field").performTextInput("Team win incoming!")
        compose.onNodeWithTag("chat_send_button").performClick()
        assertEquals("Team win incoming!", sentText)
        assertEquals(true, sentIsTeam)
    }

    @Test
    fun chatBottomSheetIgnoresBlankInput() {
        var sendInvoked = false
        compose.setContent {
            ChatBottomSheet(
                messages = emptyList(),
                localPlayerTeam = null,
                onDismiss = {},
                onSend = { _, _ -> sendInvoked = true },
            )
        }

        compose.onNodeWithTag("chat_input_field").performTextInput("   ")
        compose.onNodeWithTag("chat_send_button").performClick()
        assertEquals(false, sendInvoked)
    }

    @Test
    fun lobbyChatSectionRendersEmptyAndSendsChat() {
        var sentText = ""
        compose.setContent {
            LobbyChatSection(
                messages = emptyList(),
                onSend = { sentText = it },
            )
        }

        compose.onNodeWithTag("lobby_chat_section").assertIsDisplayed()
        compose.onNodeWithText("No messages yet. Chat with players here!").assertIsDisplayed()

        compose.onNodeWithTag("lobby_chat_input").performTextInput("Ready to roll!")
        compose.onNodeWithTag("lobby_chat_send").performClick()
        assertEquals("Ready to roll!", sentText)
    }

    @Test
    fun lobbyChatSectionRendersRecentMessages() {
        val messages = listOf(
            NearbyChatMessage("1", "h", "Host", LudoColor.RED, text = "Welcome!"),
            NearbyChatMessage("2", "g", "Guest", LudoColor.BLUE, text = "Ready!"),
        )
        compose.setContent {
            LobbyChatSection(
                messages = messages,
                onSend = {},
            )
        }

        compose.onNodeWithText("Host: ").assertIsDisplayed()
        compose.onNodeWithText("Welcome!").assertIsDisplayed()
        compose.onNodeWithText("Guest: ").assertIsDisplayed()
        compose.onNodeWithText("Ready!").assertIsDisplayed()
    }
}
