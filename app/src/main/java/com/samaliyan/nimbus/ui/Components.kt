package com.samaliyan.nimbus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.samaliyan.nimbus.R
import com.samaliyan.nimbus.ui.theme.LocalNimbus

/** A titled group of settings on one surface. */
@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 20.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 28.dp, bottom = 8.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

@Composable
fun RowDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = LocalNimbus.current.hairline)

@Composable
fun SettingRow(
    title: String,
    value: String? = null,
    hint: String? = null,
    enabled: Boolean = true,
    ltrValue: Boolean = false,
    onClick: () -> Unit,
) {
    val n = LocalNimbus.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else n.muted,
        )
        if (!value.isNullOrEmpty()) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDirection = if (ltrValue) TextDirection.Ltr else TextDirection.Content
                ),
                color = if (enabled) MaterialTheme.colorScheme.primary else n.muted,
            )
        }
        if (!hint.isNullOrEmpty()) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = n.muted)
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, hint: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val n = LocalNimbus.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else n.muted)
            if (!hint.isNullOrEmpty()) Text(hint, style = MaterialTheme.typography.bodySmall, color = n.muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

data class Choice<T>(val value: T, val label: String, val hint: String? = null)

@Composable
fun <T> ChoiceDialog(title: String, choices: List<Choice<T>>, selected: T, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = c.value == selected, role = Role.RadioButton) { onPick(c.value); onDismiss() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = c.value == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(c.label, style = MaterialTheme.typography.bodyLarge)
                            c.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalNimbus.current.muted) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A text setting edited in a dialog; Save stays off until the value passes [valid]. */
@Composable
fun TextDialog(
    title: String,
    initial: String,
    hint: String? = null,
    placeholder: String? = null,
    numeric: Boolean = false,
    valid: (String) -> Boolean,
    errorText: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val ok = valid(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                hint?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = LocalNimbus.current.muted)
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = asciiDigits(it) },
                    singleLine = true,
                    isError = !ok,
                    placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr)) } },
                    supportingText = if (!ok) ({ Text(errorText) }) else null,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                    keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Ascii, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()); onDismiss() }, enabled = ok) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Persian and Arabic keyboards type their own digits; the core needs ASCII ones. */
fun asciiDigits(text: String): String = buildString(text.length) {
    for (c in text) append(
        when (c) {
            in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
            in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
            else -> c
        }
    )
}
