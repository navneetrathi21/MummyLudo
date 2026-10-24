package com.mummyludo.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mummyludo.app.game.GameState
import com.mummyludo.app.game.LudoEngine
import com.mummyludo.app.game.Seat
import com.mummyludo.app.storage.GameRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class GameViewModel(
    val engine: LudoEngine = LudoEngine(),
    val repository: GameRepository,
    private val externalScope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val scope: CoroutineScope
        get() = externalScope ?: viewModelScope

    var game by mutableStateOf<GameState?>(null)
        internal set

    var hasSavedGame by mutableStateOf(false)
        internal set

    var autoPlayRunning by mutableStateOf(false)
        internal set

    var simulationSeed by mutableIntStateOf(1001)

    init {
        refreshSavedGameStatus()
    }

    fun refreshSavedGameStatus() {
        hasSavedGame = repository.hasActiveGame()
    }

    fun startNewGame(seats: List<Seat>): GameState {
        val newGame = engine.newGame(seats)
        game = newGame
        repository.saveActiveGame(newGame)
        hasSavedGame = true
        return newGame
    }

    fun continueSavedGame(): Boolean {
        val saved = repository.loadActiveGame()
        return if (saved != null && !saved.completed) {
            game = saved
            hasSavedGame = true
            true
        } else {
            hasSavedGame = false
            false
        }
    }

    fun updateGameState(updated: GameState, isNearby: Boolean = false) {
        game = updated
        if (!isNearby) {
            if (updated.completed) {
                repository.clearActiveGame()
                hasSavedGame = false
            } else {
                repository.saveActiveGame(updated)
                hasSavedGame = true
            }
        }
    }

    fun triggerAutoPlay(isNearby: Boolean = false) {
        if (autoPlayRunning) return
        val snapshot = game ?: return
        autoPlayRunning = true
        val seed = simulationSeed++
        scope.launch {
            val completed = withContext(ioDispatcher) {
                runCatching { engine.simulate(snapshot, seed) }
            }
            completed.onSuccess {
                game = it
                if (!isNearby) {
                    if (it.completed) {
                        repository.clearActiveGame()
                        hasSavedGame = false
                    } else {
                        repository.saveActiveGame(it)
                        hasSavedGame = true
                    }
                }
            }.onFailure {
                game = snapshot.copy(message = "Could not finish automatically")
            }
            autoPlayRunning = false
        }
    }

    fun clearAndCloseGame() {
        game = null
        repository.clearActiveGame()
        hasSavedGame = false
    }
}
