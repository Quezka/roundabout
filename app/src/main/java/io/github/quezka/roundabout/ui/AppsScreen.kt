package io.github.quezka.roundabout.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.quezka.roundabout.R
import io.github.quezka.roundabout.domain.AppEntry
import io.github.quezka.roundabout.domain.Role

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val back = stringResource(R.string.back)
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onBack, Modifier.size(68.dp).testTag("back").semantics { contentDescription = back },
            shape = RoundedCornerShape(22.dp)) {
            GlyphIcon(Glyph.BACK, cs.onSecondaryContainer, 30.dp)
        }
        Spacer(Modifier.width(20.dp))
        Text(title, color = cs.onSurface, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppsScreen(
    apps: List<AppEntry>,
    favorites: List<AppEntry>,
    icon: IconLoader,
    onBack: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onSetRole: (Role, AppEntry) -> Unit,
    onToggleFavorite: (AppEntry) -> Unit,
    onInfo: (AppEntry) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    Column(Modifier.fillMaxSize().background(cs.surfaceContainerLowest).padding(start = (Dash.RAIL_DP + 28).dp, end = 28.dp, top = 20.dp, bottom = 20.dp)) {
        ScreenHeader(stringResource(R.string.apps), onBack)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(136.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).testTag("app_grid"),
        ) {
            items(apps, key = { it.packageName + "/" + it.activity }) { app ->
                Column(
                    Modifier.clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer)
                        .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { menuFor = app })
                        .padding(vertical = 16.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppBubble(app, icon, 68.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(app.label, color = cs.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
    menuFor?.let { app ->
        val pinned = favorites.any { it.packageName == app.packageName }
        AlertDialog(
            onDismissRequest = { menuFor = null },
            containerColor = cs.surfaceContainerHigh,
            title = { Text(app.label, color = cs.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    MenuLine(stringResource(if (pinned) R.string.unpin else R.string.pin)) { onToggleFavorite(app); menuFor = null }
                    MenuLine(stringResource(R.string.use_for_navigation)) { onSetRole(Role.NAVIGATION, app); menuFor = null }
                    MenuLine(stringResource(R.string.use_for_music)) { onSetRole(Role.MUSIC, app); menuFor = null }
                    MenuLine(stringResource(R.string.app_info)) { onInfo(app); menuFor = null }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuFor = null }) {
                    Text(stringResource(R.string.cancel), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

@Composable
private fun MenuLine(label: String, onClick: () -> Unit) {
    Text(
        label, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 16.dp, horizontal = 8.dp),
    )
}
