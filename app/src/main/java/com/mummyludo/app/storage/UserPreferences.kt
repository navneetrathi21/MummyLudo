package com.mummyludo.app.storage

import android.content.Context
import android.content.SharedPreferences

interface UserPreferences {
    fun getPlayerName(): String
    fun setPlayerName(name: String)
    fun hasCompletedWelcome(): Boolean
    fun setCompletedWelcome(completed: Boolean)
}

class SharedUserPreferences(context: Context) : UserPreferences {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getPlayerName(): String = prefs.getString(KEY_PLAYER_NAME, "") ?: ""

    override fun setPlayerName(name: String) {
        prefs.edit().putString(KEY_PLAYER_NAME, name.trim()).apply()
    }

    override fun hasCompletedWelcome(): Boolean = prefs.getBoolean(KEY_HAS_COMPLETED_WELCOME, false)

    override fun setCompletedWelcome(completed: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_COMPLETED_WELCOME, completed).apply()
    }

    companion object {
        const val PREFS_NAME = "mummyludo_user_prefs"
        const val KEY_PLAYER_NAME = "user_player_name"
        const val KEY_HAS_COMPLETED_WELCOME = "has_completed_welcome"
    }
}
