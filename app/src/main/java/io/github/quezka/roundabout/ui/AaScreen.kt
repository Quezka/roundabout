package io.github.quezka.roundabout.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.quezka.roundabout.R
import io.github.quezka.roundabout.aa.AaClient
import io.github.quezka.roundabout.aa.AaDiscovery
import io.github.quezka.roundabout.aa.AaInput
import io.github.quezka.roundabout.aa.AaMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AaScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val client = remember { AaClient(ctx.applicationContext) }
    var phase by remember { mutableStateOf(client.phase) }
    var detail by remember { mutableStateOf(client.detail) }
    val prefs = remember { ctx.getSharedPreferences("aa", android.content.Context.MODE_PRIVATE) }
    var host by rememberSaveable { mutableStateOf(prefs.getString("host", "192.168.43.1") ?: "192.168.43.1") }
    var port by rememberSaveable { mutableStateOf(prefs.getString("port", "5277") ?: "5277") }
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }
    var finding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        while (true) {
            phase = client.phase
            detail = client.detail
            delay(400)
        }
    }
    DisposableEffect(Unit) { onDispose { client.disconnect() } }

    Box(Modifier.fillMaxSize().background(Color.Black).padding(start = Dash.RAIL_DP.dp)) {
        AndroidView(
            factory = { c ->
                SurfaceView(c).apply {
                    this.holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) { surfaceHolder = h }
                        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) { surfaceHolder = h }
                        override fun surfaceDestroyed(h: SurfaceHolder) { surfaceHolder = null }
                    })
                }
            },
            modifier = Modifier.fillMaxSize().testTag("aa_surface"),
        )

        if (phase == AaClient.Phase.RUNNING) {
            Box(
                Modifier.fillMaxSize().pointerInput(phase) {
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: continue
                            if (!ch.pressed && !ch.previousPressed) continue
                            val sx = AaMessages.Video.W / size.width
                            val sy = AaMessages.Video.H / size.height
                            val x = (ch.position.x * sx).toInt().coerceIn(0, AaMessages.Video.W - 1)
                            val y = (ch.position.y * sy).toInt().coerceIn(0, AaMessages.Video.H - 1)
                            val action = when {
                                ch.pressed && !ch.previousPressed -> AaInput.Action.DOWN
                                !ch.pressed && ch.previousPressed -> AaInput.Action.UP
                                else -> AaInput.Action.MOVED
                            }
                            client.touch(x, y, action)
                            ch.consume()
                        }
                    }
                }.testTag("aa_touch"),
            )
        }

        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val back = stringResource(R.string.back)
                FilledTonalIconButton(
                    onClick = { client.disconnect(); onBack() },
                    modifier = Modifier.size(52.dp).testTag("aa_back").semantics { contentDescription = back },
                    shape = RoundedCornerShape(18.dp),
                ) { GlyphIcon(Glyph.BACK, cs.onSecondaryContainer, 24.dp) }
                Spacer(Modifier.width(14.dp))
                Surface(
                    color = cs.surfaceContainerHigh.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = "${phase.name.lowercase()}${if (detail.isNotEmpty()) " · $detail" else ""}",
                        color = cs.onSurface,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).testTag("aa_status"),
                    )
                }
                if (phase == AaClient.Phase.RUNNING || phase == AaClient.Phase.HANDSHAKE) {
                    Spacer(Modifier.width(10.dp))
                    FilledTonalButton(
                        onClick = { client.disconnect() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(40.dp).testTag("aa_disconnect"),
                    ) { Text(stringResource(R.string.aa_disconnect), fontSize = 14.sp) }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (phase != AaClient.Phase.RUNNING && phase != AaClient.Phase.HANDSHAKE) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text(stringResource(R.string.aa_host), fontSize = 12.sp) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.width(220.dp).testTag("aa_host"),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text(stringResource(R.string.aa_port), fontSize = 12.sp) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.width(120.dp).testTag("aa_port"),
                    )
                    FilledTonalButton(
                        onClick = {
                            val h = surfaceHolder?.surface
                            val p = port.toIntOrNull() ?: 5277
                            val dark = cs.background.luminance() < 0.5f
                            prefs.edit().putString("host", host).putString("port", port).apply()
                            if (h != null) client.connectWireless(host, p, h, night = dark)
                        },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.height(56.dp).testTag("aa_connect"),
                    ) { Text(stringResource(R.string.aa_connect), fontSize = 16.sp) }
                    FilledTonalButton(
                        onClick = {
                            finding = true
                            scope.launch {
                                val ip = withContext(Dispatchers.IO) { AaDiscovery.find() }
                                if (ip != null) host = ip
                                finding = false
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.height(56.dp).testTag("aa_find"),
                    ) { Text(if (finding) "…" else stringResource(R.string.aa_find), fontSize = 16.sp) }
                    FilledTonalButton(
                        onClick = { client.connectNative() },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.height(56.dp).testTag("aa_native"),
                    ) { Text(stringResource(R.string.aa_native), fontSize = 16.sp) }
                }
            }
        }
    }
}
