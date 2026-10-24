package com.mummyludo.app.nearby

import com.mummyludo.app.game.LudoColor
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
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

    @Test fun `wifi ssid is cleaned for display`() {
        assertEquals("Home Wi-Fi", cleanWifiSsid("\"Home Wi-Fi\""))
        assertNull(cleanWifiSsid("<unknown ssid>"))
        assertNull(cleanWifiSsid("  "))
    }

    @Test fun `wifi permissions follow Android version`() {
        assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION"), requiredWifiPermissions(32))
        assertEquals(
            listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.NEARBY_WIFI_DEVICES"),
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
            assertTrue("Host refused a ready two-player lobby", host!!.startGame())
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
}
