package io.github.quezka.roundabout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.quezka.roundabout.R
import io.github.quezka.roundabout.domain.Ambient

@Composable
fun SettingsScreen(
    ambient: Ambient,
    version: String,
    onBack: () -> Unit,
    onAmbient: (Ambient) -> Unit,
    onSystemSettings: () -> Unit,
    onNotificationAccess: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(cs.surfaceContainerLowest).padding(horizontal = 28.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(stringResource(R.string.settings), onBack)
        Section {
            Text(stringResource(R.string.ambient_light), color = cs.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Ambient.entries.forEach { a ->
                    val selected = a == ambient
                    Box(
                        Modifier.size(68.dp).clip(CircleShape)
                            .border(if (selected) 4.dp else 0.dp, if (selected) cs.onSurface else Color.Transparent, CircleShape)
                            .padding(if (selected) 8.dp else 4.dp).clip(CircleShape).background(Color(a.argb))
                            .clickable { onAmbient(a) }.testTag("ambient_" + a.name.lowercase()),
                    )
                }
            }
        }
        Section {
            Line(stringResource(R.string.system_settings), onSystemSettings)
            Line(stringResource(R.string.notification_access), onNotificationAccess)
            Text(stringResource(R.string.roles_hint), color = cs.onSurfaceVariant, fontSize = 17.sp, modifier = Modifier.padding(16.dp))
        }
        Text("Roundabout $version", color = cs.outline, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, start = 8.dp))
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { Column(Modifier.padding(20.dp)) { content() } }
}

@Composable
private fun Line(label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(label, fontSize = 21.sp, fontWeight = FontWeight.SemiBold) },
        trailingContent = { GlyphIcon(Glyph.BACK, cs.onSurfaceVariant, 22.dp, Modifier.graphicsLayer { scaleX = -1f }) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
    )
}
