package io.github.quezka.roundabout.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import java.io.File

// frame @ 1s ; jpg cache on disk <- decode slow @ atom
class ClipThumbs(context: Context) {
    private val mem = LruCache<String, Bitmap>(40)
    private val disk = File(context.cacheDir, "thumbs").apply { mkdirs() }

    fun thumb(path: String, widthPx: Int): Bitmap? {
        val key = File(path).name + "@" + widthPx
        mem.get(key)?.let { return it }
        val cached = File(disk, "$key.jpg")
        if (cached.exists()) BitmapFactory.decodeFile(cached.path)?.let { mem.put(key, it); return it }
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            val frame = r.getFrameAtTime(1_000_000) ?: return null
            val h = frame.height * widthPx / frame.width
            val small = Bitmap.createScaledBitmap(frame, widthPx, h, true)
            if (small !== frame) frame.recycle()
            cached.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            mem.put(key, small)
            small
        } catch (e: Exception) {
            null
        } finally {
            try { r.release() } catch (e: Exception) { }
        }
    }
}
