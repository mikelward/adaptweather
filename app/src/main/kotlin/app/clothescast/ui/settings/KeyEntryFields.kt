package app.clothescast.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Status text + password-style input + save/clear buttons for one BYOK API key.
 * Used inline by the Voice settings screen (once per cloud engine) and by the
 * onboarding screen (Gemini only).
 */
@Composable
internal fun KeyEntryFields(
    configured: Boolean,
    statusText: String,
    placeholder: String,
    saveLabel: String,
    clearLabel: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    LinkifiedText(text = statusText, style = MaterialTheme.typography.bodyMedium)

    var input by remember { mutableStateOf("") }
    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        singleLine = true,
        visualTransformation = if (input.isEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
            onClick = {
                if (input.isNotBlank()) {
                    onSave(input)
                    input = ""
                }
            },
            enabled = input.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(saveLabel) }

        if (configured) {
            TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text(clearLabel)
            }
        }
    }
}
