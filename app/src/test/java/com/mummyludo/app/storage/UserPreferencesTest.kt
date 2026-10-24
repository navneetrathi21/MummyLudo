package com.mummyludo.app.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserPreferencesTest {

    private lateinit var preferences: SharedUserPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(SharedUserPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        preferences = SharedUserPreferences(context)
    }

    @Test
    fun defaultValuesAreEmptyAndNotCompleted() {
        assertEquals("", preferences.getPlayerName())
        assertFalse(preferences.hasCompletedWelcome())
    }

    @Test
    fun setPlayerNamePersistsAndTrimsWhitespace() {
        preferences.setPlayerName("  Cleo  ")
        assertEquals("Cleo", preferences.getPlayerName())
    }

    @Test
    fun setCompletedWelcomePersistsStatus() {
        preferences.setCompletedWelcome(true)
        assertTrue(preferences.hasCompletedWelcome())

        preferences.setCompletedWelcome(false)
        assertFalse(preferences.hasCompletedWelcome())
    }
}
