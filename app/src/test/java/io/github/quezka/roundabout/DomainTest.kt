package io.github.quezka.roundabout

import io.github.quezka.roundabout.domain.Ambient
import io.github.quezka.roundabout.domain.AppCatalog
import io.github.quezka.roundabout.domain.AppEntry
import io.github.quezka.roundabout.domain.Clip
import io.github.quezka.roundabout.domain.ClipFilter
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

    @Test fun dashcamLoopDeletesOldestOnly() {
        val p = io.github.quezka.roundabout.domain.DashcamPolicy
        val mb = 1024L * 1024
        val clips = (1..40).map { io.github.quezka.roundabout.domain.Clip("c$it", 60 * mb, it.toLong()) }
        val del = p.toDelete(clips, free = 10_000 * mb)
        assertTrue(del.isNotEmpty())
        assertEquals(del.map { it.name }, clips.take(del.size).map { it.name })
        assertTrue(clips.sumOf { it.bytes } - del.sumOf { it.bytes } + 56 * mb <= p.MAX_BYTES)
        assertTrue(p.toDelete(clips.take(3), free = 10_000 * mb).isEmpty())
        // disk almost full -> delete even under cap
        assertTrue(p.toDelete(clips.take(3), free = 100 * mb).isNotEmpty())
    }

    @Test fun dashcamAlternatesLenses() {
        val p = io.github.quezka.roundabout.domain.DashcamPolicy
        assertEquals(io.github.quezka.roundabout.domain.Lens.ROAD, p.nextLens(null, true))
        // cabin off by user choice
        assertEquals(io.github.quezka.roundabout.domain.Lens.ROAD, p.nextLens(io.github.quezka.roundabout.domain.Lens.ROAD, true))
        assertEquals(io.github.quezka.roundabout.domain.Lens.ROAD, p.nextLens(io.github.quezka.roundabout.domain.Lens.CABIN, true))
        assertEquals(io.github.quezka.roundabout.domain.Lens.ROAD, p.nextLens(io.github.quezka.roundabout.domain.Lens.ROAD, false))
        assertTrue(p.durationMs(io.github.quezka.roundabout.domain.Lens.ROAD) > p.durationMs(io.github.quezka.roundabout.domain.Lens.CABIN))
    }

    @Test fun clipFilterAndLens() {
        val a = Clip("20261119_090000_road.mp4", 1, 1)
        val b = Clip("20261119_090300_cabin.mp4", 1, 2)
        val c = Clip("20261119_090400_road.mp4", 1, 3, saved = true)
        assertEquals(io.github.quezka.roundabout.domain.Lens.CABIN, b.lens)
        assertEquals(listOf(c, b, a), ClipFilter.ALL.apply(listOf(a, b, c)))
        assertEquals(listOf(c, a), ClipFilter.ROAD.apply(listOf(a, b, c)))
        assertEquals(listOf(c), ClipFilter.SAVED.apply(listOf(a, b, c)))
    }

    @Test fun impactNeedsBigJolt() {
        val d = io.github.quezka.roundabout.domain.ImpactDetector
        assertFalse(d.isImpact(0f, 0f, 9.81f))
        // hard brake ~0.6g sideways
        assertFalse(d.isImpact(5.9f, 0f, 9.81f))
        // crash ~3g
        assertTrue(d.isImpact(29f, 0f, 9.81f))
        // free fall
        assertTrue(d.isImpact(0f, 0f, 0f))
    }

    @Test fun clipNameSortsByTime() {
        val n = io.github.quezka.roundabout.domain.DashcamPolicy.clipName(io.github.quezka.roundabout.domain.Lens.CABIN, 2026, 11, 19, 9, 5, 7)
        assertEquals("20261119_090507_cabin.mp4", n)
    }

    @Test fun mediaPresence() {
        assertFalse(MediaState.NONE.hasMedia)
        assertFalse(MediaState(" ", null, true, "p").hasMedia)
        assertTrue(MediaState("Song", null, false, "p").hasMedia)
    }
}
