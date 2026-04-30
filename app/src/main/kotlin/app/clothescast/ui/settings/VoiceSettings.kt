package app.clothescast.ui.settings

import app.clothescast.diag.DiagLog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import app.clothescast.R
import app.clothescast.core.domain.model.BandClause
import app.clothescast.core.domain.model.ClothesClause
import app.clothescast.core.domain.model.DeltaClause
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.InsightSummary
import app.clothescast.core.domain.model.TemperatureBand
import app.clothescast.core.domain.model.Region
import app.clothescast.core.domain.model.TtsEngine
import app.clothescast.core.domain.model.VoiceLocale
import app.clothescast.insight.InsightFormatter
import app.clothescast.core.data.tts.ELEVENLABS_MODEL_FLASH_V2_5
import app.clothescast.core.data.tts.ELEVENLABS_MODEL_MULTILINGUAL_V2
import app.clothescast.core.data.tts.ELEVENLABS_MODEL_TURBO_V2_5
import app.clothescast.core.data.tts.ELEVENLABS_MODEL_V3
import app.clothescast.core.domain.model.UserPreferences
import app.clothescast.tts.DeviceVoice
import app.clothescast.tts.ELEVENLABS_VOICES
import app.clothescast.tts.ElevenLabsTtsSpeaker
import app.clothescast.tts.GEMINI_VOICES
import app.clothescast.tts.GOOGLE_TTS_PACKAGE
import app.clothescast.tts.GeminiTtsSpeaker
import app.clothescast.tts.OPENAI_VOICES
import app.clothescast.tts.OpenAITtsSpeaker
import app.clothescast.tts.LocaleFallbackTier
import app.clothescast.tts.TtsVoiceOption
import app.clothescast.tts.filterByVariant
import app.clothescast.tts.localeFallbackTier
import app.clothescast.tts.resolve
import app.clothescast.tts.toJavaLocale
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
    elevenLabsRefreshedVoices: List<TtsVoiceOption>?,
    elevenLabsRefreshing: Boolean,
    elevenLabsModel: String,
    elevenLabsSpeed: Double,
    elevenLabsStability: Double,
    openAiSpeed: Double,
    voiceLocale: VoiceLocale,
    region: Region,
    padding: PaddingValues,
    onSetTtsEngine: (TtsEngine) -> Unit,
    onSetGeminiVoice: (String) -> Unit,
    onSetOpenAiVoice: (String) -> Unit,
    onSetOpenAiSpeed: (Double) -> Unit,
    onSetElevenLabsVoice: (String) -> Unit,
    onSetDeviceVoice: (String?) -> Unit,
    onSetVoiceLocale: (VoiceLocale) -> Unit,
    onSetGeminiKey: (String) -> Unit,
    onClearGeminiKey: () -> Unit,
    onSetOpenAiKey: (String) -> Unit,
    onClearOpenAiKey: () -> Unit,
    onSetElevenLabsKey: (String) -> Unit,
    onClearElevenLabsKey: () -> Unit,
    onRefreshElevenLabsVoices: () -> Unit,
    onSetElevenLabsModel: (String) -> Unit,
    onSetElevenLabsSpeed: (Double) -> Unit,
    onSetElevenLabsStability: (Double) -> Unit,
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

    // Slider / picker callbacks need to pass the just-released value into
    // `preview` directly: `onSet…` writes asynchronously through DataStore →
    // StateFlow, so the prop hasn't caught up yet when we kick off the
    // preview. Defaulting each override to the current prop means callers
    // that aren't changing that knob can keep passing positional args as
    // before; callers that *are* changing it pass the released value.
    fun preview(
        engine: TtsEngine,
        gVoice: String,
        oVoice: String,
        eVoice: String,
        dVoice: String?,
        locale: VoiceLocale,
        oSpeed: Double = openAiSpeed,
        eSpeed: Double = elevenLabsSpeed,
        eStability: Double = elevenLabsStability,
        eModel: String = elevenLabsModel,
    ) {
        if (isPreviewing) return
        isPreviewing = true
        coroutineScope.launch {
            try {
                runTtsPreview(
                    context = context,
                    engine = engine,
                    geminiVoice = gVoice,
                    openAiVoice = oVoice,
                    openAiSpeed = oSpeed,
                    elevenLabsVoice = eVoice,
                    elevenLabsModel = eModel,
                    elevenLabsSpeed = eSpeed,
                    elevenLabsStability = eStability,
                    deviceVoice = dVoice,
                    voiceLocale = locale,
                    region = region,
                )
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
                region = region,
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
                    KeyEntryFields(
                        configured = geminiKeyConfigured,
                        statusText = stringResource(
                            if (geminiKeyConfigured) R.string.settings_api_key_status_set
                            else R.string.settings_api_key_status_unset,
                        ),
                        placeholder = stringResource(R.string.settings_api_key_placeholder),
                        saveLabel = stringResource(R.string.settings_api_key_save),
                        clearLabel = stringResource(R.string.settings_api_key_clear),
                        onSave = onSetGeminiKey,
                        onClear = onClearGeminiKey,
                    )
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
                    KeyEntryFields(
                        configured = openAiKeyConfigured,
                        statusText = stringResource(
                            if (openAiKeyConfigured) R.string.settings_openai_key_status_set
                            else R.string.settings_openai_key_status_unset,
                        ),
                        placeholder = stringResource(R.string.settings_openai_key_placeholder),
                        saveLabel = stringResource(R.string.settings_openai_key_save),
                        clearLabel = stringResource(R.string.settings_openai_key_clear),
                        onSave = onSetOpenAiKey,
                        onClear = onClearOpenAiKey,
                    )
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
                    LocaleFallbackCaption(
                        source = OPENAI_VOICES,
                        voiceLocale = voiceLocale,
                    )
                    TtsParameterSlider(
                        labelRes = R.string.settings_openai_speed_label,
                        persistedValue = openAiSpeed,
                        min = UserPreferences.MIN_OPENAI_SPEED,
                        max = UserPreferences.MAX_OPENAI_SPEED,
                        step = 0.05,
                        enabled = !isPreviewing,
                        onValueChangeFinished = { released ->
                            onSetOpenAiSpeed(released)
                            preview(
                                TtsEngine.OPENAI, geminiVoice, openAiVoice, elevenLabsVoice, deviceVoice, voiceLocale,
                                oSpeed = released,
                            )
                        },
                    )
                    TestVoiceButton(isPreviewing = isPreviewing) {
                        preview(selected, geminiVoice, openAiVoice, elevenLabsVoice, deviceVoice, voiceLocale)
                    }
                }
                TtsEngine.ELEVENLABS -> {
                    KeyEntryFields(
                        configured = elevenLabsKeyConfigured,
                        statusText = stringResource(
                            if (elevenLabsKeyConfigured) R.string.settings_elevenlabs_key_status_set
                            else R.string.settings_elevenlabs_key_status_unset,
                        ),
                        placeholder = stringResource(R.string.settings_elevenlabs_key_placeholder),
                        saveLabel = stringResource(R.string.settings_elevenlabs_key_save),
                        clearLabel = stringResource(R.string.settings_elevenlabs_key_clear),
                        onSave = onSetElevenLabsKey,
                        onClear = onClearElevenLabsKey,
                    )
                    // Use the user's account voices once they've refreshed,
                    // else fall back to the curated en-US library list. Both
                    // paths apply the same locale filter so the picker
                    // behaves consistently whether the list is curated or
                    // account-fetched. `keepSelected` keeps the user's
                    // current voice visible if its mapped accent doesn't
                    // match the chosen locale (otherwise the dialog would
                    // omit it and the button label would fall back to the
                    // raw voice ID). Empty refreshed list (exotic account
                    // state) silently falls back to the curated list so the
                    // picker is never empty.
                    val elevenLabsSource = elevenLabsRefreshedVoices
                        ?.takeIf { it.isNotEmpty() }
                        ?: ELEVENLABS_VOICES
                    val pickerVoices = elevenLabsSource
                        .filterByVariant(voiceLocale, keepSelected = elevenLabsVoice)
                    VoicePicker(
                        title = stringResource(R.string.settings_tts_voice_label),
                        voices = pickerVoices,
                        selectedId = elevenLabsVoice,
                        enabled = !isPreviewing,
                        onSelect = {
                            onSetElevenLabsVoice(it)
                            preview(TtsEngine.ELEVENLABS, geminiVoice, openAiVoice, it, deviceVoice, voiceLocale)
                        },
                    )
                    LocaleFallbackCaption(
                        source = elevenLabsSource,
                        voiceLocale = voiceLocale,
                    )
                    // Caption only when we actually rendered the refreshed
                    // list. An empty refresh result silently falls back to
                    // the curated voices (above), so "Showing 0 voices …"
                    // would be misleading.
                    if (!elevenLabsRefreshedVoices.isNullOrEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.settings_elevenlabs_refresh_voices_loaded,
                                elevenLabsRefreshedVoices.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Refresh is gated on the key being configured — without
                    // a key the API call would 401. Disabled while a preview
                    // or another refresh is in flight to avoid stacking
                    // requests against the user's quota.
                    if (elevenLabsKeyConfigured) {
                        OutlinedButton(
                            onClick = onRefreshElevenLabsVoices,
                            enabled = !isPreviewing && !elevenLabsRefreshing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // Always render the text label so TalkBack /
                            // VoiceOver have something to announce (the
                            // spinner alone is unlabeled). The spinner
                            // sits to the left of the label while a
                            // refresh is in flight.
                            if (elevenLabsRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                            }
                            Text(stringResource(R.string.settings_elevenlabs_refresh_voices))
                        }
                    }
                    ElevenLabsModelPicker(
                        selected = elevenLabsModel,
                        enabled = !isPreviewing,
                        onSelect = { picked ->
                            onSetElevenLabsModel(picked)
                            preview(
                                TtsEngine.ELEVENLABS, geminiVoice, openAiVoice, elevenLabsVoice, deviceVoice, voiceLocale,
                                eModel = picked,
                            )
                        },
                    )
                    TtsParameterSlider(
                        labelRes = R.string.settings_elevenlabs_speed_label,
                        persistedValue = elevenLabsSpeed,
                        min = UserPreferences.MIN_ELEVENLABS_SPEED,
                        max = UserPreferences.MAX_ELEVENLABS_SPEED,
                        step = 0.05,
                        enabled = !isPreviewing,
                        // Persist + preview on slider release only. Every
                        // drag tick would fire a DataStore write, a
                        // preferences-flow emission, and (worse) a billed
                        // synthesis call. The slider holds its dragged
                        // value in local Compose state until release.
                        onValueChangeFinished = { released ->
                            onSetElevenLabsSpeed(released)
                            preview(
                                TtsEngine.ELEVENLABS, geminiVoice, openAiVoice, elevenLabsVoice, deviceVoice, voiceLocale,
                                eSpeed = released,
                            )
                        },
                    )
                    TtsParameterSlider(
                        labelRes = R.string.settings_elevenlabs_stability_label,
                        persistedValue = elevenLabsStability,
                        min = UserPreferences.MIN_ELEVENLABS_STABILITY,
                        max = UserPreferences.MAX_ELEVENLABS_STABILITY,
                        // 0.05 increments give 21 selectable values across
                        // 0.0–1.0 — fine enough that the user can audition
                        // "expressive" (≤0.4), "default" (~0.65), and
                        // "steady" (≥0.85) without overwhelming the picker.
                        step = 0.05,
                        enabled = !isPreviewing,
                        onValueChangeFinished = { released ->
                            onSetElevenLabsStability(released)
                            preview(
                                TtsEngine.ELEVENLABS, geminiVoice, openAiVoice, elevenLabsVoice, deviceVoice, voiceLocale,
                                eStability = released,
                            )
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

@Composable
private fun VoiceLocalePicker(
    selected: VoiceLocale,
    region: Region,
    onSelect: (VoiceLocale) -> Unit,
    enabled: Boolean = true,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    val title = stringResource(R.string.settings_tts_voice_locale_label)
    // Show the locale that VoiceLocale.SYSTEM resolves to: the app's
    // configured region language when the user has set one explicitly, or the
    // device UI language otherwise. This is what the TTS engine actually uses,
    // so the user can verify what accent they'll hear without leaving Settings.
    val uiLocale = LocalContext.current.resourcesLocale()
    // Prefer the region-configured locale over the device default. Fall back
    // to uiLocale (the context resources locale) when region is SYSTEM so the
    // tag still updates with runtime app-language changes.
    // Strip Unicode extensions (e.g. `-u-fw-mon` from a Monday-week device
    // preference) — they're irrelevant to the spoken accent and just clutter
    // the picker label.
    val systemTag = VoiceLocale.SYSTEM.resolve(region.toJavaLocale() ?: uiLocale).stripExtensions().toLanguageTag()
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
                val collator = remember(uiLocale) { Collator.getInstance(uiLocale) }
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
    VoiceLocale.DE_AT -> R.string.settings_tts_voice_locale_de_at
    VoiceLocale.DE_CH -> R.string.settings_tts_voice_locale_de_ch
    VoiceLocale.FR_FR -> R.string.settings_tts_voice_locale_fr_fr
    VoiceLocale.FR_CA -> R.string.settings_tts_voice_locale_fr_ca
    VoiceLocale.IT_IT -> R.string.settings_tts_voice_locale_it_it
    VoiceLocale.ES_ES -> R.string.settings_tts_voice_locale_es_es
    VoiceLocale.ES_MX -> R.string.settings_tts_voice_locale_es_mx
    VoiceLocale.CA_ES -> R.string.settings_tts_voice_locale_ca_es
    VoiceLocale.RU_RU -> R.string.settings_tts_voice_locale_ru_ru
    VoiceLocale.PL_PL -> R.string.settings_tts_voice_locale_pl_pl
    VoiceLocale.HR_HR -> R.string.settings_tts_voice_locale_hr_hr
    VoiceLocale.SL_SI -> R.string.settings_tts_voice_locale_sl_si
    VoiceLocale.SR_RS -> R.string.settings_tts_voice_locale_sr_rs
    VoiceLocale.SR_CYRL_RS -> R.string.settings_tts_voice_locale_sr_cyrl_rs
    VoiceLocale.BG_BG -> R.string.settings_tts_voice_locale_bg_bg
    VoiceLocale.CS_CZ -> R.string.settings_tts_voice_locale_cs_cz
    VoiceLocale.SK_SK -> R.string.settings_tts_voice_locale_sk_sk
    VoiceLocale.HU_HU -> R.string.settings_tts_voice_locale_hu_hu
    VoiceLocale.RO_RO -> R.string.settings_tts_voice_locale_ro_ro
    VoiceLocale.EL_GR -> R.string.settings_tts_voice_locale_el_gr
    VoiceLocale.UK_UA -> R.string.settings_tts_voice_locale_uk_ua
    VoiceLocale.PT_BR -> R.string.settings_tts_voice_locale_pt_br
    VoiceLocale.PT_PT -> R.string.settings_tts_voice_locale_pt_pt
    VoiceLocale.NL_NL -> R.string.settings_tts_voice_locale_nl_nl
    VoiceLocale.SV_SE -> R.string.settings_tts_voice_locale_sv_se
    VoiceLocale.DA_DK -> R.string.settings_tts_voice_locale_da_dk
    VoiceLocale.NB_NO -> R.string.settings_tts_voice_locale_nb_no
    VoiceLocale.FI_FI -> R.string.settings_tts_voice_locale_fi_fi
    VoiceLocale.ET_EE -> R.string.settings_tts_voice_locale_et_ee
    VoiceLocale.LV_LV -> R.string.settings_tts_voice_locale_lv_lv
    VoiceLocale.LT_LT -> R.string.settings_tts_voice_locale_lt_lt
    VoiceLocale.TR_TR -> R.string.settings_tts_voice_locale_tr_tr
    VoiceLocale.EN_ZA -> R.string.settings_tts_voice_locale_en_za
    VoiceLocale.ID_ID -> R.string.settings_tts_voice_locale_id_id
    VoiceLocale.MS_MY -> R.string.settings_tts_voice_locale_ms_my
    VoiceLocale.FIL_PH -> R.string.settings_tts_voice_locale_fil_ph
    VoiceLocale.VI_VN -> R.string.settings_tts_voice_locale_vi_vn
    VoiceLocale.TH_TH -> R.string.settings_tts_voice_locale_th_th
    VoiceLocale.ZH_CN -> R.string.settings_tts_voice_locale_zh_cn
    VoiceLocale.ZH_TW -> R.string.settings_tts_voice_locale_zh_tw
    VoiceLocale.HI_IN -> R.string.settings_tts_voice_locale_hi_in
    VoiceLocale.BN_BD -> R.string.settings_tts_voice_locale_bn_bd
    VoiceLocale.JA_JP -> R.string.settings_tts_voice_locale_ja_jp
    VoiceLocale.KO_KR -> R.string.settings_tts_voice_locale_ko_kr
    VoiceLocale.AR_SA -> R.string.settings_tts_voice_locale_ar_sa
    VoiceLocale.AR_EG -> R.string.settings_tts_voice_locale_ar_eg
    VoiceLocale.AR_AE -> R.string.settings_tts_voice_locale_ar_ae
    VoiceLocale.AR_MA -> R.string.settings_tts_voice_locale_ar_ma
    VoiceLocale.HE_IL -> R.string.settings_tts_voice_locale_he_il
    VoiceLocale.FA_IR -> R.string.settings_tts_voice_locale_fa_ir
    VoiceLocale.SQ_AL -> R.string.settings_tts_voice_locale_sq_al
    VoiceLocale.UR_PK -> R.string.settings_tts_voice_locale_ur_pk
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
 * Radio list for the four ElevenLabs models we expose: Turbo v2.5 (default,
 * balanced), Multilingual v2 (legacy flagship — 2× the credit cost), Flash
 * v2.5 (lowest latency), and v3 (alpha). Kept as a small fixed list rather
 * than a `GET /v1/models` fetch — the cost / latency trade-offs of each are
 * UX prose the user benefits from seeing inline, and the wire IDs don't
 * change often. Add a new constant + entry here when ElevenLabs ships
 * something worth surfacing.
 */
@Composable
private fun ElevenLabsModelPicker(
    selected: String,
    onSelect: (String) -> Unit,
    enabled: Boolean = true,
) {
    val models = listOf(
        ELEVENLABS_MODEL_TURBO_V2_5 to R.string.settings_elevenlabs_model_turbo_v2_5,
        ELEVENLABS_MODEL_MULTILINGUAL_V2 to R.string.settings_elevenlabs_model_multilingual_v2,
        ELEVENLABS_MODEL_FLASH_V2_5 to R.string.settings_elevenlabs_model_flash_v2_5,
        ELEVENLABS_MODEL_V3 to R.string.settings_elevenlabs_model_v3,
    )
    Text(
        text = stringResource(R.string.settings_elevenlabs_model_label),
        style = MaterialTheme.typography.titleSmall,
    )
    models.forEach { (id, labelRes) ->
        RadioRow(
            label = stringResource(labelRes),
            selected = id == selected,
            enabled = enabled,
            onSelect = { onSelect(id) },
        )
    }
}

/**
 * Slider for a per-clip TTS tuning parameter (currently used for ElevenLabs
 * speed + stability and OpenAI speed). [labelRes] takes one `%1$s` formatted
 * placeholder for the live numeric value (e.g. "Speed × %1$s" → "Speed × 0.95").
 *
 * The dragged value is held in local Compose state — only on release does
 * [onValueChangeFinished] fire with the final value, which the caller
 * persists and uses to trigger a billed synthesis preview. This avoids a
 * burst of DataStore writes (one per drag tick) and keeps a single
 * preview from stacking on top of itself if the user is still dragging.
 *
 * The label updates live during drag (off the local state), so the
 * thumb-following number isn't laggy.
 */
@Composable
private fun TtsParameterSlider(
    @androidx.annotation.StringRes labelRes: Int,
    persistedValue: Double,
    min: Double,
    max: Double,
    step: Double,
    onValueChangeFinished: (Double) -> Unit,
    enabled: Boolean = true,
) {
    val minF = min.toFloat()
    val maxF = max.toFloat()
    // Slider's `steps` is the count of positions *between* the endpoints, so
    // an N-step range uses (N - 1) here. round() guards against the floating
    // tail of e.g. (1.0 - 0.0) / 0.05 producing 19.999…
    val steps = (((max - min) / step).let { Math.round(it).toInt() } - 1).coerceAtLeast(0)
    // `persistedValue` keys the remember so an external change (DataStore
    // emission landing after `onValueChangeFinished` persists, or a future
    // settings reset) overwrites the local-during-drag value.
    var draggedValue by remember(persistedValue) {
        mutableStateOf(persistedValue.toFloat().coerceIn(minF, maxF))
    }
    val displayValue = String.format(Locale.US, "%.2f", draggedValue)
    Text(
        text = stringResource(labelRes, displayValue),
        style = MaterialTheme.typography.titleSmall,
    )
    Slider(
        value = draggedValue,
        onValueChange = { draggedValue = it },
        onValueChangeFinished = { onValueChangeFinished(draggedValue.toDouble()) },
        valueRange = minF..maxF,
        steps = steps,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

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

/**
 * Caption rendered under a cloud-engine voice picker when [filterByVariant]
 * fell back from an exact-accent match — `filterByVariant` silently falls
 * back to a same-language list (or the full list) so the picker is never
 * empty, and without context that looks like the engine ignored the
 * locale setting. The caption names the locale so the user knows the
 * fallback was deliberate, and tells them whether the visible voices are
 * a same-language near-match or just "everything we have, try another
 * accent".
 *
 * Renders nothing when [voiceLocale] is [VoiceLocale.SYSTEM] (no
 * preference expressed) or when at least one voice in [source] matches it.
 */
@Composable
private fun LocaleFallbackCaption(source: List<TtsVoiceOption>, voiceLocale: VoiceLocale) {
    val tier = source.localeFallbackTier(voiceLocale)
    val captionRes = when (tier) {
        LocaleFallbackTier.Exact -> return
        LocaleFallbackTier.SameLanguage -> R.string.settings_tts_voice_locale_language_fallback
        LocaleFallbackTier.FullList -> R.string.settings_tts_voice_locale_no_match
    }
    val localeLabel = stringResource(voiceLocaleLabel(voiceLocale))
    Text(
        text = stringResource(captionRes, localeLabel),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
    openAiSpeed: Double,
    elevenLabsVoice: String,
    elevenLabsModel: String,
    elevenLabsSpeed: Double,
    elevenLabsStability: Double,
    deviceVoice: String?,
    voiceLocale: VoiceLocale,
    region: Region,
) {
    val app = context.applicationContext as app.clothescast.ClothesCastApplication
    // Network synthesis and AudioTrack write are both blocking-ish work — Ktor
    // suspends off-Main internally, but AudioTrack.write/play are JNI calls and
    // we don't want a hot stack of preview work running on the UI dispatcher.
    withContext(Dispatchers.IO) {
        val locale = voiceLocale.resolve(region.toJavaLocale() ?: Locale.getDefault())
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
                        OpenAITtsSpeaker(
                            client = app.openAiTtsClient,
                            voice = openAiVoice,
                            speed = openAiSpeed,
                        ).speak(text, locale)
                    TtsEngine.ELEVENLABS ->
                        ElevenLabsTtsSpeaker(
                            client = app.elevenLabsTtsClient,
                            voiceId = elevenLabsVoice,
                            model = elevenLabsModel,
                            speed = elevenLabsSpeed,
                            stability = elevenLabsStability,
                        ).speak(text, locale)
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
