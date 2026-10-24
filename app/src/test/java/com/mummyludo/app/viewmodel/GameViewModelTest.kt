package com.mummyludo.app.viewmodel

import com.mummyludo.app.game.*
import com.mummyludo.app.storage.GameRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private lateinit var repository: FakeGameRepository
    private lateinit var engine: LudoEngine
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val humanSeats = listOf(
        Seat(LudoColor.RED, PlayerType.HUMAN, "Player 1"),
        Seat(LudoColor.YELLOW, PlayerType.BOT, "Bot 1"),
    )

    @Before
    fun setUp() {
        repository = FakeGameRepository()
        engine = LudoEngine { 4 }
    }

    @Test
    fun startNewGameInitializesAndPersistsMatch() {
        val vm = GameViewModel(engine, repository, testScope)
        assertFalse(vm.hasSavedGame)
        assertNull(vm.game)

        val game = vm.startNewGame(humanSeats)
        assertNotNull(vm.game)
        assertEquals(game, vm.game)
        assertTrue(vm.hasSavedGame)
        assertTrue(repository.hasActiveGame())
        assertEquals(game, repository.savedState)
    }

    @Test
    fun continueSavedGameRestoresActiveMatch() {
        val vm = GameViewModel(engine, repository, testScope)
        assertFalse(vm.continueSavedGame())

        val initialGame = engine.newGame(humanSeats)
        repository.saveActiveGame(initialGame)
        vm.refreshSavedGameStatus()
        assertTrue(vm.hasSavedGame)

        assertTrue(vm.continueSavedGame())
        assertEquals(initialGame, vm.game)
    }

    @Test
    fun continueSavedGameReturnsFalseIfGameIsCompleted() {
        val vm = GameViewModel(engine, repository, testScope)
        val completedGame = engine.newGame(humanSeats).copy(completed = true)
        repository.saveActiveGame(completedGame)

        assertFalse(vm.continueSavedGame())
        assertFalse(vm.hasSavedGame)
        assertNull(vm.game)
    }

    @Test
    fun updateGameStatePersistsWhenActiveAndClearsWhenCompleted() {
        val vm = GameViewModel(engine, repository, testScope)
        val game = vm.startNewGame(humanSeats)

        val turn2 = game.copy(turn = 2)
        vm.updateGameState(turn2, isNearby = false)
        assertEquals(turn2, vm.game)
        assertEquals(turn2, repository.savedState)
        assertTrue(vm.hasSavedGame)

        val completed = turn2.copy(completed = true)
        vm.updateGameState(completed, isNearby = false)
        assertEquals(completed, vm.game)
        assertFalse(vm.hasSavedGame)
        assertFalse(repository.hasActiveGame())

        // In nearby mode, local repository is not touched
        val nearbyGame = game.copy(turn = 3)
        vm.updateGameState(nearbyGame, isNearby = true)
        assertEquals(nearbyGame, vm.game)
        assertFalse(repository.hasActiveGame())
    }

    @Test
    fun clearAndCloseGameDiscardsGameAndStorage() {
        val vm = GameViewModel(engine, repository, testScope)
        vm.startNewGame(humanSeats)
        assertTrue(vm.hasSavedGame)
        assertNotNull(vm.game)

        vm.clearAndCloseGame()
        assertNull(vm.game)
        assertFalse(vm.hasSavedGame)
        assertFalse(repository.hasActiveGame())
    }

    @Test
    fun triggerAutoPlayCompletesAndClearsSavedMatch() = runTest(testDispatcher) {
        val vm = GameViewModel(LudoEngine { 6 }, repository, this, testDispatcher)
        val game = vm.startNewGame(humanSeats)
        assertTrue(repository.hasActiveGame())

        vm.triggerAutoPlay(isNearby = false)
        testScheduler.advanceUntilIdle()

        assertNotNull(vm.game)
        assertTrue(vm.game!!.completed)
        assertFalse(vm.autoPlayRunning)
        assertFalse(vm.hasSavedGame)
        assertFalse(repository.hasActiveGame())
    }

    @Test
    fun triggerAutoPlayHandlesSimulationFailureGracefully() = runTest(testDispatcher) {
        val failingEngine = object : LudoEngine({ 3 }) {
            override fun simulate(initial: GameState, seed: Int, maxTurns: Int): GameState {
                throw IllegalStateException("Simulation aborted")
            }
        }
        val vm = GameViewModel(failingEngine, repository, this, testDispatcher)
        vm.startNewGame(humanSeats)

        vm.triggerAutoPlay(isNearby = false)
        testScheduler.advanceUntilIdle()

        assertFalse(vm.autoPlayRunning)
        assertEquals("Could not finish automatically", vm.game?.message)
    }

    @Test
    fun triggerAutoPlaySupportsNearbyAndIncompleteSimulation() = runTest(testDispatcher) {
        // 1. When game is null
        val vmNull = GameViewModel(engine, repository, this, testDispatcher)
        vmNull.triggerAutoPlay()
        assertFalse(vmNull.autoPlayRunning)

        // 2. When simulation returns non-completed game (covers lines 97-98)
        val incompleteEngine = object : LudoEngine({ 3 }) {
            override fun simulate(initial: GameState, seed: Int, maxTurns: Int): GameState {
                return initial.copy(turn = 5, completed = false)
            }
        }
        val vmIncomplete = GameViewModel(incompleteEngine, repository, this, testDispatcher)
        vmIncomplete.startNewGame(humanSeats)
        vmIncomplete.triggerAutoPlay(isNearby = false)
        testScheduler.advanceUntilIdle()
        assertEquals(5, vmIncomplete.game?.turn)
        assertTrue(vmIncomplete.hasSavedGame)

        // 3. When isNearby is true
        val vmNearby = GameViewModel(LudoEngine { 6 }, repository, this, testDispatcher)
        vmNearby.startNewGame(humanSeats)
        vmNearby.triggerAutoPlay(isNearby = true)
        testScheduler.advanceUntilIdle()
        assertTrue(vmNearby.game!!.completed)
    }

    @Test
    fun triggerAutoPlayIgnoresDuplicateCallsWhileRunning() = runTest(testDispatcher) {
        val vm = GameViewModel(LudoEngine { 6 }, repository, this)
        vm.startNewGame(humanSeats)
        vm.autoPlayRunning = true

        val seedBefore = vm.simulationSeed
        vm.triggerAutoPlay(isNearby = false)
        assertEquals(seedBefore, vm.simulationSeed)
    }

    private class FakeGameRepository : GameRepository {
        var savedState: GameState? = null

        override fun saveActiveGame(state: GameState, revision: Int) {
            savedState = state
        }

        override fun loadActiveGame(): GameState? = savedState

        override fun clearActiveGame() {
            savedState = null
        }

        override fun hasActiveGame(): Boolean = savedState != null && !savedState!!.completed
    }
}
