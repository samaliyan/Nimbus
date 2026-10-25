package com.samaliyan.nimbus.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samaliyan.nimbus.BuildConfig
import com.samaliyan.nimbus.R
import com.samaliyan.nimbus.core.Level
import com.samaliyan.nimbus.core.LogLine
import com.samaliyan.nimbus.core.Tunnel
import com.samaliyan.nimbus.ui.theme.LocalNimbus
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun LogScreen(snackbar: SnackbarHostState) {
    val logs by Tunnel.logs.collectAsStateWithLifecycle()
    var problemsOnly by rememberSaveable { mutableStateOf(false) }
    val shown by remember(logs, problemsOnly) {
        derivedStateOf { if (problemsOnly) logs.filter { it.level == Level.WARN || it.level == Level.ERROR } else logs }
    }
    // Opens at the newest line.
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (shown.size - 1).coerceAtLeast(0))
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Follow new lines only while the reader is already at the bottom.
    val atBottom by remember {
        derivedStateOf {
            val info = list.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(shown.size) {
        if (shown.isNotEmpty() && atBottom) list.scrollToItem(shown.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.tab_log), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                copy(context, render(logs))
                scope.launch { snackbar.showSnackbar(context.getString(R.string.copied)) }
            }, enabled = logs.isNotEmpty()) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.copy))
            }
            IconButton(onClick = {
                val dir = File(context.cacheDir, "logs").apply { mkdirs() }
                val f = File(dir, "nimbus-log.txt").apply { writeText(render(logs)) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        null,
                    )
                )
            }, enabled = logs.isNotEmpty()) {
                Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.share))
            }
            IconButton(onClick = { Tunnel.clearLogs() }, enabled = logs.isNotEmpty()) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.clear))
            }
        }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !problemsOnly, onClick = { problemsOnly = false }, label = { Text(stringResource(R.string.log_all)) })
            FilterChip(selected = problemsOnly, onClick = { problemsOnly = true }, label = { Text(stringResource(R.string.log_problems)) })
        }
        Spacer(Modifier.height(8.dp))

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.log_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalNimbus.current.muted,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            ) {
                // Core output is English; it reads left to right whatever the app's language.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    SelectionContainer {
                        LazyColumn(state = list, contentPadding = PaddingValues(12.dp)) {
                            itemsIndexed(shown) { _, line -> LogRow(line) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(line: LogLine) {
    val n = LocalNimbus.current
    val color = when (line.level) {
        Level.ERROR -> n.error
        Level.WARN -> n.warn
        Level.DEBUG -> n.muted
        Level.INFO -> MaterialTheme.colorScheme.onSurface
    }
    Row(Modifier.padding(vertical = 2.dp)) {
        if (line.time.isNotEmpty()) {
            Text(line.time, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = n.muted, lineHeight = 16.sp)
            Spacer(Modifier.width(8.dp))
        }
        Text(line.message, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = color, lineHeight = 16.sp)
    }
}

private fun render(logs: List<LogLine>): String = buildString {
    appendLine("Nimbus ${BuildConfig.VERSION_NAME} · core ${BuildConfig.CORE_VERSION} · Android ${android.os.Build.VERSION.RELEASE}")
    logs.forEach { l ->
        if (l.time.isNotEmpty()) append(l.time).append("Z ")
        append(l.level.name.padEnd(5)).append(' ').appendLine(l.message)
    }
}
