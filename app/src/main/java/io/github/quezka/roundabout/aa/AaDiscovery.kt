package io.github.quezka.roundabout.aa

// wireless AA endpoint discovery ; neighbour table + tcp probe on 5277
object AaDiscovery {
    const val PORT = 5277

    // /proc/net/arp row: ip hwtype flags hwaddr mask device
    fun parseArp(text: String): List<String> {
        val out = ArrayList<String>()
        for (line in text.lineSequence()) {
            val f = line.trim().split(Regex("\\s+"))
            if (f.size < 4) continue
            val ip = f[0]
            if (!ip.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}"))) continue
            if (ip == "0.0.0.0") continue
            if (f[2] == "0x0") continue // incomplete neighbour entry
            if (out.contains(ip)) continue
            out.add(ip)
        }
        return out
    }

    fun readArp(): String = try {
        java.io.File("/proc/net/arp").readText()
    } catch (e: Exception) {
        ""
    }

    fun candidates(): List<String> = parseArp(readArp())

    fun probe(host: String, port: Int = PORT, timeoutMs: Int = 300): Boolean = try {
        java.net.Socket().use {
            it.connect(java.net.InetSocketAddress(host, port), timeoutMs)
            true
        }
    } catch (e: Exception) {
        false
    }

    fun find(port: Int = PORT): String? = candidates().firstOrNull { probe(it, port) }
}
