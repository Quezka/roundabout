package io.github.quezka.roundabout.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import io.github.quezka.roundabout.domain.Clip
import io.github.quezka.roundabout.domain.ClipStore
import java.io.File

class FileClipStore(context: Context) : ClipStore {
    // app dir -> no storage permission need ; visible via usb @ Android/data
    val dir: File = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "dashcam").apply { mkdirs() }

    override fun clips(): List<Clip> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".mp4") }.orEmpty()
            .map { Clip(it.name, it.length(), it.lastModified()) }

    override fun delete(clip: Clip) {
        File(dir, clip.name).delete()
    }

    override fun freeBytes(): Long = StatFs(dir.path).let { it.availableBlocksLong * it.blockSizeLong }

    fun file(name: String) = File(dir, name)
}
