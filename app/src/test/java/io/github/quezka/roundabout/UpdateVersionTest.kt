package io.github.quezka.roundabout

import io.github.quezka.roundabout.domain.UpdateVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {
    @Test fun newer() {
        assertTrue(UpdateVersion.isNewer("v0.2.0", "0.1.0"))
        assertTrue(UpdateVersion.isNewer("1.0.1", "1.0.0"))
        assertTrue(UpdateVersion.isNewer("v1.10.0", "1.9.0"))
        assertTrue(UpdateVersion.isNewer("2.0", "1.9.9"))
    }

    @Test fun sameOrOlder() {
        assertFalse(UpdateVersion.isNewer("0.1.0", "0.1.0"))
        assertFalse(UpdateVersion.isNewer("v0.1.0", "0.2.0"))
        assertFalse(UpdateVersion.isNewer("", "0.1.0"))
    }
}
