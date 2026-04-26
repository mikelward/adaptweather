package app.adaptweather.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.adaptweather.R

@Composable
internal fun ApiKeysContent(
    geminiConfigured: Boolean,
    openAiConfigured: Boolean,
    elevenLabsConfigured: Boolean,
    padding: PaddingValues,
    onSetGeminiKey: (String) -> Unit,
    onClearGeminiKey: () -> Unit,
    onSetOpenAiKey: (String) -> Unit,
    onClearOpenAiKey: () -> Unit,
    onSetElevenLabsKey: (String) -> Unit,
    onClearElevenLabsKey: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = stringResource(R.string.settings_api_keys_title)) {
            Text(
                text = stringResource(R.string.settings_api_keys_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = stringResource(R.string.settings_api_key_gemini_label),
                style = MaterialTheme.typography.titleSmall,
            )
            KeyEntryFields(
                configured = geminiConfigured,
                statusText = stringResource(
                    if (geminiConfigured) R.string.settings_api_key_status_set
                    else R.string.settings_api_key_status_unset,
                ),
                placeholder = stringResource(R.string.settings_api_key_placeholder),
                saveLabel = stringResource(R.string.settings_api_key_save),
                clearLabel = stringResource(R.string.settings_api_key_clear),
                onSave = onSetGeminiKey,
                onClear = onClearGeminiKey,
            )

            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_api_key_openai_label),
                style = MaterialTheme.typography.titleSmall,
            )
            KeyEntryFields(
                configured = openAiConfigured,
                statusText = stringResource(
                    if (openAiConfigured) R.string.settings_openai_key_status_set
                    else R.string.settings_openai_key_status_unset,
                ),
                placeholder = stringResource(R.string.settings_openai_key_placeholder),
                saveLabel = stringResource(R.string.settings_openai_key_save),
                clearLabel = stringResource(R.string.settings_openai_key_clear),
                onSave = onSetOpenAiKey,
                onClear = onClearOpenAiKey,
            )

            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_api_key_elevenlabs_label),
                style = MaterialTheme.typography.titleSmall,
            )
            KeyEntryFields(
                configured = elevenLabsConfigured,
                statusText = stringResource(
                    if (elevenLabsConfigured) R.string.settings_elevenlabs_key_status_set
                    else R.string.settings_elevenlabs_key_status_unset,
                ),
                placeholder = stringResource(R.string.settings_elevenlabs_key_placeholder),
                saveLabel = stringResource(R.string.settings_elevenlabs_key_save),
                clearLabel = stringResource(R.string.settings_elevenlabs_key_clear),
                onSave = onSetElevenLabsKey,
                onClear = onClearElevenLabsKey,
            )
        }
    }
}

/**
 * Status text + password-style input + save/clear buttons for one BYOK API key.
 * Reused by the API-keys settings page (once per provider) and the onboarding
 * screen (Gemini only).
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
    Text(text = statusText, style = MaterialTheme.typography.bodyMedium)

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
