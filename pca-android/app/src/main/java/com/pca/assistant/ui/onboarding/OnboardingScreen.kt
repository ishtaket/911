package com.pca.assistant.ui.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.pca.assistant.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(vm: OnboardingViewModel, onDone: () -> Unit) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LinearProgressIndicator(
                progress = (s.step.ordinal + 1) / OnbStep.entries.size.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
            when (s.step) {
                OnbStep.WELCOME -> WelcomeStep(onContinue = vm::next)
                OnbStep.CONSENT -> ConsentStep(onAccept = vm::next, onBack = vm::back)
                OnbStep.PERMS -> PermsStep(onContinue = vm::next, onBack = vm::back)
                OnbStep.ENROLL -> EnrollStep(
                    state = s,
                    onBiometricConfirmed = vm::confirmBiometric,
                    onPhraseRecorded = vm::addPhraseEmbedding,
                    onContinue = vm::next,
                    onBack = vm::back,
                )
                OnbStep.FINISH -> {
                    val finishScope = rememberCoroutineScope()
                    FinishStep(
                        onStart = {
                            finishScope.launch {
                                vm.finish(name = "Owner", languageHint = null).join()
                                onDone()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    Text(stringResource(R.string.onb_welcome_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.onb_welcome_body))
    Spacer(Modifier.height(16.dp))
    Button(onClick = onContinue) { Text(stringResource(R.string.action_continue)) }
}

@Composable
private fun ConsentStep(onAccept: () -> Unit, onBack: () -> Unit) {
    Text(stringResource(R.string.onb_consent_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.onb_consent_body))
    Spacer(Modifier.height(16.dp))
    Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onb_consent_accept))
    }
    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_back))
    }
}

@Composable
private fun PermsStep(onContinue: () -> Unit, onBack: () -> Unit) {
    val permsRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* user reply consumed silently; service tolerates missing perms */ }

    Text(stringResource(R.string.onb_perms_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.onb_perms_body))
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = {
            permsRequest.launch(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            )
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text(stringResource(R.string.onb_perms_grant)) }
    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_continue))
    }
    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_back))
    }
}

@Composable
private fun EnrollStep(
    state: OnbState,
    onBiometricConfirmed: () -> Unit,
    onPhraseRecorded: (ShortArray) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val phrases = stringArrayResource(R.array.enroll_phrases)
    var recording by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val activity = context as? FragmentActivity

    // B-30 fix: re-enrollment can be triggered from Settings while the
    // foreground service is still holding the mic. AudioRecord would fail
    // with "mic busy" on OneUI. Pause the service while we're on this step
    // and resume it on dispose if the service was running before.
    DisposableEffect(Unit) {
        com.pca.assistant.service.ListeningService.sendAction(
            context, com.pca.assistant.service.ListeningService.ACTION_PAUSE
        )
        onDispose {
            // Resume on exit. If onboarding was the initial flow (service
            // wasn't running), this RESUME is delivered to a non-existent
            // service and is a no-op — startForegroundService creates it,
            // but onCreate immediately bails on the missing perm check or
            // is followed by Finish → start. Either way no UX impact.
            com.pca.assistant.service.ListeningService.sendAction(
                context, com.pca.assistant.service.ListeningService.ACTION_RESUME
            )
        }
    }

    Text(stringResource(R.string.onb_enroll_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.onb_enroll_body))
    Spacer(Modifier.height(8.dp))

    if (!state.biometricConfirmed) {
        Button(
            onClick = {
                val bm = BiometricManager.from(context)
                val ok = bm.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                ) == BiometricManager.BIOMETRIC_SUCCESS
                if (!ok || activity == null) {
                    onBiometricConfirmed()
                    return@Button
                }
                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(context),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            onBiometricConfirmed()
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            // Treat error as soft-skip — onboarding shouldn't dead-end.
                            onBiometricConfirmed()
                        }
                    }
                )
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(context.getString(R.string.onb_enroll_biometric))
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build()
                prompt.authenticate(info)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.onb_enroll_biometric)) }
    } else {
        val total = phrases.size
        val current = state.phrasesRecorded.coerceAtMost(total - 1)
        if (state.phrasesRecorded < total) {
            Text(
                stringResource(R.string.onb_enroll_phrase, state.phrasesRecorded + 1, phrases[current]),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(
                enabled = !recording,
                onClick = {
                    scope.launch {
                        recording = true
                        val pcm = withContext(Dispatchers.IO) { recordShortClip(context) }
                        recording = false
                        if (pcm.isNotEmpty()) onPhraseRecorded(pcm)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (recording) "..." else stringResource(R.string.onb_enroll_record))
            }
        } else {
            Text(stringResource(R.string.onb_enroll_done), color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_continue))
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_back))
    }
}

@Composable
private fun FinishStep(onStart: () -> Unit) {
    Text(stringResource(R.string.onb_finish_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.onb_finish_body))
    Spacer(Modifier.height(16.dp))
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onb_start))
    }
}

/**
 * Records ~3 seconds of mic audio for voice enrollment. Returns empty array if
 * permission is missing or initialisation failed (caller will simply skip).
 */
@SuppressLint("MissingPermission")
private fun recordShortClip(context: android.content.Context): ShortArray {
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!granted) return ShortArray(0)

    val sampleRate = 16_000
    val minBuf = AudioRecord.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(sampleRate * 2 * 4)
    val record = AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        minBuf
    )
    if (record.state != AudioRecord.STATE_INITIALIZED) return ShortArray(0)
    val totalSamples = sampleRate * 3
    val out = ShortArray(totalSamples)
    record.startRecording()
    try {
        var off = 0
        val buf = ShortArray(sampleRate / 4)
        while (off < totalSamples) {
            val read = record.read(buf, 0, buf.size)
            if (read <= 0) break
            val copy = minOf(read, totalSamples - off)
            System.arraycopy(buf, 0, out, off, copy)
            off += copy
        }
        return out
    } finally {
        runCatching { record.stop() }
        runCatching { record.release() }
    }
}
