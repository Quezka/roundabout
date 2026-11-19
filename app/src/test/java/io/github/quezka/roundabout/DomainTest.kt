package io.github.quezka.roundabout

import io.github.quezka.roundabout.domain.Ambient
import io.github.quezka.roundabout.domain.AppCatalog
import io.github.quezka.roundabout.domain.AppEntry
import io.github.quezka.roundabout.domain.DashClock
import io.github.quezka.roundabout.domain.Greeting
import io.github.quezka.roundabout.domain.MediaState
import io.github.quezka.roundabout.domain.Role
import io.github.quezka.roundabout.domain.RoleDefaults
import io.github.quezka.roundabout.domain.Speed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {
    private fun app(p: String, l: String = p) = AppEntry(p, "$p.Main", l)

    @Test fun speedConvertsAndRejectsJunk() {
        assertEquals(36, Speed.kmh(10f))
        assertEquals(0, Speed.kmh(0f))
        assertNull(Speed.kmh(null))
        assertNull(Speed.kmh(-1f))
        assertNull(Speed.kmh(Float.NaN))
    }

    @Test fun gaugeClamps() {
        assertEquals(0f, Speed.gaugeFraction(null), 0f)
        assertEquals(0.5f, Speed.gaugeFraction(80), 0.0001f)
        assertEquals(1f, Speed.gaugeFraction(400), 0f)
    }

    @Test fun clockPads() {
        assertEquals("07:05", DashClock.time(7, 5))
        assertEquals("23:59", DashClock.time(23, 59))
    }

    @Test fun greetingByHour() {
        assertEquals(Greeting.NIGHT, Greeting.forHour(3))
        assertEquals(Greeting.MORNING, Greeting.forHour(8))
        assertEquals(Greeting.AFTERNOON, Greeting.forHour(14))
        assertEquals(Greeting.EVENING, Greeting.forHour(20))
        assertEquals(Greeting.NIGHT, Greeting.forHour(23))
    }

    @Test fun ambientCyclesAndParses() {
        assertEquals(Ambient.RED, Ambient.ORANGE.next())
        assertEquals(Ambient.ORANGE, Ambient.WHITE.next())
        assertEquals(Ambient.BLUE, Ambient.parse("BLUE"))
        assertEquals(Ambient.ORANGE, Ambient.parse("nope"))
        assertEquals(Ambient.ORANGE, Ambient.parse(null))
    }

    @Test fun catalogSortsAndHidesSelf() {
        val out = AppCatalog.sorted(listOf(app("b", "zeta"), app("me"), app("a", "Alpha"), app("a", "Alpha")), "me")
        assertEquals(listOf("Alpha", "zeta"), out.map { it.label })
    }

    @Test fun roleUsesChoiceThenDefaults() {
        val installed = listOf(app("com.google.android.apps.maps"), app("app.organicmaps"), app("x.y"))
        assertEquals("app.organicmaps", RoleDefaults.resolve(Role.NAVIGATION, null, installed)?.packageName)
        assertEquals("x.y", RoleDefaults.resolve(Role.NAVIGATION, "x.y", installed)?.packageName)
        // chosen app uninstalled -> fall back
        assertEquals("app.organicmaps", RoleDefaults.resolve(Role.NAVIGATION, "gone", installed)?.packageName)
        assertNull(RoleDefaults.resolve(Role.MUSIC, null, installed))
    }

    @Test fun favoritesToggleAndCap() {
        val f = io.github.quezka.roundabout.domain.Favorites
        assertEquals(listOf("a"), f.toggle(emptyList(), "a"))
        assertEquals(emptyList<String>(), f.toggle(listOf("a"), "a"))
        assertEquals(listOf("b", "c", "d"), f.toggle(listOf("a", "b", "c"), "d"))
        assertEquals(listOf("a", "b"), f.decode(f.encode(listOf("a", "b"))))
        assertEquals(emptyList<String>(), f.decode(null))
    }

    @Test fun mediaPresence() {
        assertFalse(MediaState.NONE.hasMedia)
        assertFalse(MediaState(" ", null, true, "p").hasMedia)
        assertTrue(MediaState("Song", null, false, "p").hasMedia)
    }
}
