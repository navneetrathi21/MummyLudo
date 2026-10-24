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

        vm.closeHost()
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
        vm.updateGuestProfile("Guest", LudoColor.BLUE, true)
        vm.sendClientAction(NearbyGameAction.Roll)
        vm.closeAllSessions()
    }
}
