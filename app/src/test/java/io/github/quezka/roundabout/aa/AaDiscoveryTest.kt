package io.github.quezka.roundabout.aa

import org.junit.Assert.assertEquals
import org.junit.Test

class AaDiscoveryTest {
    private val arp = """
        IP address       HW type     Flags       HW address            Mask     Device
        192.168.43.129   0x1         0x2         3c:5a:b4:11:22:33     *        wlan0
        192.168.43.1     0x1         0x0         00:00:00:00:00:00     *        wlan0
        10.0.0.5         0x1         0x2         aa:bb:cc:dd:ee:ff     *        rmnet0
        0.0.0.0          0x1         0x0         00:00:00:00:00:00     *        wlan0
        bad line
        192.168.43.129   0x1         0x2         3c:5a:b4:11:22:33     *        wlan0
    """.trimIndent()

    @Test fun parsesArpSkipsHeaderIncompleteAndDuplicates() {
        assertEquals(listOf("192.168.43.129", "10.0.0.5"), AaDiscovery.parseArp(arp))
    }

    @Test fun emptyInputIsSafe() {
        assertEquals(emptyList<String>(), AaDiscovery.parseArp(""))
        assertEquals(emptyList<String>(), AaDiscovery.parseArp("garbage\n"))
    }
}
