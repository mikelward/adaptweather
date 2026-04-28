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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.clothescast.R
import app.clothescast.core.domain.model.BandClause
import app.clothescast.core.domain.model.ClothesClause
import app.clothescast.core.domain.model.DeltaClause
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.InsightSummary
import app.clothescast.core.domain.model.TemperatureBand
import app.clothescast.core.domain.model.TtsEngine
import app.clothescast.core.domain.model.VoiceLocale
import app.clothescast.insight.InsightFormatter
import app.clothescast.tts.DeviceVoice
import app.clothescast.tts.ELEVENLABS_VOICES
import app.clothescast.tts.ElevenLabsTtsSpeaker
import app.clothescast.tts.GEMINI_VOICES
import app.clothescast.tts.GOOGLE_TTS_PACKAGE
import app.clothescast.tts.GeminiTtsSpeaker
import app.clothescast.tts.OPENAI_VOICES
import app.clothescast.tts.OpenAITtsSpeaker
import app.clothescast.tts.TtsVoiceOption
import app.clothescast.tts.filterByVariant
import app.clothescast.tts.resolve
import app.clothescast.tts.withSpeechAudioFocus
import java.text.Collator
import java.util.Locale
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
    deviceVoice: String?,
    deviceVoices: List<DeviceVoice>,
    effectiveDeviceVoice: DeviceVoice?,
    geminiKeyConfigured: Boolean,
    openAiKeyConfigured: Boolean,
    elevenLabsKeyConfigured: Boolean,
    voiceLocale: VoiceLocale,
    padding: PaddingValues,
    onSetTtsEngine: (TtsEngine) -> Unit,
    onSetGeminiVoice: (String) -> Unit,
    onSetOpenAiVoice: (String) -> Unit,
    onSetElevenLabsVoice: (String) -> Unit,
    onSetDeviceVoice: (String?) -> Unit,
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

    fun preview(engine: TtsEngine, gVoice: String, oVoice: String, eVoice: String, dVoice: String?, locale: VoiceLocale) {
        if (isPreviewing) return
        isPreviewing = true
        coroutineScope.launch {
            try {
                runTtsPreview(context, engine, gVoice, oVoice, eVoice, dVoice, locale)
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
            Text(
                text = stringResource(R.string.settings_tts_voice_locale_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VoiceLocalePicker(
                selected = voiceLocale,
                enabled = !isPreviewing,
                onSelect = {
                    onSetVoiceLocale(it)
                    preview(selected, geminiVoice, openAiVoice, elevenLabsVoice, deviceVoice, it)
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
                        preview(engine, geminiVoice, openAiVoice, elevenLabsVoice, deviceVoice, voiceLocale)
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
                            preview(TtsEngine.GEMINI, it, openAiVoice, elevenLabsVoice, deviceVoice, voiceLocale)
                        },
                    )
                    TestVoiceButton(isPreviewing = isPreviewing) {
                        preview(selected, geminiVoice, openAiVoice, elevenLabsVoice, deviceVoice, voiceLocale)
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
                            preview(TtsEngine.OPENAI, geminiVoice, it, elevenLabsVoice, deviceVoice, voiceLocale)
                        },
                    )
                    TestVoiceButton(isPreviewing = isPreviewing) {
                        preview(selected, geminiVoice, openAiVoice, elevenLabsVoice, deviceVoice, voiceLocale)
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
                            preview(TtsEngine.ELEVENLABS, geminiVoice, openAiVoice, it, deviceVoice, voiceLocale)
                        },
                    )
                    TestVoiceButton(isPreviewing = isPreviewing) {
                        preview(selected, geminiVoice, openAiVoice, elevenLabsVoice, deviceVoice, voiceLocale)
                    }
                }
                TtsEngine.DEVICE -> {
                    if (!rememberIsGoogleTtsInstalled()) {
                        InstallGoogleTtsHint()
                    }
                    DeviceVoicePicker(
                        voices = deviceVoices,
                        selectedId = deviceVoice,
                        effectiveDeviceVoice = effectiveDeviceVoice,
                        enabled = !isPreviewing,
                        onSelect = { picked ->
                            onSetDeviceVoice(picked)
                            preview(selected, geminiVoice, openAiVoice, elevenLabsVoice, picked, voiceLocale)
                        },
                    )
                    TestVoiceButton(isPreviewing = isPreviewing) {
                        preview(selected, geminiVoice, openAiVoice, elevenLabsVoice, deviceVoice, voiceLocale)
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
    // Mirrors RegionSettings: append the resolved phone-locale tag to the
    // "Follow phone language" entry so the user can see what System actually
    // means on their device (e.g. en-GB) without leaving the screen.
    val systemTag = remember { Locale.getDefault().toLanguageTag() }
    val labelFor: @Composable (VoiceLocale) -> String = { option ->
        val base = stringResource(voiceLocaleLabel(option))
        if (option == VoiceLocale.SYSTEM) "$base ($systemTag)" else base
    }
    OutlinedButton(
        onClick = { dialogOpen = true },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("$title: ${labelFor(selected)}")
    }
    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(title) },
            text = {
                // Sort by the resolved display label using a locale-aware
                // collator (so e.g. "Ç" sorts with "C" in fr-FR, "Ä" with "A"
                // in de-DE). SYSTEM stays pinned at the top — it's a "follow
                // device" option, not a language to sort with the rest.
                val collator = remember { Collator.getInstance(Locale.getDefault()) }
                val (system, rest) = VoiceLocale.entries
                    .map { it to labelFor(it) }
                    .partition { it.first == VoiceLocale.SYSTEM }
                val sorted = system + rest.sortedWith(compareBy(collator) { it.second })
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    sorted.forEach { (option, label) ->
                        RadioRow(
                            label = label,
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
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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

/**
 * Voice picker for the on-device TTS engine. Wraps the generic [VoicePicker]
 * with two extras specific to the device path:
 *
 *  - An "Auto" entry at the top that maps to a `null` voice ID — meaning
 *    "let [app.clothescast.tts.AndroidTtsSpeaker] auto-pick the highest-
 *    quality voice for the locale." This is the default for installs that
 *    haven't opened the picker.
 *  - A "Currently:" line below the picker showing what would actually be
 *    spoken right now — the user's pin if set, else the auto-pick. Lets
 *    users see their selection without first hitting Test.
 *
 * Voices come from [app.clothescast.tts.AndroidTtsVoiceEnumerator] via the
 * Settings ViewModel; an empty list means we're still enumerating (or the
 * device has no voices at all).
 */
@Composable
private fun DeviceVoicePicker(
    voices: List<DeviceVoice>,
    selectedId: String?,
    effectiveDeviceVoice: DeviceVoice?,
    onSelect: (String?) -> Unit,
    enabled: Boolean = true,
) {
    val autoLabel = stringResource(R.string.settings_tts_device_voice_auto)
    val networkTag = stringResource(R.string.settings_tts_device_voice_network)
    val offlineTag = stringResource(R.string.settings_tts_device_voice_offline)
    val tagFor: (DeviceVoice) -> String = { if (it.isNetworkRequired) networkTag else offlineTag }
    val labelFor: (DeviceVoice) -> String = { "${it.id} · ${tagFor(it)} · q${it.quality}" }
    val voiceOptions = listOf(TtsVoiceOption(DEVICE_VOICE_AUTO_ID, autoLabel)) +
        voices.map { TtsVoiceOption(it.id, labelFor(it)) }
    VoicePicker(
        title = stringResource(R.string.settings_tts_device_voice_label),
        voices = voiceOptions,
        selectedId = selectedId ?: DEVICE_VOICE_AUTO_ID,
        enabled = enabled,
        onSelect = { id -> onSelect(id.takeIf { it != DEVICE_VOICE_AUTO_ID }) },
    )
    val currently = effectiveDeviceVoice?.let(labelFor)
        ?: stringResource(R.string.settings_tts_device_voice_currently_loading)
    Text(
        text = stringResource(R.string.settings_tts_device_voice_currently, currently),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val DEVICE_VOICE_AUTO_ID = "__auto__"

/**
 * Tracks whether `com.google.android.tts` is currently installed, refreshing
 * on every `ON_RESUME`. Lives in the composable layer (rather than the
 * SettingsViewModel) because the ViewModel instance is reused across
 * Settings entries — a one-shot package check at construction time would
 * never refresh after the user installs Google TTS via the CTA below and
 * returns to the app. The check itself is a cheap sync PackageManager
 * call (~1ms) so there's no need to push it off the main thread.
 */
@Composable
private fun rememberIsGoogleTtsInstalled(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var installed by remember { mutableStateOf(checkGoogleTtsInstalled(context)) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                installed = checkGoogleTtsInstalled(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return installed
}

private fun checkGoogleTtsInstalled(context: android.content.Context): Boolean = runCatching {
    context.packageManager.getPackageInfo(GOOGLE_TTS_PACKAGE, 0)
}.isSuccess

/**
 * Banner shown when "Speech Services by Google" isn't installed. The vendor
 * default TTS engine on most non-Pixel devices sounds notably worse than
 * Google's, and the install fixes that with one tap. Hidden once Google's
 * engine is detected.
 */
@Composable
private fun InstallGoogleTtsHint() {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.settings_tts_install_google_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        onClick = {
            // ACTION_VIEW with market:// goes to Play Store if installed,
            // falls back gracefully on Aurora/Sideload installs that handle
            // the scheme. We don't add a https-fallback here because users
            // without any market app installed are extremely unlikely to be
            // installing TTS engines anyway.
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("market://details?id=$GOOGLE_TTS_PACKAGE"),
            )
            runCatching { context.startActivity(intent) }
                .onFailure { DiagLog.w("VoiceSettings", "Couldn't open Play Store for Google TTS install", it) }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.settings_tts_install_google_button))
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
 * Plays a preview through the chosen engine + voice. Builds a fixed canned
 * [InsightSummary] and renders it through the same [InsightFormatter] the
 * real briefing pipeline uses, so the preview reads exactly like a real
 * morning briefing in the chosen voice locale — no separate per-locale
 * sample string to keep in sync with the prose templates. The summary is
 * deliberately broad enough to exercise band + delta + multi-item clothes
 * clauses on every preview tap, and costs the same number of BYOK tokens
 * each time so engines / voices compare on identical words.
 *
 * The sample is rendered in the selected *voice locale* so the preview
 * matches the accent and language the user is auditioning, not the app's
 * UI locale. Brand-prefix framing ("Today's ClothesCast: …") is omitted
 * for now — see TODO(brand-intro) in [FetchAndNotifyWorker.formatProse].
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
    deviceVoice: String?,
    voiceLocale: VoiceLocale,
) {
    val app = context.applicationContext as app.clothescast.ClothesCastApplication
    // Network synthesis and AudioTrack write are both blocking-ish work — Ktor
    // suspends off-Main internally, but AudioTrack.write/play are JNI calls and
    // we don't want a hot stack of preview work running on the UI dispatcher.
    withContext(Dispatchers.IO) {
        val locale = voiceLocale.resolve()
        // Render the canned sample in the *voice* locale, not the app's UI
        // locale — playing English prose through a Japanese voice is exactly
        // the mismatch the user picked the locale to avoid. The formatter
        // resolves to values/strings.xml for any locale without an override.
        val text = InsightFormatter.forContext(context, locale).format(SAMPLE_SUMMARY)
        withSpeechAudioFocus(context) {
            try {
                when (engine) {
                    TtsEngine.GEMINI ->
                        GeminiTtsSpeaker(app.geminiTtsClient, voiceName = geminiVoice).speak(text, locale)
                    TtsEngine.OPENAI ->
                        OpenAITtsSpeaker(app.openAiTtsClient, voice = openAiVoice).speak(text, locale)
                    TtsEngine.ELEVENLABS ->
                        ElevenLabsTtsSpeaker(app.elevenLabsTtsClient, voiceId = elevenLabsVoice).speak(text, locale)
                    TtsEngine.DEVICE ->
                        app.deviceTtsSpeaker(deviceVoice).speak(text, locale)
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
                        app.deviceTtsSpeaker(deviceVoice).speak(text, locale)
                    } catch (_: CancellationException) {
                        // user moved on; fine
                    } catch (fallback: Throwable) {
                        DiagLog.w("VoiceSettings", "Device TTS fallback also failed", fallback)
                    }
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

// Canned forecast for the voice preview: a cold day 7° down on yesterday with
// sweater + jacket as the clothes pick. Renders (en-US) as "Today will be cold.
// It will be 7° cooler than yesterday. Wear a sweater and jacket." — exercises
// band, delta, and a multi-item clothes clause without dragging in a precip /
// tie-in time that would force a different number of TTS tokens per locale.
private val SAMPLE_SUMMARY = InsightSummary(
    period = ForecastPeriod.TODAY,
    band = BandClause(TemperatureBand.COLD, TemperatureBand.COLD),
    delta = DeltaClause(degrees = 7, direction = DeltaClause.Direction.COOLER),
    clothes = ClothesClause(items = listOf("sweater", "jacket")),
)
