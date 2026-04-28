package app.clothescast.ui.settings

import app.clothescast.diag.DiagLog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.clothescast.R
import app.clothescast.core.domain.model.TtsEngine
import app.clothescast.core.domain.model.VoiceLocale
import app.clothescast.tts.ELEVENLABS_VOICES
import app.clothescast.tts.ElevenLabsTtsSpeaker
import app.clothescast.tts.GEMINI_VOICES
import app.clothescast.tts.GeminiTtsSpeaker
import app.clothescast.tts.OPENAI_VOICES
import app.clothescast.tts.OpenAITtsSpeaker
import app.clothescast.tts.TtsVoiceOption
import app.clothescast.tts.filterByVariant
import app.clothescast.tts.resolve
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun VoiceContent(
    selected: TtsEngine,
    geminiVoice: String,
    openAiVoice: String,
    elevenLabsVoice: String,
    geminiKeyConfigured: Boolean,
    openAiKeyConfigured: Boolean,
    elevenLabsKeyConfigured: Boolean,
    voiceLocale: VoiceLocale,
    padding: PaddingValues,
    onSetTtsEngine: (TtsEngine) -> Unit,
    onSetGeminiVoice: (String) -> Unit,
    onSetOpenAiVoice: (String) -> Unit,
    onSetElevenLabsVoice: (String) -> Unit,
    onSetVoiceLocale: (VoiceLocale) -> Unit,
    onSetGeminiKey: (String) -> Unit,
    onSetOpenAiKey: (String) -> Unit,
    onSetElevenLabsKey: (String) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // While a preview is in flight (synthesis + playback) we lock the whole
    // voice section. Cloud TTS calls are billed per-character and the in-flight
    // request can't reliably be un-billed by client-side cancellation, so the
    // safe behaviour is to ignore further triggers — engine taps, voice taps,
    // locale taps, the Test Voice button — until the current preview finishes.
    // Compose callbacks run on the main thread, so the read-then-write here is
    // atomic with respect to other UI events.
    var isPreviewing by remember { mutableStateOf(false) }

    fun preview(engine: TtsEngine, gVoice: String, oVoice: String, eVoice: String, locale: VoiceLocale) {
        if (isPreviewing) return
        isPreviewing = true
        coroutineScope.launch {
            try {
                runTtsPreview(context, engine, gVoice, oVoice, eVoice, locale)
            } finally {
                isPreviewing = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Locale lives outside the engine card now: it drives the OpenAI default voice
        // (en-GB → fable, else nova) as well as the device engine, so users on any
        // engine can pick it without first switching engine.
        SectionCard(title = stringResource(R.string.settings_tts_voice_locale_label)) {
            VoiceLocalePicker(
                selected = voiceLocale,
                enabled = !isPreviewing,
                onSelect = {
                    onSetVoiceLocale(it)
                    preview(selected, geminiVoice, openAiVoice, elevenLabsVoice, it)
                },
            )
        }

        SectionCard(title = stringResource(R.string.settings_tts_engine_title)) {
            Text(
                text = stringResource(R.string.settings_tts_engine_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TtsEngine.entries.forEach { engine ->
                RadioRow(
                    label = stringResource(ttsEngineLabel(engine)),
                    selected = engine == selected,
                    enabled = !isPreviewing,
                    onSelect = {
                        onSetTtsEngine(engine)
                        preview(engine, geminiVoice, openAiVoice, elevenLabsVoice, voiceLocale)
                    },
                )
            }
            when (selected) {
                TtsEngine.GEMINI -> {
                    if (!geminiKeyConfigured) {
                        MissingKeyHint(
                            engineName = stringResource(R.string.settings_api_key_gemini_label),
                            placeholder = stringResource(R.string.settings_api_key_placeholder),
                            onSaveKey = onSetGeminiKey,
                        )
                    }
                    VoicePicker(
                        title = stringResource(R.string.settings_tts_voice_label),
                        voices = GEMINI_VOICES,
                        selectedId = geminiVoice,
                        enabled = !isPreviewing,
                        onSelect = {
                            onSetGeminiVoice(it)
                            preview(TtsEngine.GEMINI, it, openAiVoice, elevenLabsVoice, voiceLocale)
                        },
                    )
                    TestVoiceButton(isPreviewing = isPreviewing) {
                        preview(selected, geminiVoice, openAiVoice, elevenLabsVoice, voiceLocale)
                    }
                }
                TtsEngine.OPENAI -> {
                    if (!openAiKeyConfigured) {
                        MissingKeyHint(
                            engineName = stringResource(R.string.settings_api_key_openai_label),
                            placeholder = stringResource(R.string.settings_openai_key_placeholder),
                            onSaveKey = onSetOpenAiKey,
                        )
                    }
                    VoicePicker(
                        title = stringResource(R.string.settings_tts_voice_label),
                        voices = OPENAI_VOICES.filterByVariant(voiceLocale, keepSelected = openAiVoice),
                        selectedId = openAiVoice,
                        enabled = !isPreviewing,
                        onSelect = {
                            onSetOpenAiVoice(it)
                            preview(TtsEngine.OPENAI, geminiVoice, it, elevenLabsVoice, voiceLocale)
                        },
                    )
                    TestVoiceButton(isPreviewing = isPreviewing) {
                        preview(selected, geminiVoice, openAiVoice, elevenLabsVoice, voiceLocale)
                    }
                }
                TtsEngine.ELEVENLABS -> {
                    if (!elevenLabsKeyConfigured) {
                        MissingKeyHint(
                            engineName = stringResource(R.string.settings_api_key_elevenlabs_label),
                            placeholder = stringResource(R.string.settings_elevenlabs_key_placeholder),
                            onSaveKey = onSetElevenLabsKey,
                        )
                    }
                    VoicePicker(
                        title = stringResource(R.string.settings_tts_voice_label),
                        voices = ELEVENLABS_VOICES.filterByVariant(voiceLocale),
                        selectedId = elevenLabsVoice,
                        enabled = !isPreviewing,
                        onSelect = {
                            onSetElevenLabsVoice(it)
                            preview(TtsEngine.ELEVENLABS, geminiVoice, openAiVoice, it, voiceLocale)
                        },
                    )
                    TestVoiceButton(isPreviewing = isPreviewing) {
                        preview(selected, geminiVoice, openAiVoice, elevenLabsVoice, voiceLocale)
                    }
                }
                TtsEngine.DEVICE -> {
                    TestVoiceButton(isPreviewing = isPreviewing) {
                        preview(selected, geminiVoice, openAiVoice, elevenLabsVoice, voiceLocale)
                    }
                }
            }
        }
    }
}

/**
 * Inline hint shown next to the engine picker when the chosen engine's key isn't
 * configured. Tapping "Add API key" opens a paste dialog so the user can enter
 * the key without leaving the Voice screen — the same key that the API keys
 * page would store.
 */
@Composable
private fun MissingKeyHint(
    engineName: String,
    placeholder: String,
    onSaveKey: (String) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    Text(
        text = stringResource(R.string.settings_tts_no_key_hint, engineName),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
    Button(
        onClick = { dialogOpen = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.settings_tts_add_api_key))
    }
    if (dialogOpen) {
        AddApiKeyDialog(
            engineName = engineName,
            placeholder = placeholder,
            onSave = {
                onSaveKey(it)
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false },
        )
    }
}

@Composable
private fun AddApiKeyDialog(
    engineName: String,
    placeholder: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_tts_add_api_key_title, engineName)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                visualTransformation = if (input.isEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                placeholder = { Text(placeholder) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = input.trim()
                    if (trimmed.isNotEmpty()) onSave(trimmed)
                },
                enabled = input.isNotBlank(),
            ) {
                Text(stringResource(R.string.settings_tts_add_api_key_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_tts_add_api_key_cancel))
            }
        },
    )
}

@Composable
private fun VoiceLocalePicker(
    selected: VoiceLocale,
    onSelect: (VoiceLocale) -> Unit,
    enabled: Boolean = true,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    val title = stringResource(R.string.settings_tts_voice_locale_label)
    OutlinedButton(
        onClick = { dialogOpen = true },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("$title: ${stringResource(voiceLocaleLabel(selected))}")
    }
    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(title) },
            text = {
                Column {
                    VoiceLocale.entries.forEach { option ->
                        RadioRow(
                            label = stringResource(voiceLocaleLabel(option)),
                            selected = option == selected,
                            onSelect = {
                                onSelect(option)
                                dialogOpen = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text(stringResource(R.string.settings_tts_voice_dismiss))
                }
            },
        )
    }
}

private fun voiceLocaleLabel(locale: VoiceLocale): Int = when (locale) {
    VoiceLocale.SYSTEM -> R.string.settings_tts_voice_locale_system
    VoiceLocale.EN_US -> R.string.settings_tts_voice_locale_en_us
    VoiceLocale.EN_GB -> R.string.settings_tts_voice_locale_en_gb
    VoiceLocale.EN_AU -> R.string.settings_tts_voice_locale_en_au
    VoiceLocale.EN_CA -> R.string.settings_tts_voice_locale_en_ca
    VoiceLocale.DE_DE -> R.string.settings_tts_voice_locale_de_de
    VoiceLocale.FR_FR -> R.string.settings_tts_voice_locale_fr_fr
    VoiceLocale.FR_CA -> R.string.settings_tts_voice_locale_fr_ca
    VoiceLocale.IT_IT -> R.string.settings_tts_voice_locale_it_it
    VoiceLocale.ES_ES -> R.string.settings_tts_voice_locale_es_es
    VoiceLocale.ES_MX -> R.string.settings_tts_voice_locale_es_mx
    VoiceLocale.RU_RU -> R.string.settings_tts_voice_locale_ru_ru
    VoiceLocale.PL_PL -> R.string.settings_tts_voice_locale_pl_pl
    VoiceLocale.HR_HR -> R.string.settings_tts_voice_locale_hr_hr
    VoiceLocale.UK_UA -> R.string.settings_tts_voice_locale_uk_ua
    VoiceLocale.PT_BR -> R.string.settings_tts_voice_locale_pt_br
    VoiceLocale.NL_NL -> R.string.settings_tts_voice_locale_nl_nl
    VoiceLocale.SV_SE -> R.string.settings_tts_voice_locale_sv_se
    VoiceLocale.TR_TR -> R.string.settings_tts_voice_locale_tr_tr
    VoiceLocale.EN_ZA -> R.string.settings_tts_voice_locale_en_za
    VoiceLocale.ID_ID -> R.string.settings_tts_voice_locale_id_id
    VoiceLocale.FIL_PH -> R.string.settings_tts_voice_locale_fil_ph
    VoiceLocale.VI_VN -> R.string.settings_tts_voice_locale_vi_vn
    VoiceLocale.ZH_CN -> R.string.settings_tts_voice_locale_zh_cn
    VoiceLocale.HI_IN -> R.string.settings_tts_voice_locale_hi_in
    VoiceLocale.BN_BD -> R.string.settings_tts_voice_locale_bn_bd
    VoiceLocale.JA_JP -> R.string.settings_tts_voice_locale_ja_jp
    VoiceLocale.KO_KR -> R.string.settings_tts_voice_locale_ko_kr
    VoiceLocale.AR_SA -> R.string.settings_tts_voice_locale_ar_sa
    VoiceLocale.HE_IL -> R.string.settings_tts_voice_locale_he_il
    VoiceLocale.FA_IR -> R.string.settings_tts_voice_locale_fa_ir
}

@Composable
private fun VoicePicker(
    title: String,
    voices: List<TtsVoiceOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    enabled: Boolean = true,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    val current = voices.firstOrNull { it.id == selectedId }
    OutlinedButton(
        onClick = { dialogOpen = true },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("$title: ${current?.displayName ?: selectedId}")
    }
    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(title) },
            text = {
                Column {
                    voices.forEach { option ->
                        RadioRow(
                            label = option.displayName,
                            selected = option.id == selectedId,
                            onSelect = {
                                onSelect(option.id)
                                dialogOpen = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text(stringResource(R.string.settings_tts_voice_dismiss))
                }
            },
        )
    }
}

@Composable
private fun TestVoiceButton(isPreviewing: Boolean, onClick: () -> Unit) {
    // Disabled while previewing — billed cloud TTS calls can't be reliably
    // un-billed once dispatched, and the in-flight playback is already what
    // a fresh tap would re-trigger. Spinner replaces the label so the user
    // sees the click was registered and something is happening.
    Button(
        onClick = onClick,
        enabled = !isPreviewing,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isPreviewing) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        } else {
            Text(stringResource(R.string.settings_tts_test))
        }
    }
}

