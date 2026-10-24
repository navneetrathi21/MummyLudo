package com.mummyludo.app.storage

import androidx.test.core.app.ApplicationProvider
import com.mummyludo.app.game.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SqliteGameRepositoryTest {

    private lateinit var repository: GameRepository
    private lateinit var sqliteRepository: SqliteGameRepository
    private lateinit var helper: GameDatabaseHelper
    private val engine = LudoEngine()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(GameDatabaseHelper.DATABASE_NAME)
        sqliteRepository = SqliteGameRepository(context)
        repository = sqliteRepository
        helper = GameDatabaseHelper(context)
    }

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(GameDatabaseHelper.DATABASE_NAME)
    }

    @Test
    fun `empty repository has no active game`() {
        assertFalse(repository.hasActiveGame())
        assertNull(repository.loadActiveGame())
    }

    @Test
    fun `save and load active game round trips correctly using interface default revision`() {
        val seats = listOf(
            Seat(LudoColor.RED, PlayerType.HUMAN, "Player 1"),
            Seat(LudoColor.YELLOW, PlayerType.BOT, "Bot 1"),
        )
        val game = engine.newGame(seats)

        // Calls interface default parameter revision = 1
        repository.saveActiveGame(game)

        assertTrue(repository.hasActiveGame())
        val loaded = repository.loadActiveGame()
        assertNotNull(loaded)
        assertEquals(game, loaded)
    }

    @Test
    fun `saveActiveGame with explicit revision stores and updates state`() {
        val seats = listOf(
            Seat(LudoColor.RED, PlayerType.HUMAN, "Player 1"),
            Seat(LudoColor.YELLOW, PlayerType.BOT, "Bot 1"),
        )
        val game = engine.newGame(seats)

        // Calls with explicit revision
        repository.saveActiveGame(game, revision = 42)

        assertTrue(repository.hasActiveGame())
        val loaded = repository.loadActiveGame()
        assertNotNull(loaded)
        assertEquals(game, loaded)
    }

    @Test
    fun `overwriting active game updates stored state`() {
        val seats = listOf(
            Seat(LudoColor.RED, PlayerType.HUMAN, "Player 1"),
            Seat(LudoColor.YELLOW, PlayerType.BOT, "Bot 1"),
        )
        val initial = engine.newGame(seats)
        repository.saveActiveGame(initial)

        val updated = initial.copy(turn = 5, message = "Turn 5")
        repository.saveActiveGame(updated, revision = 2)

        val loaded = repository.loadActiveGame()
        assertEquals(5, loaded?.turn)
        assertEquals("Turn 5", loaded?.message)
    }

    @Test
    fun `clearActiveGame removes match from repository`() {
        val seats = listOf(
            Seat(LudoColor.RED, PlayerType.HUMAN, "Player 1"),
            Seat(LudoColor.YELLOW, PlayerType.BOT, "Bot 1"),
        )
        val game = engine.newGame(seats)
        repository.saveActiveGame(game)
        assertTrue(repository.hasActiveGame())

        repository.clearActiveGame()

        assertFalse(repository.hasActiveGame())
        assertNull(repository.loadActiveGame())
    }

    @Test
    fun `completed game is marked and not loaded as active`() {
        val seats = listOf(
            Seat(LudoColor.RED, PlayerType.HUMAN, "Player 1"),
            Seat(LudoColor.YELLOW, PlayerType.BOT, "Bot 1"),
        )
        val completedGame = engine.newGame(seats).copy(completed = true)

        repository.saveActiveGame(completedGame)

        assertFalse(repository.hasActiveGame())
        assertNull(repository.loadActiveGame())
    }

    @Test
    fun `database helper onUpgrade is safe to call`() {
        val db = helper.writableDatabase
        helper.onUpgrade(db, 1, 2)
        assertTrue(db.isOpen)
    }

    @Test
    fun `corrupted state json in database returns null on load`() {
        helper.saveMatch(
            matchId = GameDatabaseHelper.ACTIVE_MATCH_KEY,
            revision = 1,
            stateJson = "{ corrupt invalid json",
            completed = false,
        )
        assertTrue(repository.hasActiveGame())
        assertNull(repository.loadActiveGame())
    }

    @Test
    fun `deleteMatch removes specific match`() {
        helper.saveMatch(
            matchId = "custom_match_id",
            revision = 1,
            stateJson = "{}",
            completed = false,
        )
        // Also exercise saveMatch with default arguments (stateHash = "", completed = false)
        helper.saveMatch(
            matchId = "default_args_match",
            revision = 2,
            stateJson = "{}",
        )
        assertTrue(helper.hasActiveMatch("custom_match_id"))
        assertTrue(helper.hasActiveMatch("default_args_match"))
        helper.deleteMatch("custom_match_id")
        assertFalse(helper.hasActiveMatch("custom_match_id"))
    }
}
