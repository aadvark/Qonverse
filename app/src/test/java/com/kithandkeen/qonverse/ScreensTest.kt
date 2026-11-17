package com.kithandkeen.qonverse

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Walks the main flow and saves screenshots to app/build/screens/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class ScreensTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    @Test fun mainFlow() {
        shot("01-home")
        rule.onNodeWithText("Hotel Front Office — Japanese").performScrollTo().performClick()
        shot("02-pack")
        rule.onNodeWithText("Check-in").performClick()
        shot("03-phrase")
        rule.activity.onBackPressedDispatcher.onBackPressed(); rule.waitForIdle()
        rule.onNodeWithText("Practise this pack").performClick()
        shot("04-practice")
    }

    @Test fun addAndSettings() {
        rule.onNodeWithText("+ Add pack").performScrollTo().performClick()
        shot("05-add")
        rule.activity.onBackPressedDispatcher.onBackPressed(); rule.waitForIdle()
        rule.onNodeWithText("⚙").performClick()
        shot("06-settings")
        rule.onNodeWithText("Dark").performClick()
        rule.activity.onBackPressedDispatcher.onBackPressed(); rule.waitForIdle()
        shot("07-home-dark")
    }
}
