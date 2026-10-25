package com.samaliyan.nimbus.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samaliyan.nimbus.BuildConfig
import com.samaliyan.nimbus.R
import com.samaliyan.nimbus.ui.theme.LocalNimbus
import com.samaliyan.nimbus.update.UpdateState
import com.samaliyan.nimbus.update.Updater

@Composable
fun UpdateDialog() {
    val state by Updater.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val n = LocalNimbus.current

    when (val s = state) {
        UpdateState.Idle -> Unit
        UpdateState.Checking -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text(stringResource(R.string.update_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.update_checking))
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { Updater.dismiss() }) { Text(stringResource(R.string.cancel)) } },
        )
        UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text(stringResource(R.string.update_title)) },
            text = { Text(stringResource(R.string.update_none, BuildConfig.VERSION_NAME)) },
            confirmButton = { TextButton(onClick = { Updater.dismiss() }) { Text(stringResource(R.string.ok)) } },
        )
        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text(stringResource(R.string.update_title)) },
            text = { Text(stringResource(R.string.update_failed, s.message)) },
            confirmButton = { TextButton(onClick = { Updater.dismiss() }) { Text(stringResource(R.string.ok)) } },
        )
        is UpdateState.Available, is UpdateState.Downloading, is UpdateState.Ready -> {
            val release = when (s) {
                is UpdateState.Available -> s.release
                is UpdateState.Downloading -> s.release
                is UpdateState.Ready -> s.release
                else -> return
            }
            val downloading = s is UpdateState.Downloading
            AlertDialog(
                onDismissRequest = { if (!downloading) Updater.dismiss() },
                title = { Text(stringResource(R.string.update_available, release.version)) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.update_current, BuildConfig.VERSION_NAME, if (release.size > 0) Format.bytes(context, release.size) else "—"),
                            style = MaterialTheme.typography.bodySmall,
                            color = n.muted,
                        )
                        if (release.notes.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                release.notes.trim(),
                                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                                modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                            )
                        }
                        if (s is UpdateState.Downloading) {
                            Spacer(Modifier.height(16.dp))
                            if (s.total > 0) {
                                LinearProgressIndicator(progress = { (s.done.toFloat() / s.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            } else {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${Format.bytes(context, s.done)} / ${Format.bytes(context, s.total)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = n.muted,
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !downloading,
                        onClick = {
                            when (s) {
                                is UpdateState.Ready -> Updater.install(context, s.file)
                                else -> Updater.startDownload(context, release)
                            }
                        },
                    ) {
                        Text(
                            stringResource(
                                when (s) {
                                    is UpdateState.Ready -> R.string.update_install
                                    is UpdateState.Downloading -> R.string.update_downloading
                                    else -> R.string.update_download
                                }
                            )
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { Updater.dismiss() }) {
                        Text(stringResource(if (downloading) R.string.cancel else R.string.later))
                    }
                },
            )
        }
    }
}
