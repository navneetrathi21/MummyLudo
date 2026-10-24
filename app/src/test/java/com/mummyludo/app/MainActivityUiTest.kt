package com.mummyludo.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun updateDialogDisplaysDetailsAndInvokesBothActions() {
        var restarts = 0
        var laters = 0
        compose.setContent {
            UpdateReadyDialog(
                onRestart = { restarts++ },
                onLater = { laters++ },
            )
        }

        compose.onNodeWithText("Update ready").assertIsDisplayed()
        compose.onNodeWithText("A new version has been downloaded from Play Store.").assertIsDisplayed()
        compose.onNodeWithText("Restart").performClick()
        compose.onNodeWithText("Later").performClick()

        assertEquals(1, restarts)
        assertEquals(1, laters)
    }
}
