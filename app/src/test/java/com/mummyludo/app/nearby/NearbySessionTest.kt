package com.mummyludo.app.nearby

import com.mummyludo.app.game.*
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class NearbySessionTest {
    @Test fun `join payload round trips`() {
        val payload = JoinPayload("session-123", "192.168.1.20", 42871, "a-secure-token")
        assertEquals(payload, JoinPayloadCodec.decode(JoinPayloadCodec.encode(payload)))
    }

    @Test fun `wrong protocol and invalid port are rejected`() {
        assertNull(JoinPayloadCodec.decode("mummyludo://join?v=2&session=session-123&host=192.168.1.2&port=42871&token=a-secure-token"))
        assertNull(JoinPayloadCodec.decode("mummyludo://join?v=1&session=session-123&host=192.168.1.2&port=70000&token=a-secure-token"))
    }

    @Test fun `duplicate colors are rejected and readiness requires a color`() {
        val coordinator = NearbyLobbyCoordinator()
        var lobby = coordinator.createHost("Navneet")
        lobby = coordinator.join(lobby, "rhea", "Rhea")
        lobby = coordinator.chooseColor(lobby, "rhea", LudoColor.RED)
        assertNull(lobby.players.first { it.id == "rhea" }.color)
        lobby = coordinator.setReady(lobby, "rhea", true)
        assertFalse(lobby.players.first { it.id == "rhea" }.ready)
    }

    @Test fun `lobby starts only when players have distinct colors and are ready`() {
        val coordinator = NearbyLobbyCoordinator()
        var lobby = coordinator.createHost("Navneet")
        lobby = coordinator.join(lobby, "rhea", "Rhea")
        lobby = coordinator.chooseColor(lobby, "rhea", LudoColor.BLUE)
        assertFalse(lobby.canStart)
        lobby = coordinator.setReady(lobby, "rhea", true)
        assertTrue(lobby.canStart)
    }

    @Test fun `ready player cannot change color until set to not ready`() {
        val coordinator = NearbyLobbyCoordinator()
        var lobby = coordinator.createHost("Navneet")
        lobby = coordinator.join(lobby, "rhea", "Rhea")
        lobby = coordinator.chooseColor(lobby, "rhea", LudoColor.BLUE)
        lobby = coordinator.setReady(lobby, "rhea", true)
        assertTrue(lobby.players.first { it.id == "rhea" }.ready)
        assertEquals(LudoColor.BLUE, lobby.players.first { it.id == "rhea" }.color)

        // Attempting to change color while ready is ignored
        lobby = coordinator.chooseColor(lobby, "rhea", LudoColor.GREEN)
        assertEquals(LudoColor.BLUE, lobby.players.first { it.id == "rhea" }.color)

        // Only after unreadying can color be changed
        lobby = coordinator.setReady(lobby, "rhea", false)
        assertFalse(lobby.players.first { it.id == "rhea" }.ready)
        lobby = coordinator.chooseColor(lobby, "rhea", LudoColor.GREEN)
        assertEquals(LudoColor.GREEN, lobby.players.first { it.id == "rhea" }.color)
    }

    @Test fun `wifi ssid is cleaned for display`() {
        assertEquals("Home Wi-Fi", cleanWifiSsid("\"Home Wi-Fi\""))
        assertNull(cleanWifiSsid("<unknown ssid>"))
        assertNull(cleanWifiSsid("  "))
    }

    @Test fun `wifi permissions follow Android version`() {
        assertEquals(
            listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"),
            requiredWifiPermissions(32),
        )
        assertEquals(
            listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.NEARBY_WIFI_DEVICES"),
            requiredWifiPermissions(33),
        )
    }

    @Test fun `guest profile reaches host and host start reaches guest`() {
        val coordinator = NearbyLobbyCoordinator()
        val initial = coordinator.createHost("Host")
        val guestReady = CountDownLatch(1)
        val guestStarted = CountDownLatch(1)
        var host: NearbyLanHost? = null
        val client = NearbyLanClient(
            payload = JoinPayload(initial.sessionId, "127.0.0.1", 42871, initial.joinToken),
            initialName = "Guest",
            onLobby = { lobby ->
                val guest = lobby.players.firstOrNull { it.name == "Rhea" }
                if (guest?.color == LudoColor.BLUE && guest.ready) guestReady.countDown()
            },
            onStarted = { seats ->
                if (seats.any { it.name == "Rhea" && it.color == LudoColor.BLUE }) guestStarted.countDown()
            },
            onError = { },
        )
        try {
            host = NearbyLanHost(initial, coordinator, onLobby = { }, onStarted = { })
            client.connect()
            val joined = CountDownLatch(1)
            repeat(20) {
                client.updateProfile("Rhea", LudoColor.BLUE, true)
                if (guestReady.await(100, TimeUnit.MILLISECONDS)) {
                    joined.countDown()
                    return@repeat
                }
            }
            assertTrue("Guest profile never reached host", joined.await(1, TimeUnit.SECONDS))
            val runningHost = host
            assertNotNull(runningHost)
            assertTrue("Host refused a ready two-player lobby", runningHost.startGame())
            assertTrue("Start event never reached guest", guestStarted.await(2, TimeUnit.SECONDS))
        } finally {
            client.close()
            host?.close()
        }
    }

    @Test fun `guest action reaches host and resolved event returns to guest`() {
        val coordinator = NearbyLobbyCoordinator()
        val initial = coordinator.createHost("Host")
        val actionReceived = CountDownLatch(1)
        val eventReceived = CountDownLatch(1)
        var host: NearbyLanHost? = null
        val client = NearbyLanClient(
            payload = JoinPayload(initial.sessionId, "127.0.0.1", 42871, initial.joinToken),
            initialName = "Guest",
            onLobby = { },
            onStarted = { },
            onError = { },
            onGameEvent = { event ->
                if (event.action == NearbyGameAction.Roll && event.rollValue == 4) eventReceived.countDown()
            },
        )
        try {
            host = NearbyLanHost(
                initial,
                coordinator,
                onLobby = { },
                onStarted = { },
                onGameAction = { _, action ->
                    if (action == NearbyGameAction.Roll) {
                        actionReceived.countDown()
                        host?.broadcastGameEvent(NearbyGameEvent(1, action, 4))
                    }
                },
            )
            client.connect()
            repeat(20) {
                client.sendGameAction(NearbyGameAction.Roll)
                if (eventReceived.await(100, TimeUnit.MILLISECONDS)) return@repeat
            }
            assertTrue(actionReceived.await(1, TimeUnit.SECONDS))
            assertTrue(eventReceived.await(1, TimeUnit.SECONDS))
        } finally {
            client.close()
            host?.close()
        }
    }

    @Test fun `game is fully playable over server between host and client with alternating turns and moves`() {
        val coordinator = NearbyLobbyCoordinator()
        val initial = coordinator.createHost("Host")
        val port = 42880
        val rolls = listOf(6, 2, 6, 2).iterator()
        val engine = LudoEngine { rolls.next() }

        var hostGame: GameState? = null
        var guestGame: GameState? = null
        var sequence = 0L
        var host: NearbyLanHost? = null

        val guestStarted = CountDownLatch(1)
        val guestEvents = LinkedBlockingQueue<NearbyGameEvent>()
        val currentLobby = AtomicReference(initial)

        val client = NearbyLanClient(
            payload = JoinPayload(initial.sessionId, "127.0.0.1", port, initial.joinToken),
            initialName = "Guest",
            onLobby = currentLobby::set,
            onStarted = { seats ->
                guestGame = engine.newGame(seats.map { Seat(it.color, PlayerType.HUMAN, it.name) })
                guestStarted.countDown()
            },
            onError = {},
            onGameEvent = { guestEvents.offer(it) },
        )

        try {
            host = NearbyLanHost(
                initialLobby = initial,
                coordinator = coordinator,
                onLobby = currentLobby::set,
                onStarted = { seats ->
                    hostGame = engine.newGame(seats.map { Seat(it.color, PlayerType.HUMAN, it.name) })
                },
                onGameAction = { playerId, action ->
                    val current = hostGame ?: return@NearbyLanHost
                    val playerColor = currentLobby.get()?.players?.firstOrNull { it.id == playerId }?.color
                    if (isAuthorizedGameAction(current, playerColor, action)) {
                        val rollVal = if (action == NearbyGameAction.Roll) engine.generateRoll() else null
                        val event = NearbyGameEvent(++sequence, action, rollVal)
                        host?.broadcastGameEvent(event)
                        when (action) {
                            NearbyGameAction.Roll -> {
                                hostGame = engine.selectOnlyDie(engine.commitRoll(current, requireNotNull(rollVal)))
                            }
                            is NearbyGameAction.Move -> {
                                val move = engine.legalMoves(current).first { it.tokenIds.toSet() == action.tokenIds.toSet() }
                                hostGame = engine.selectOnlyDie(engine.move(current, move).state)
                            }
                            is NearbyGameAction.SelectDie -> {
                                hostGame = engine.selectDie(current, action.bankIndex)
                            }
                        }
                    }
                },
                port = port,
            )

            client.connect()

            // Update Guest profile and ready
            var joined = false
            repeat(20) {
                client.updateProfile("Guest", LudoColor.GREEN, true)
                val lobby = currentLobby.get()
                if (lobby != null && lobby.players.size == 2 && lobby.canStart) {
                    joined = true
                    return@repeat
                }
                Thread.sleep(100)
            }
            assertTrue("Guest failed to join and get ready", joined)

            // 1. Host starts the game
            assertTrue("Host failed to start game", host.startGame())
            assertTrue("Guest never received game start", guestStarted.await(2, TimeUnit.SECONDS))
            assertNotNull(hostGame)
            assertNotNull(guestGame)
            assertEquals(2, hostGame!!.seats.size)
            assertEquals(LudoColor.RED, hostGame!!.seats[0].color)
            assertEquals(LudoColor.GREEN, hostGame!!.seats[1].color)
            assertEquals(0, hostGame!!.activeSeat) // Red's turn

            // 2. TURN 1 (Host / Red):
            // Host rolls on the server -> 6 (banked, awaitingRoll remains true)
            val roll1 = engine.generateRoll()
            assertEquals(6, roll1)
            val hostRollEv1 = NearbyGameEvent(++sequence, NearbyGameAction.Roll, roll1)
            host.broadcastGameEvent(hostRollEv1)
            hostGame = engine.commitRoll(hostGame!!, roll1)

            // Guest receives roll event 1 over socket
            val guestEv1 = guestEvents.poll(2, TimeUnit.SECONDS)
            assertNotNull(guestEv1)
            assertEquals(6, guestEv1!!.rollValue)
            guestGame = engine.commitRoll(guestGame!!, guestEv1.rollValue!!)
            assertEquals(hostGame, guestGame)
            assertTrue(hostGame!!.awaitingRoll)
            assertEquals(listOf(6), hostGame!!.diceBank)

            // Host rolls second time -> 2
            val roll2 = engine.generateRoll()
            assertEquals(2, roll2)
            val hostRollEv2 = NearbyGameEvent(++sequence, NearbyGameAction.Roll, roll2)
            host.broadcastGameEvent(hostRollEv2)
            hostGame = engine.commitRoll(hostGame!!, roll2)

            val guestEv2 = guestEvents.poll(2, TimeUnit.SECONDS)
            assertNotNull(guestEv2)
            assertEquals(2, guestEv2!!.rollValue)
            guestGame = engine.commitRoll(guestGame!!, guestEv2.rollValue!!)
            assertEquals(hostGame, guestGame)
            assertFalse(hostGame!!.awaitingRoll)
            assertEquals(listOf(6, 2), hostGame!!.diceBank)

            // Host selects first banked die (the 6)
            val hostSelectEv = NearbyGameEvent(++sequence, NearbyGameAction.SelectDie(0))
            host.broadcastGameEvent(hostSelectEv)
            hostGame = engine.selectDie(hostGame!!, 0)

            val guestEv3 = guestEvents.poll(2, TimeUnit.SECONDS)
            assertNotNull(guestEv3)
            assertEquals(NearbyGameAction.SelectDie(0), guestEv3!!.action)
            guestGame = engine.selectDie(guestGame!!, 0)
            assertEquals(hostGame, guestGame)
            assertEquals(6, hostGame!!.roll)
            assertEquals(listOf(2), hostGame!!.diceBank)

            // Host moves token "R0" onto board
            val redMove1 = engine.legalMoves(hostGame!!).first { "R0" in it.tokenIds }
            val hostMoveEv1 = NearbyGameEvent(++sequence, NearbyGameAction.Move(redMove1.tokenIds))
            host.broadcastGameEvent(hostMoveEv1)
            hostGame = engine.selectOnlyDie(engine.move(hostGame!!, redMove1).state)

            // Guest receives move event 1 over socket
            val guestEv4 = guestEvents.poll(2, TimeUnit.SECONDS)
            assertNotNull(guestEv4)
            assertTrue(guestEv4!!.action is NearbyGameAction.Move)
            val guestMove1 = engine.legalMoves(guestGame!!).first { "R0" in it.tokenIds }
            guestGame = engine.selectOnlyDie(engine.move(guestGame!!, guestMove1).state)
            assertEquals(hostGame, guestGame)
            assertEquals(0, hostGame!!.tokens.first { it.id == "R0" }.progress) // On board track!
            // Remaining banked die (2) was automatically selected by selectOnlyDie!
            assertEquals(2, hostGame!!.roll)
            assertTrue(hostGame!!.diceBank.isEmpty())

            // Host moves "R0" by 2 steps
            val redMove2 = engine.legalMoves(hostGame!!).first { "R0" in it.tokenIds }
            val hostMoveEv2 = NearbyGameEvent(++sequence, NearbyGameAction.Move(redMove2.tokenIds))
            host.broadcastGameEvent(hostMoveEv2)
            hostGame = engine.selectOnlyDie(engine.move(hostGame!!, redMove2).state)

            val guestEv5 = guestEvents.poll(2, TimeUnit.SECONDS)
            assertNotNull(guestEv5)
            guestGame = engine.selectOnlyDie(engine.move(guestGame!!, engine.legalMoves(guestGame!!).first { "R0" in it.tokenIds }).state)
            assertEquals(hostGame, guestGame)
            val redTurnFinal = requireNotNull(hostGame)
            val guestTurnRed = requireNotNull(guestGame)
            assertEquals(2, redTurnFinal.tokens.first { it.id == "R0" }.progress) // Advanced 2 steps!

            // Turn now ends for Red and passes to Green (Guest)!
            assertEquals(1, redTurnFinal.activeSeat)
            assertEquals(1, guestTurnRed.activeSeat)
            assertEquals(LudoColor.GREEN, redTurnFinal.seats[redTurnFinal.activeSeat].color)
            assertTrue(redTurnFinal.awaitingRoll)

            // 3. UNAUTHORIZED ACTION CHECK:
            // Red (Host) tries to roll while it's Green's turn -> must be rejected by server
            assertFalse(isAuthorizedGameAction(redTurnFinal, LudoColor.RED, NearbyGameAction.Roll))

            // 4. TURN 2 (Guest / Green over Network Socket):
            // Guest sends ROLL over socket!
            client.sendGameAction(NearbyGameAction.Roll)

            // Server generates roll (6), broadcasts, and guest receives it:
            val guestEv6 = guestEvents.poll(2, TimeUnit.SECONDS)
            assertNotNull("Guest never received its roll event from server", guestEv6)
            assertEquals(NearbyGameAction.Roll, guestEv6!!.action)
            assertEquals(6, guestEv6.rollValue)
            guestGame = engine.commitRoll(guestGame!!, guestEv6.rollValue!!)
            assertEquals(hostGame, guestGame)
            assertTrue(guestGame!!.awaitingRoll)
            assertEquals(listOf(6), guestGame!!.diceBank)

            // Green rolls second time (2) over socket:
            client.sendGameAction(NearbyGameAction.Roll)

            val guestEv7 = guestEvents.poll(2, TimeUnit.SECONDS)
            assertNotNull(guestEv7)
            assertEquals(2, guestEv7!!.rollValue)
            guestGame = engine.commitRoll(guestGame!!, guestEv7.rollValue!!)
            assertEquals(hostGame, guestGame)
            assertFalse(guestGame!!.awaitingRoll)
            assertEquals(listOf(6, 2), guestGame!!.diceBank)

            // Guest selects first banked die (the 6) over socket:
            client.sendGameAction(NearbyGameAction.SelectDie(0))

            val guestEv8 = guestEvents.poll(2, TimeUnit.SECONDS)
            assertNotNull(guestEv8)
            assertEquals(NearbyGameAction.SelectDie(0), guestEv8!!.action)
            guestGame = engine.selectDie(guestGame!!, 0)
            assertEquals(hostGame, guestGame)
            assertEquals(6, guestGame!!.roll)
            assertEquals(listOf(2), guestGame!!.diceBank)

            // Guest sends MOVE ("G0" enter board) over socket!
            client.sendGameAction(NearbyGameAction.Move(listOf("G0")))

            // Server verifies legal move, moves on server, broadcasts event to guest:
            val guestEv9 = guestEvents.poll(2, TimeUnit.SECONDS)
            assertNotNull("Guest never received move event from server", guestEv9)
            assertEquals(NearbyGameAction.Move(listOf("G0")), guestEv9!!.action)
            guestGame = engine.selectOnlyDie(engine.move(guestGame!!, engine.legalMoves(guestGame!!).first { "G0" in it.tokenIds }).state)
            assertEquals(hostGame, guestGame)
            assertEquals(0, guestGame!!.tokens.first { it.id == "G0" }.progress) // Green is on the board!
            // Remaining banked die (2) was automatically selected by selectOnlyDie!
            assertEquals(2, guestGame!!.roll)
            assertTrue(guestGame!!.diceBank.isEmpty())

            // Guest moves "G0" by 2 steps over socket:
            client.sendGameAction(NearbyGameAction.Move(listOf("G0")))

            val guestEv10 = guestEvents.poll(2, TimeUnit.SECONDS)
            assertNotNull(guestEv10)
            guestGame = engine.selectOnlyDie(engine.move(guestGame!!, engine.legalMoves(guestGame!!).first { "G0" in it.tokenIds }).state)
            assertEquals(hostGame, guestGame)
            val finalHost = requireNotNull(hostGame)
            val finalGuest = requireNotNull(guestGame)
            assertEquals(2, finalHost.tokens.first { it.id == "G0" }.progress) // Green advanced 2 steps!

            // Turn passes back to Red (Host)!
            assertEquals(0, finalHost.activeSeat)
            assertEquals(0, finalGuest.activeSeat)
            assertTrue(finalHost.awaitingRoll)
            assertTrue(finalGuest.awaitingRoll)

            // Both match states are in complete 1:1 synchronization across the server!
            assertEquals(hostGame, guestGame)
        } finally {
            client.close()
            host?.close()
        }
    }

    @Test fun `host profile updates are published and host alone cannot start`() {
        val coordinator = NearbyLobbyCoordinator()
        val initial = coordinator.createHost("Host")
        val published = AtomicReference<NearbyLobbyState>()
        NearbyLanHost(
            initialLobby = initial,
            coordinator = coordinator,
            onLobby = published::set,
            onStarted = { fail("Host-only game must not start") },
        ).use { host ->
            host.updateProfile("New host name", LudoColor.BLUE, false)

            val player = published.get().players.single()
            assertEquals("New host name", player.name)
            assertEquals(LudoColor.BLUE, player.color)
            assertFalse(player.ready)
            assertFalse(host.startGame())
        }
    }

    @Test fun `ready host cannot change color or name while remaining ready`() {
        val coordinator = NearbyLobbyCoordinator()
        val initial = coordinator.createHost("Host")
        val published = AtomicReference(initial)
        NearbyLanHost(
            initialLobby = initial,
            coordinator = coordinator,
            onLobby = published::set,
            onStarted = {},
        ).use { host ->
            assertTrue(published.get().players.single().ready)
            assertEquals(LudoColor.RED, published.get().players.single().color)

            // Attempt to update color while staying ready -> ignored
            host.updateProfile("Hacker Host", LudoColor.YELLOW, true)
            assertEquals("Host", published.get().players.single().name)
            assertEquals(LudoColor.RED, published.get().players.single().color)

            // Unreadying allows updates
            host.updateProfile("Hacker Host", LudoColor.YELLOW, false)
            assertEquals("Hacker Host", published.get().players.single().name)
            assertEquals(LudoColor.YELLOW, published.get().players.single().color)
            assertFalse(published.get().players.single().ready)
        }
    }

    @Test fun `invalid join token is rejected and never enters the lobby`() {
        val coordinator = NearbyLobbyCoordinator()
        val initial = coordinator.createHost("Host")
        val error = AtomicReference<String>()
        val rejected = CountDownLatch(1)
        val hostLobby = AtomicReference(initial)
        NearbyLanHost(
            initialLobby = initial,
            coordinator = coordinator,
            onLobby = hostLobby::set,
            onStarted = { fail("Rejected guest must not start a game") },
        ).use { host ->
            NearbyLanClient(
                payload = JoinPayload(initial.sessionId, "127.0.0.1", 42871, "wrong-join-token"),
                initialName = "Uninvited",
                onLobby = { fail("Rejected guest must not receive a lobby") },
                onStarted = { fail("Rejected guest must not start a game") },
                onError = { message -> error.set(message); rejected.countDown() },
            ).use { client ->
                client.connect()
                assertTrue("Host did not reject invalid token", rejected.await(3, TimeUnit.SECONDS))
                assertEquals("This game code has expired", error.get())
                assertEquals(1, hostLobby.get().players.size)
                assertFalse(host.startGame())
            }
        }
    }

    @Test fun `unreachable host reports a connection error`() {
        val unavailablePort = ServerSocket(0).use { it.localPort }
        val error = AtomicReference<String>()
        val failed = CountDownLatch(1)
        NearbyLanClient(
            payload = JoinPayload("session-123", "127.0.0.1", unavailablePort, "unused-token-value"),
            initialName = "Guest",
            onLobby = { fail("Unreachable host must not send a lobby") },
            onStarted = { fail("Unreachable host must not start a game") },
            onError = { message -> error.set(message); failed.countDown() },
        ).use { client ->
            client.connect()
            assertTrue("Connection failure was not reported", failed.await(3, TimeUnit.SECONDS))
            assertEquals("Could not reach the host on this Wi-Fi", error.get())
        }
    }

    @Test fun `create host generates 4-digit numeric room code`() {
        val coordinator = NearbyLobbyCoordinator()
        val host = coordinator.createHost("Alex")
        assertEquals(4, host.roomCode.length)
        assertTrue(host.roomCode.all { it.isDigit() })
    }

    @Test fun `4-digit code payload round trips correctly`() {
        val payload = JoinPayload("4821", "192.168.1.15", 42871, "4821")
        val encoded = JoinPayloadCodec.encode(payload)
        assertEquals(payload, JoinPayloadCodec.decode(encoded))
    }

    @Test fun `discoverHostByRoomCode finds running host on loopback`() = kotlinx.coroutines.runBlocking {
        val coordinator = NearbyLobbyCoordinator()
        val initial = coordinator.createHost("Host")
        NearbyLanHost(
            initialLobby = initial,
            coordinator = coordinator,
            onLobby = {},
            onStarted = {},
        ).use {
            val found = discoverHostByRoomCode(initial.roomCode, localIp = "127.0.0.1")
            assertNotNull(found)
            assertEquals(initial.roomCode, found?.sessionId)
            assertEquals("127.0.0.1", found?.hostAddress)

            val wrong = discoverHostByRoomCode("0000", localIp = "127.0.0.1")
            assertNull(wrong)

            val subnetFound = discoverHostByRoomCode(initial.roomCode, localIp = "127.0.0.2", subnetRange = 1..1)
            assertNotNull(subnetFound)
            assertEquals("127.0.0.1", subnetFound?.hostAddress)

            val subnetNotFound = discoverHostByRoomCode(initial.roomCode, localIp = "192.0.2.1", subnetRange = 1..1)
            assertNull(subnetNotFound)
        }
    }

    @Test fun `discoverHostByRoomCode rejects non 4-digit code`() = kotlinx.coroutines.runBlocking {
        assertNull(discoverHostByRoomCode("12"))
        assertNull(discoverHostByRoomCode("abcd"))
    }

    @Test fun `room code generation produces 4-digit string`() {
        val coordinator = NearbyLobbyCoordinator()
        val codeWithSeed = coordinator.generateRoomCode("seed-123")
        assertEquals(4, codeWithSeed.length)
        assertTrue(codeWithSeed.all { it.isDigit() })

        val codeWithoutSeed = coordinator.generateRoomCode(null)
        assertEquals(4, codeWithoutSeed.length)
        assertTrue(codeWithoutSeed.all { it.isDigit() })
    }

    @Test fun `client send failure triggers error callback`() {
        val coordinator = NearbyLobbyCoordinator()
        val initial = coordinator.createHost("Host")
        val errorLatch = CountDownLatch(1)
        val errorMessage = AtomicReference<String>()
        val clientLobby = CountDownLatch(1)

        val host = NearbyLanHost(
            initialLobby = initial,
            coordinator = coordinator,
            onLobby = {},
            onStarted = {},
        )
        val client = NearbyLanClient(
            payload = JoinPayload(initial.sessionId, "127.0.0.1", 42871, initial.joinToken),
            initialName = "Guest",
            onLobby = { clientLobby.countDown() },
            onStarted = {},
            onError = { message ->
                errorMessage.set(message)
                errorLatch.countDown()
            },
        )
        try {
            client.connect()
            assertTrue("Client did not connect", clientLobby.await(3, TimeUnit.SECONDS))
            host.close()
            Thread.sleep(100)
            repeat(10) {
                client.updateProfile("Guest2", LudoColor.BLUE, false)
                Thread.sleep(50)
            }
            assertTrue("Client did not report connection lost", errorLatch.await(3, TimeUnit.SECONDS))
            assertEquals("Connection to the host was lost", errorMessage.get())
        } finally {
            client.close()
            host.close()
        }
    }
}
