package com.kithandkeen.qonverse

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Walks the main flows and saves screenshots to app/build/screens/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class ScreensTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    private fun back() { rule.activity.onBackPressedDispatcher.onBackPressed(); rule.waitForIdle() }

    @Test fun mainFlow() {
        shot("01-home")
        rule.onNodeWithText("Hotel Front Office — Japanese").performScrollTo().performClick()
        shot("02-pack")
        rule.onNodeWithText("Check-in").performClick()
        rule.onNodeWithContentDescription("Star this phrase").performClick()
        shot("03-phrase")
        rule.onNodeWithText("Show guest").performClick()
        shot("08-show-guest")
        rule.onNodeWithText("Close").performClick()
        back()
        rule.onNodeWithText("Practise").performClick()
        shot("04-practice")
        back()
        rule.onNodeWithText("🎧  Shadow mode").performClick()
        shot("09-shadow")
    }

    @Test fun addSettingsSearch() {
        rule.onNodeWithText("+ Add pack").performScrollTo().performClick()
        shot("05-add")
        back()
        rule.onNodeWithText("⌕  Search").performScrollTo().performClick()
        rule.onNodeWithText("Search in English or the other language").performTextInput("bill")
        shot("10-search")
        back()
        rule.onNodeWithContentDescription("Settings").performClick()
        shot("06-settings")
        rule.onNodeWithText("Dark").performClick()
        back()
        shot("07-home-dark")
    }
}
