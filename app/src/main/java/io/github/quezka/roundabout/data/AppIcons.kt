package io.github.quezka.roundabout.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap

// cache <- icon decode slow @ tablet
class AppIcons(private val context: Context) {
    private val cache = LruCache<String, Bitmap>(96)

    fun icon(packageName: String, activity: String, sizePx: Int): Bitmap? {
        val key = "$packageName/$activity@$sizePx"
        cache.get(key)?.let { return it }
        return try {
            val d = context.packageManager.getActivityIcon(ComponentName(packageName, activity))
            d.toBitmap(sizePx, sizePx).also { cache.put(key, it) }
        } catch (e: Exception) {
            null
        }
    }
}
