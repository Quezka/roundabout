package io.github.quezka.roundabout.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.quezka.roundabout.R
import io.github.quezka.roundabout.domain.AppEntry
import io.github.quezka.roundabout.domain.MediaAction
import io.github.quezka.roundabout.domain.MediaState
import io.github.quezka.roundabout.domain.PowerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

typealias IconLoader = (packageName: String, activity: String, sizePx: Int) -> Bitmap?

data class DashUi(
    val time: String,
    val date: String,
    val greeting: String,
    val speedKmh: Int?,
    val power: PowerState?,
    val media: MediaState,
    val mediaAccess: Boolean,
    val navigation: AppEntry?,
    val music: AppEntry?,
    val favorites: List<AppEntry>,
    val player: AppEntry?,
)

sealed interface DashIntent {
    data object Navigate : DashIntent
    data object Apps : DashIntent
    data object Light : DashIntent
    data object Settings : DashIntent
    data object GrantMedia : DashIntent
    data object OpenPlayer : DashIntent
    data class Launch(val app: AppEntry) : DashIntent
    data class Media(val action: MediaAction) : DashIntent
}

// android auto layout: rail left + cards right
@Composable
fun DashScreen(ui: DashUi, icon: IconLoader, onIntent: (DashIntent) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxSize().background(cs.surfaceContainerLowest)) {
        Rail(ui, icon, onIntent)
        Row(Modifier.weight(1f).fillMaxHeight().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DriveCard(ui, onIntent, Modifier.weight(0.5f).fillMaxHeight())
            MediaCard(ui, icon, onIntent, Modifier.weight(0.5f).fillMaxHeight())
        }
    }
}

@Composable
private fun Rail(ui: DashUi, icon: IconLoader, onIntent: (DashIntent) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val apps = (listOfNotNull(ui.navigation, ui.music) + ui.favorites).distinctBy { it.packageName }.take(5)
    Column(
        Modifier.width(112.dp).fillMaxHeight().background(cs.surfaceContainerLow).padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(ui.time, color = cs.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("clock"))
        ui.power?.let { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (p.charging) GlyphIcon(Glyph.BOLT, cs.primary, 14.dp)
                Text("${p.percent}%", color = cs.onSurfaceVariant, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            apps.forEach { app ->
                AppBubble(app, icon, 64.dp, Modifier.clickable(onClickLabel = app.label) { onIntent(DashIntent.Launch(app)) }
                    .semantics { contentDescription = app.label })
            }
        }
        RailButton(Glyph.GEAR, stringResource(R.string.settings), filled = false) { onIntent(DashIntent.Settings) }
        Spacer(Modifier.height(12.dp))
        RailButton(Glyph.GRID, stringResource(R.string.apps), filled = true) { onIntent(DashIntent.Apps) }
    }
}

@Composable
private fun RailButton(glyph: Glyph, label: String, filled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val m = Modifier.size(68.dp).semantics { contentDescription = label }
    if (filled) {
        FilledIconButton(onClick, m, shape = RoundedCornerShape(22.dp)) { GlyphIcon(Glyph.GRID, cs.onPrimary, 28.dp) }
    } else {
        FilledTonalIconButton(onClick, m, shape = RoundedCornerShape(22.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = cs.surfaceContainerHigh)) {
            GlyphIcon(glyph, cs.onSurface, 28.dp)
        }
    }
}

@Composable
fun AppBubble(app: AppEntry, icon: IconLoader, size: Dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bmp by produceState<Bitmap?>(null, app.packageName, app.activity, px) {
        value = withContext(Dispatchers.IO) { icon(app.packageName, app.activity, px) }
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size))
        else Box(Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
    }
}

@Composable
private fun DriveCard(ui: DashUi, onIntent: (DashIntent) -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Card(modifier, shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(ui.greeting, color = cs.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(ui.date, color = cs.onSurfaceVariant, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            SpeedGauge(
                ui.speedKmh, stringResource(R.string.speed_unit),
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)
                    .clickable(onClickLabel = stringResource(R.string.light)) { onIntent(DashIntent.Light) },
            )
            Button(
                onClick = { onIntent(DashIntent.Navigate) },
                modifier = Modifier.fillMaxWidth().height(68.dp).testTag("navigate"),
                shape = RoundedCornerShape(34.dp),
            ) {
                GlyphIcon(Glyph.PIN, cs.onPrimary, 26.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    ui.navigation?.let { stringResource(R.string.navigate_with, it.label) } ?: stringResource(R.string.navigate),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MediaCard(ui: DashUi, icon: IconLoader, onIntent: (DashIntent) -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val m = ui.media
    Card(modifier.testTag("media"), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer)) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val player = ui.player ?: ui.music
                Surface(
                    Modifier.size(96.dp).clickable { onIntent(DashIntent.OpenPlayer) },
                    shape = RoundedCornerShape(24.dp), color = cs.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (player != null) AppBubble(player, icon, 64.dp) else GlyphIcon(Glyph.NOTE, cs.primary, 44.dp)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.now_playing), color = cs.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text((ui.player ?: ui.music)?.label.orEmpty(), color = cs.onSurfaceVariant, fontSize = 15.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.height(18.dp))
            Column(Modifier.weight(1f)) {
                when {
                    !ui.mediaAccess -> {
                        Text(stringResource(R.string.media_access_needed), color = cs.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { onIntent(DashIntent.GrantMedia) }, shape = RoundedCornerShape(24.dp)) {
                            Text(stringResource(R.string.grant_access), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    m.hasMedia -> {
                        Text(m.title.orEmpty(), color = cs.onSurface, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("media_title"))
                        Spacer(Modifier.height(4.dp))
                        Text(m.artist ?: stringResource(R.string.unknown_artist), color = cs.onSurfaceVariant, fontSize = 20.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    else -> Text(stringResource(R.string.nothing_playing), color = cs.onSurfaceVariant, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val prevLabel = stringResource(R.string.media_prev)
                val nextLabel = stringResource(R.string.media_next)
                val tonal = IconButtonDefaults.filledTonalIconButtonColors(containerColor = cs.surfaceContainerHighest)
                FilledTonalIconButton({ onIntent(DashIntent.Media(MediaAction.PREVIOUS)) },
                    Modifier.size(76.dp).semantics { contentDescription = prevLabel }, colors = tonal) {
                    GlyphIcon(Glyph.PREVIOUS, cs.onSurface, 30.dp)
                }
                val playLabel = stringResource(if (m.playing) R.string.media_pause else R.string.media_play)
                FilledIconButton({ onIntent(DashIntent.Media(MediaAction.TOGGLE)) },
                    Modifier.size(width = 120.dp, height = 88.dp).semantics { contentDescription = playLabel },
                    shape = RoundedCornerShape(30.dp)) {
                    GlyphIcon(if (m.playing) Glyph.PAUSE else Glyph.PLAY, cs.onPrimary, 36.dp)
                }
                FilledTonalIconButton({ onIntent(DashIntent.Media(MediaAction.NEXT)) },
                    Modifier.size(76.dp).semantics { contentDescription = nextLabel }, colors = tonal) {
                    GlyphIcon(Glyph.NEXT, cs.onSurface, 30.dp)
                }
            }
        }
    }
}
