package io.github.quezka.roundabout

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.quezka.roundabout.domain.Ambient
import io.github.quezka.roundabout.domain.AppEntry
import io.github.quezka.roundabout.domain.MediaAction
import io.github.quezka.roundabout.domain.MediaState
import io.github.quezka.roundabout.ui.AppsScreen
import io.github.quezka.roundabout.ui.DashIntent
import io.github.quezka.roundabout.ui.DashScreen
import io.github.quezka.roundabout.ui.DashUi
import io.github.quezka.roundabout.ui.RoundaboutTheme
import io.github.quezka.roundabout.ui.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [22], qualifiers = "w960dp-h600dp-land")
class ScreensTest {
    @get:Rule val rule = createComposeRule()

    private fun ui(media: MediaState = MediaState("Song A", "Band", true, "p"), access: Boolean = true) = DashUi(
        date = "Thursday 19 November", greeting = "Hi", speedKmh = 47, media = media,
        mediaAccess = access, navigation = AppEntry("nav", "nav.Main", "Maps"), music = null, player = null,
    )

    @Test fun dashShowsClockSpeedMedia() {
        val got = mutableListOf<DashIntent>()
        rule.setContent { RoundaboutTheme { DashScreen(ui(), { _, _, _ -> null }) { got += it } } }
        rule.onNodeWithText("Hi").assertIsDisplayed()
        rule.onNodeWithText("47").assertIsDisplayed()
        rule.onNodeWithText("Song A").assertIsDisplayed()
        rule.onNodeWithText("Navigate · Maps").assertIsDisplayed()
        rule.onNodeWithTag("navigate").performClick()
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Settings")).performClick()
        assertEquals(listOf(DashIntent.Navigate, DashIntent.Settings), got)
    }

    @Test fun dashAsksForMediaAccess() {
        val got = mutableListOf<DashIntent>()
        rule.setContent { RoundaboutTheme { DashScreen(ui(MediaState.NONE, access = false), { _, _, _ -> null }) { got += it } } }
        rule.onNodeWithText("Allow").performClick()
        assertEquals(listOf<DashIntent>(DashIntent.GrantMedia), got)
    }

    @Test fun playButtonSendsToggle() {
        val got = mutableListOf<DashIntent>()
        rule.setContent { RoundaboutTheme { DashScreen(ui(), { _, _, _ -> null }) { got += it } } }
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Pause")).performClick()
        assertEquals(listOf<DashIntent>(DashIntent.Media(MediaAction.TOGGLE)), got)
    }

    @Test fun appsGridLaunches() {
        var launched: AppEntry? = null
        val apps = listOf(AppEntry("a", "a.M", "Alpha"), AppEntry("b", "b.M", "Beta"))
        rule.setContent { RoundaboutTheme { AppsScreen(apps, emptyList(), { _, _, _ -> null }, {}, { launched = it }, { _, _ -> }, {}, {}) } }
        rule.onNodeWithText("Beta").performClick()
        assertEquals("b", launched?.packageName)
    }

    @Test fun settingsPicksAmbient() {
        var picked: Ambient? = null
        rule.setContent { RoundaboutTheme { SettingsScreen(Ambient.ORANGE, "0.1.0", {}, { picked = it }, {}, {}) } }
        rule.onNodeWithTag("ambient_green").performClick()
        assertEquals(Ambient.GREEN, picked)
    }
}
