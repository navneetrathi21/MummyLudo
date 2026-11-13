package com.mummyludo.app.viewmodel

import com.mummyludo.app.game.*
import com.mummyludo.app.nearby.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyLobbyViewModelTest {

    private lateinit var coordinator: NearbyLobbyCoordinator
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val engine = LudoEngine { 5 }

    @Before
    fun setUp() {
        coordinator = NearbyLobbyCoordinator()
    }

    @Test
    fun startHostingInitializesHostSessionAndLobby() = runTest(testDispatcher) {
        val vm = NearbyLobbyViewModel(coordinator, this)
        var startedSeats: List<NearbyStartedSeat>? = null
        var lastAction: NearbyGameAction? = null

        try {
            val success = vm.startHosting(
                initialPlayerName = "Host",
                onStarted = { startedSeats = it },
                onGameAction = { _, action -> lastAction = action },
            )

            assertTrue(success)
            assertTrue(vm.isHost)
            assertEquals(LudoColor.RED, vm.nearbyLocalColor)
            assertNotNull(vm.hostLobby)
            assertNotNull(vm.hostSession)
            assertNull(vm.nearbyError)

            vm.updateHostProfile("Host New", LudoColor.BLUE, false)
            assertEquals(LudoColor.BLUE, vm.nearbyLocalColor)
            val hostPlayer = vm.hostLobby?.players?.first { it.isHost }
            assertEquals("Host New", hostPlayer?.name)
            assertEquals(LudoColor.BLUE, hostPlayer?.color)
            assertFalse(hostPlayer?.ready ?: true)

            // Exercise bot operations with active hostSession
            vm.addBot(LudoColor.GREEN, 1)
            val botId = vm.hostLobby?.players?.firstOrNull { it.isBot }?.id ?: "bot-1"
            vm.updateBot(botId, LudoColor.YELLOW, 2)
            vm.removeBot(botId)

            // Attempting to change profile while ready is ignored
            vm.updateHostProfile("Host New", LudoColor.BLUE, true)
            vm.updateHostProfile("Attempt Change", LudoColor.YELLOW, true)
            assertEquals("Host New", vm.hostLobby?.players?.first { it.isHost }?.name)
        } finally {
            vm.closeHost()
        }
        assertNull(vm.hostSession)
        assertNull(vm.hostLobby)
    }

    @Test
    fun acceptNearbyActionBroadcastsAuthorizedActionAndIgnoresUnauthorized() {
        val vm = NearbyLobbyViewModel(coordinator, testScope)
        val seats = listOf(
            Seat(LudoColor.RED, PlayerType.HUMAN, "P1"),
            Seat(LudoColor.YELLOW, PlayerType.HUMAN, "P2"),
        )
        val game = engine.newGame(seats) // Red's turn, awaiting roll

        // Unauthorized color (Yellow attempting to roll on Red's turn)
        vm.acceptNearbyAction(game, LudoColor.YELLOW, NearbyGameAction.Roll, engine)
        assertNull(vm.nearbyEvent)
        assertEquals(0L, vm.sequence)

        // Authorized color (Red rolls)
        vm.acceptNearbyAction(game, LudoColor.RED, NearbyGameAction.Roll, engine)
        assertNotNull(vm.nearbyEvent)
        assertEquals(1L, vm.sequence)
        assertEquals(NearbyGameAction.Roll, vm.nearbyEvent?.action)
        assertEquals(5, vm.nearbyEvent?.rollValue)

        // Null game check
        vm.acceptNearbyAction(null, LudoColor.RED, NearbyGameAction.Roll, engine)
        assertEquals(1L, vm.sequence)
    }

    @Test
    fun closeGuestAndCloseAllSessionsResetStateCleanly() {
        val vm = NearbyLobbyViewModel(coordinator, testScope)
        vm.isHost = true
        vm.nearbyLocalColor = LudoColor.BLUE
        vm.nearbyEvent = NearbyGameEvent(1L, NearbyGameAction.Roll, 4)

        vm.closeGuest()
        assertNull(vm.clientSession)
        assertNull(vm.guestLobby)

        vm.closeAllSessions()
        assertFalse(vm.isHost)
        assertNull(vm.hostSession)
        assertNull(vm.clientSession)
        assertNull(vm.hostLobby)
        assertNull(vm.guestLobby)
        assertNull(vm.nearbyLocalColor)
        assertNull(vm.nearbyEvent)
    }

    @Test
    fun guestActionsAndOnClearedAreExercisedCleanly() {
        // Constructor default parameters
        val vmDefault = NearbyLobbyViewModel()
        assertNotNull(vmDefault.coordinator)

        val vm = NearbyLobbyViewModel(coordinator, testScope)
        vm.sendClientAction(NearbyGameAction.Roll)
        vm.updateGuestProfile("Guest", LudoColor.BLUE, true)

        vm.onCleared()
        assertFalse(vm.isHost)
    }

    @Test
    fun startHostingWithDefaultNameAndJoining() = runTest(testDispatcher) {
        val vm = NearbyLobbyViewModel(coordinator, this)
        try {
            val success = vm.startHosting(
                onStarted = {},
                onGameAction = { _, _ -> },
            )
            assertTrue(success)
            assertEquals("Player 1", vm.hostLobby?.players?.firstOrNull()?.name)

            // Exercise startJoining default initialName
            val payload = JoinPayload("session-test", "127.0.0.1", 49152, "token")
            vm.startJoining(payload = payload, onStarted = { _, _ -> })
            assertNotNull(vm.clientSession)
            val clientId = vm.clientSession?.playerId.orEmpty()
            vm.guestLobby = NearbyLobbyState("s", "t", listOf(NearbyPlayer(clientId, "Guest", false, LudoColor.BLUE, true)))
            vm.updateGuestProfile("Different", LudoColor.RED, true)
            vm.sendClientAction(NearbyGameAction.Roll)
        } finally {
            vm.closeAllSessions()
        }
    }

    @Test
    fun botAndTeamViewModelOperationsWorkWithoutSessionAndWithSession() {
        val vm = NearbyLobbyViewModel(coordinator, testScope)
        val initialLobby = coordinator.createHost("Host")
        vm.hostLobby = initialLobby

        // Without active session (direct lobby manipulation)
        val bot = vm.addBot(color = LudoColor.GREEN, team = 1)
        assertNotNull(vm.hostLobby)
        assertEquals(2, vm.hostLobby?.players?.size)
        val addedBot = vm.hostLobby?.players?.last()
        assertEquals(LudoColor.GREEN, addedBot?.color)
        assertEquals(1, addedBot?.team)

        // Update bot
        val botId = addedBot?.id.orEmpty()
        vm.updateBot(botId, LudoColor.YELLOW, 2)
        val modifiedBot = vm.hostLobby?.players?.last()
        assertEquals(LudoColor.YELLOW, modifiedBot?.color)
        assertEquals(2, modifiedBot?.team)

        // Update host team
        vm.updateHostProfile("Host", LudoColor.RED, false, team = 2)
        assertEquals(2, vm.hostLobby?.players?.first { it.isHost }?.team)

        // Remove bot
        vm.removeBot(botId)
        assertEquals(1, vm.hostLobby?.players?.size)

        // Edge case: null hostLobby
        vm.hostLobby = null
        vm.addBot()
        vm.removeBot("nonexistent")
        vm.updateBot("nonexistent", null, null)
    }
}
