package io.github.quezka.roundabout.ui

import android.graphics.Bitmap
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.quezka.roundabout.R
import io.github.quezka.roundabout.domain.Clip
import io.github.quezka.roundabout.domain.ClipFilter
import io.github.quezka.roundabout.domain.Lens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

typealias ThumbLoader = (path: String, widthPx: Int) -> Bitmap?

@Composable
fun ClipsScreen(
    clips: List<Clip>,
    path: (Clip) -> String,
    thumb: ThumbLoader,
    onBack: () -> Unit,
    onSave: (Clip) -> Unit,
    onDelete: (Clip) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var filter by rememberSaveable { mutableStateOf(ClipFilter.ALL) }
    var playing by remember { mutableStateOf<Clip?>(null) }
    val shown = filter.apply(clips)

    val open = playing
    if (open != null) {
        val i = shown.indexOfFirst { it.name == open.name }
        ClipPlayer(
            clip = open,
            path = path(open),
            onBack = { playing = null },
            onPrev = shown.getOrNull(i + 1)?.let { c -> { playing = c } },
            onNext = shown.getOrNull(i - 1)?.let { c -> { playing = c } },
            onSave = { onSave(open); playing = open.copy(saved = true) },
            onDelete = { onDelete(open); playing = null },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(cs.surfaceContainerLowest).padding(horizontal = 28.dp, vertical = 20.dp)) {
        ScreenHeader(stringResource(R.string.clips_title), onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 14.dp)) {
            ClipFilter.entries.forEach { f ->
                FilterChip(
                    selected = f == filter,
                    onClick = { filter = f },
                    label = { Text(filterLabel(f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("filter_" + f.name.lowercase()),
                )
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_clips), color = cs.onSurfaceVariant, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(230.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shown, key = { (if (it.saved) "s/" else "") + it.name }) { clip ->
                    ClipCard(clip, path(clip), thumb) { playing = clip }
                }
            }
        }
    }
}

@Composable
private fun filterLabel(f: ClipFilter) = stringResource(
    when (f) {
        ClipFilter.ALL -> R.string.filter_all
        ClipFilter.ROAD -> R.string.lens_road_title
        ClipFilter.CABIN -> R.string.lens_cabin_title
        ClipFilter.SAVED -> R.string.filter_saved
    },
)

@Composable
private fun lensLabel(l: Lens?) = when (l) {
    Lens.ROAD -> stringResource(R.string.lens_road_title)
    Lens.CABIN -> stringResource(R.string.lens_cabin_title)
    null -> ""
}

private fun whenText(c: Clip): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(c.modified))

@Composable
private fun ClipCard(clip: Clip, path: String, thumb: ThumbLoader, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val px = with(LocalDensity.current) { 230.dp.roundToPx() }
    val bmp by produceState<Bitmap?>(null, path) { value = withContext(Dispatchers.IO) { thumb(path, px) } }
    Card(
        Modifier.clickable(onClick = onClick).testTag("clip"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(cs.surfaceContainerHighest)) {
            bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Surface(Modifier.padding(10.dp), shape = RoundedCornerShape(10.dp), color = Color.Black.copy(alpha = 0.6f)) {
                Text(lensLabel(clip.lens), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
            if (clip.saved) {
                Surface(Modifier.align(Alignment.TopEnd).padding(10.dp), shape = RoundedCornerShape(10.dp), color = cs.primary) {
                    Text(stringResource(R.string.filter_saved), color = cs.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            Box(Modifier.align(Alignment.Center).size(56.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center) { GlyphIcon(Glyph.PLAY, Color.White, 26.dp) }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(whenText(clip), color = cs.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("%.0f MB".format(clip.bytes / 1048576.0), color = cs.onSurfaceVariant, fontSize = 15.sp)
        }
    }
}

@Composable
private fun ClipPlayer(
    clip: Clip,
    path: String,
    onBack: () -> Unit,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var view by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember(path) { mutableStateOf(true) }
    var progress by remember(path) { mutableFloatStateOf(0f) }
    var confirmDelete by remember { mutableStateOf(false) }

    // poll position 4x/s only while playing
    LaunchedEffect(view, isPlaying, path) {
        val v = view ?: return@LaunchedEffect
        while (isPlaying) {
            if (v.duration > 0) progress = v.currentPosition.toFloat() / v.duration
            delay(250)
        }
    }
    DisposableEffect(Unit) { onDispose { view?.stopPlayback() } }

    Row(Modifier.fillMaxSize().background(cs.surfaceContainerLowest).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { ctx -> VideoView(ctx).also { view = it } },
                update = { v ->
                    if (v.tag != path) {
                        v.tag = path
                        v.setVideoPath(path)
                        v.setOnCompletionListener { isPlaying = false; progress = 1f }
                        v.start()
                    }
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }
        Card(Modifier.width(300.dp).fillMaxHeight(), shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                val back = stringResource(R.string.back)
                FilledTonalIconButton(onBack, Modifier.size(60.dp).semantics { contentDescription = back }, shape = RoundedCornerShape(20.dp)) {
                    GlyphIcon(Glyph.BACK, cs.onSecondaryContainer, 26.dp)
                }
                Spacer(Modifier.height(14.dp))
                Text(lensLabel(clip.lens), color = cs.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(whenText(clip), color = cs.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Slider(
                    value = progress,
                    onValueChange = { f -> progress = f; view?.let { v -> if (v.duration > 0) v.seekTo((f * v.duration).toInt()) } },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton({ onPrev?.invoke() }, Modifier.size(60.dp), enabled = onPrev != null) {
                        GlyphIcon(Glyph.PREVIOUS, cs.onSecondaryContainer, 24.dp)
                    }
                    FilledIconButton({
                        view?.let { v -> if (v.isPlaying) { v.pause(); isPlaying = false } else { v.start(); isPlaying = true } }
                    }, Modifier.size(width = 96.dp, height = 72.dp), shape = RoundedCornerShape(26.dp)) {
                        GlyphIcon(if (isPlaying) Glyph.PAUSE else Glyph.PLAY, cs.onPrimary, 30.dp)
                    }
                    FilledTonalIconButton({ onNext?.invoke() }, Modifier.size(60.dp), enabled = onNext != null) {
                        GlyphIcon(Glyph.NEXT, cs.onSecondaryContainer, 24.dp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onSave, Modifier.fillMaxWidth().height(56.dp), enabled = !clip.saved, shape = RoundedCornerShape(28.dp)) {
                    Text(stringResource(if (clip.saved) R.string.filter_saved else R.string.save_clip), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Button({ confirmDelete = true }, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.surfaceContainerHighest, contentColor = cs.error)) {
                    Text(stringResource(R.string.delete_clip), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = cs.surfaceContainerHigh,
            title = { Text(stringResource(R.string.delete_confirm), fontSize = 22.sp, fontWeight = FontWeight.Bold) },
            confirmButton = { TextButton({ confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete_clip), color = cs.error, fontSize = 18.sp) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.cancel), fontSize = 18.sp) } },
        )
    }
}
