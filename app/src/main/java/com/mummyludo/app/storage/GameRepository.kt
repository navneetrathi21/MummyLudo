package com.mummyludo.app.storage

import android.content.Context
import com.mummyludo.app.game.GameState

interface GameRepository {
    fun saveActiveGame(state: GameState) = saveActiveGame(state, 1)
    fun saveActiveGame(state: GameState, revision: Int)
    fun loadActiveGame(): GameState?
    fun clearActiveGame()
    fun hasActiveGame(): Boolean
}

class SqliteGameRepository(context: Context) : GameRepository {
    private val dbHelper = GameDatabaseHelper(context.applicationContext)

    override fun saveActiveGame(state: GameState, revision: Int) {
        val json = GameStateCodec.encode(state)
        dbHelper.saveMatch(
            matchId = GameDatabaseHelper.ACTIVE_MATCH_KEY,
            revision = revision,
            stateJson = json,
            completed = state.completed,
        )
    }

    override fun loadActiveGame(): GameState? {
        val json = dbHelper.loadMatchJson(GameDatabaseHelper.ACTIVE_MATCH_KEY) ?: return null
        return GameStateCodec.decode(json)
    }

    override fun clearActiveGame() {
        dbHelper.deleteMatch(GameDatabaseHelper.ACTIVE_MATCH_KEY)
    }

    override fun hasActiveGame(): Boolean {
        return dbHelper.hasActiveMatch(GameDatabaseHelper.ACTIVE_MATCH_KEY)
    }
}