/**
 * Plays a preview through the chosen engine + voice. Uses a fixed weather-y
 * sample (rather than the latest cached prose) so every preview tap exercises
 * the brand name's pronunciation and costs the same number of BYOK tokens —
 * making it easier to compare engines and voices on identical words.
 *
 * Errors are surfaced as a Toast so the user can see *why* the voice failed
 * (most often: missing or wrong API key for the chosen provider).
 */
private suspend fun runTtsPreview(
    context: android.content.Context,
    engine: TtsEngine,
    geminiVoice: String,
    openAiVoice: String,
    elevenLabsVoice: String,
    voiceLocale: VoiceLocale,
) {
    val app = context.applicationContext as app.clothescast.ClothesCastApplication
    // Network synthesis and AudioTrack write are both blocking-ish work — Ktor
    // suspends off-Main internally, but AudioTrack.write/play are JNI calls and
    // we don't want a hot stack of preview work running on the UI dispatcher.
    withContext(Dispatchers.IO) {
        val locale = voiceLocale.resolve()
        val text = context.getString(R.string.settings_tts_test_sample)
        try {
            when (engine) {
                TtsEngine.GEMINI ->
                    GeminiTtsSpeaker(app.geminiTtsClient, voiceName = geminiVoice).speak(text, locale)
                TtsEngine.OPENAI ->
                    OpenAITtsSpeaker(app.openAiTtsClient, voice = openAiVoice).speak(text, locale)
                TtsEngine.ELEVENLABS ->
                    ElevenLabsTtsSpeaker(app.elevenLabsTtsClient, voiceId = elevenLabsVoice).speak(text, locale)
                TtsEngine.DEVICE ->
                    app.deviceTtsSpeaker.speak(text, locale)
            }
        } catch (_: CancellationException) {
            // Composable scope cancelled (user navigated away mid-preview); not an error.
        } catch (t: Throwable) {
            // TTS exceptions already name their provider in the message
            // (e.g. "Gemini TTS HTTP 400: …"); don't double that up.
            val message = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
            DiagLog.w("VoiceSettings", "TTS preview failed for $engine", t)
            // Toast.show() posts internally, but Toast.makeText()'s constructor needs
            // a Looper on the calling thread — Dispatchers.IO has none, so hop to Main.
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            }
            // Fall back to the on-device engine so the user still hears the preview
            // and can confirm audio output is working — mirrors FetchAndNotifyWorker.
            if (engine != TtsEngine.DEVICE) {
                try {
                    app.deviceTtsSpeaker.speak(text, locale)
                } catch (_: CancellationException) {
                    // user moved on; fine
                } catch (fallback: Throwable) {
                    DiagLog.w("VoiceSettings", "Device TTS fallback also failed", fallback)
                }
            }
        }
    }
}

private fun ttsEngineLabel(engine: TtsEngine): Int = when (engine) {
    TtsEngine.DEVICE -> R.string.settings_tts_engine_device
    TtsEngine.GEMINI -> R.string.settings_tts_engine_gemini
    TtsEngine.OPENAI -> R.string.settings_tts_engine_openai
    TtsEngine.ELEVENLABS -> R.string.settings_tts_engine_elevenlabs
}
