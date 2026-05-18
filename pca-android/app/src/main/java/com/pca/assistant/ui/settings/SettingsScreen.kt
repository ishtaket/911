package com.pca.assistant.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LinearProgressIndicator
import com.pca.assistant.R
import com.pca.assistant.models.ModelRegistry
import com.pca.assistant.models.ModelSpec
import com.pca.assistant.settings.LanguageChoice
import com.pca.assistant.settings.ProviderMode
import com.pca.assistant.settings.SttModelChoice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onReEnroll: () -> Unit) {
    val s by vm.state.collectAsState()
    var bridgeText by remember(s) { mutableStateOf(s?.bridgeUrl.orEmpty()) }
    var showWipe by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        if (s == null) return@Scaffold
        val cfg = s!!
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Section(title = stringResource(R.string.settings_provider)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { vm.setProvider(ProviderMode.MOCK) },
                        label = { Text(stringResource(R.string.settings_provider_mock)) },
                        leadingIcon = if (cfg.providerMode == ProviderMode.MOCK) ({
                            Icon(androidx.compose.material.icons.Icons.Default.Check, contentDescription = null)
                        }) else null
                    )
                    AssistChip(
                        onClick = { vm.setProvider(ProviderMode.BRIDGE) },
                        label = { Text(stringResource(R.string.settings_provider_bridge)) },
                        leadingIcon = if (cfg.providerMode == ProviderMode.BRIDGE) ({
                            Icon(androidx.compose.material.icons.Icons.Default.Check, contentDescription = null)
                        }) else null
                    )
                }
                if (cfg.providerMode == ProviderMode.BRIDGE) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = bridgeText,
                        onValueChange = { bridgeText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_bridge_url)) },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.setBridgeUrl(bridgeText) }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }

            Section(title = stringResource(R.string.settings_window_minutes)) {
                Text(text = "${cfg.windowMinutes}", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = cfg.windowMinutes.toFloat(),
                    onValueChange = { vm.setWindowMinutes(it.toInt()) },
                    valueRange = 1f..30f,
                    steps = 28,
                )
            }

            Section(title = stringResource(R.string.settings_language)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LangChip(R.string.settings_lang_system, LanguageChoice.SYSTEM, cfg.language, vm::setLanguage)
                    LangChip(R.string.settings_lang_en, LanguageChoice.EN, cfg.language, vm::setLanguage)
                    LangChip(R.string.settings_lang_ru, LanguageChoice.RU, cfg.language, vm::setLanguage)
                    LangChip(R.string.settings_lang_he, LanguageChoice.HE, cfg.language, vm::setLanguage)
                }
            }

            Section(title = stringResource(R.string.settings_model)) {
                ModelChoice(R.string.settings_model_android, SttModelChoice.ANDROID_BUILT_IN, cfg.sttModel, vm::setSttModel)
                ModelChoice(R.string.settings_model_whisper_small, SttModelChoice.WHISPER_SMALL_Q5, cfg.sttModel, vm::setSttModel)
                ModelChoice(R.string.settings_model_whisper_turbo, SttModelChoice.WHISPER_TURBO_Q5, cfg.sttModel, vm::setSttModel)
                ModelChoice(R.string.settings_model_ivrit, SttModelChoice.WHISPER_TURBO_PLUS_IVRIT, cfg.sttModel, vm::setSttModel)
            }

            Section(title = stringResource(R.string.settings_models_title)) {
                val readyMap by vm.modelsReady.collectAsState()
                val downloadsMap by vm.downloads.collectAsState()
                listOf(
                    ModelRegistry.WHISPER_TURBO_Q5,
                    ModelRegistry.WHISPER_SMALL_Q5,
                    ModelRegistry.IVRIT_TURBO_Q5,
                    ModelRegistry.ECAPA_TDNN_ONNX,
                ).forEach { spec ->
                    ModelRow(
                        spec = spec,
                        ready = readyMap[spec.id] == true,
                        status = downloadsMap[spec.id],
                        onDownload = { vm.download(spec) },
                        onDelete = { vm.deleteModel(spec) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Section(title = stringResource(R.string.settings_geofence_pause)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = cfg.geofencePause, onCheckedChange = { vm.setGeofencePause(it) })
                }
            }

            Button(
                onClick = onReEnroll,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_re_enroll)) }

            Button(
                onClick = { showWipe = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_wipe)) }
        }
    }

    if (showWipe) {
        AlertDialog(
            onDismissRequest = { showWipe = false },
            title = { Text(stringResource(R.string.settings_wipe_confirm)) },
            text = { Text(stringResource(R.string.settings_wipe_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showWipe = false
                    vm.wipeEverything()
                }) { Text(stringResource(R.string.settings_wipe_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showWipe = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun LangChip(labelRes: Int, value: LanguageChoice, current: LanguageChoice, onPick: (LanguageChoice) -> Unit) {
    AssistChip(
        onClick = { onPick(value) },
        label = { Text(stringResource(labelRes)) },
        leadingIcon = if (current == value) ({
            Icon(androidx.compose.material.icons.Icons.Default.Check, contentDescription = null)
        }) else null,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (current == value) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun ModelRow(
    spec: ModelSpec,
    ready: Boolean,
    status: DownloadStatus?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(spec.id, style = MaterialTheme.typography.bodyLarge)
                Text("≈ ${spec.approxMb} MB", style = MaterialTheme.typography.bodySmall)
            }
            when {
                ready -> Button(onClick = onDelete) {
                    Text(stringResource(R.string.settings_models_delete))
                }
                status != null && !status.done && status.failed == null -> {
                    Text("${status.percent.coerceAtLeast(0)}%")
                }
                else -> Button(onClick = onDownload) {
                    Text(stringResource(R.string.settings_download_model))
                }
            }
        }
        if (status != null && !status.done && status.failed == null && status.percent >= 0) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { status.percent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (status?.failed != null) {
            Text(
                text = status.failed,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ModelChoice(labelRes: Int, value: SttModelChoice, current: SttModelChoice, onPick: (SttModelChoice) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(labelRes), modifier = Modifier.weight(1f))
        Switch(checked = current == value, onCheckedChange = { if (it) onPick(value) })
    }
}
