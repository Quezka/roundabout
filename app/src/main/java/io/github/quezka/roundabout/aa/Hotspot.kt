package io.github.quezka.roundabout.aa

import android.content.Context
import java.io.File

// bring up a real access point on the tablet with root (hostapd + dnsmasq).
// the phone joins it after the bluetooth handshake, exactly like a car head unit.
class Hotspot(
    private val ctx: Context,
    private val ssid: String,
    private val password: String,
    private val addr: String = "192.168.43.1",
    private val log: (String) -> Unit = {},
) {
    private val conf = File(ctx.filesDir, "hostapd.conf")
    private val dnsconf = File(ctx.filesDir, "dnsmasq.conf")

    private fun sh(cmd: String): String = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        log("su[$cmd] -> ${out.trim().take(160)}")
        out
    } catch (e: Exception) {
        log("su failed: ${e.message}")
        ""
    }

    // no pkill/awk on this device ; find pids from ps and kill them
    private fun killAll(name: String) {
        val out = sh("ps")
        val pids = out.lineSequence()
            .map { it.trim() }
            .filter { it.contains(name) && !it.contains("grep") && !it.contains("sh -c") }
            .mapNotNull { it.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull() }
            .toList()
        for (p in pids) { log("kill $name $p"); sh("kill $p") }
    }

    // daemons inherit stdout and keep the pipe open ; run them detached
    private fun shq(cmd: String): Int {
        val out = sh("$cmd > /dev/null 2>&1; echo rc=\$?")
        val rc = out.lineSequence().mapNotNull { it.trim().removePrefix("rc=").toIntOrNull() }.firstOrNull() ?: -1
        log("run[$cmd] rc=$rc")
        return rc
    }

    companion object {
        // whatever address wlan0 currently holds (station mode) ; null when it has none
        fun stationIp(): String? {
            val out = try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "ip -o -4 addr show wlan0"))
                p.inputStream.bufferedReader().readText()
            } catch (e: Exception) { return null }
            val m = Regex("inet ([0-9.]+)/").find(out) ?: return null
            val ip = m.groupValues[1]
            return if (ip.startsWith("192.168.43.")) null else ip
        }
    }

    fun up(): Boolean {
        log("ap up $ssid")
        conf.writeText(
            """
            interface=wlan0
            driver=nl80211
            ssid=$ssid
            hw_mode=g
            wmm_enabled=0
            ieee80211n=0
            channel=6
            ignore_broadcast_ssid=0
            wpa=2
            wpa_passphrase=$password
            wpa_key_mgmt=WPA-PSK
            rsn_pairwise=CCMP
            """.trimIndent() + "\n",
        )
        dnsconf.writeText(
            """
            interface=wlan0
            bind-interfaces
            pid-file=${ctx.filesDir.absolutePath}/dnsmasq.pid
            log-facility=${ctx.filesDir.absolutePath}/dnsmasq.log
            dhcp-range=192.168.43.10,192.168.43.100,12h
            dhcp-option=3,$addr
            dhcp-option=6,$addr
            no-resolv
            """.trimIndent() + "\n",
        )
        killAll("hostapd")
        killAll("dnsmasq")
        sh("chmod 644 ${conf.absolutePath} ${dnsconf.absolutePath}")
        sh("svc wifi disable")
        Thread.sleep(2000)
        sh("ip link set wlan0 down")
        sh("ip addr flush dev wlan0")
        shq("hostapd -B ${conf.absolutePath}")
        Thread.sleep(1500)
        sh("ip addr add $addr/24 dev wlan0")
        sh("ip link set wlan0 up")
        shq("dnsmasq -C ${dnsconf.absolutePath}")
        val up = sh("cat /sys/class/net/wlan0/operstate").trim()
        log("wlan0 state=$up")
        return up.startsWith("up") || up.startsWith("unknown")
    }

    fun down() {
        killAll("dnsmasq")
        killAll("hostapd")
        sh("ip addr flush dev wlan0")
        sh("svc wifi enable")
        log("ap down")
    }

    fun bssid(): String = sh("cat /sys/class/net/wlan0/address").trim().ifEmpty { "02:00:00:00:00:00" }
}
