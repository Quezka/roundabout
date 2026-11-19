package io.github.quezka.roundabout.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// OTA self-update: read a GitHub releases feed, download the apk, install via root
class Updater(private val context: Context) {
    // point at your repo ; a plain static manifest also works if you swap the parser
    private val releasesApi = "https://api.github.com/repos/quezka/roundabout/releases/latest"

    data class Update(val tag: String, val apkUrl: String)

    fun currentVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) { "?" }

    // blocking ; call off the main thread
    fun check(): Update? {
        return try {
            val conn = (URL(releasesApi).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            val j = JSONObject(body)
            val tag = j.optString("tag_name", "")
            val assets = j.optJSONArray("assets")
            var apk: String? = null
            for (i in 0 until (assets?.length() ?: 0)) {
                val a = assets!!.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
            }
            if (tag.isBlank() || apk == null) null else Update(tag, apk!!)
        } catch (e: Exception) {
            null
        }
    }

    fun download(u: Update): File? {
        return try {
            val f = File(context.cacheDir, "update.apk")
            val conn = (URL(u.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 30_000
            }
            conn.inputStream.use { input -> f.outputStream().use { input.copyTo(it) } }
            f
        } catch (e: Exception) {
            null
        }
    }

    // root install, silent ; returns true on success
    fun install(apk: File): Boolean {
        return try {
            val p = ProcessBuilder("su", "-c", "pm install -r ${apk.absolutePath}")
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor() == 0 && out.contains("Success")
        } catch (e: Exception) {
            false
        }
    }
}
