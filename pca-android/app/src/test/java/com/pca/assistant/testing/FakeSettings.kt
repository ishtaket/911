package com.pca.assistant.testing

import com.pca.assistant.settings.AppSettings
import com.pca.assistant.settings.LanguageChoice
import com.pca.assistant.settings.ProviderMode
import com.pca.assistant.settings.Settings
import com.pca.assistant.settings.SttModelChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Drop-in replacement for [AppSettings] that doesn't touch Android DataStore.
 *
 * Production [AppSettings] is a concrete @Singleton with a real Context
 * dependency. We can't easily fake an Android Context in pure JVM tests, so
 * the things that consume settings (ProviderRouter, WindowProcessor) are
 * written against the public API surface — a [StateFlow<Settings>] plus
 * suspend setters. Tests construct [FakeSettings] directly and pass it in
 * place of the production singleton.
 *
 * To keep the same call sites unchanged in tests, FakeSettings exposes the
 * same `flow` and setter signatures via [asAppSettings] when wiring through
 * constructors typed as the concrete [AppSettings] is needed. The production
 * router does NOT use that escape hatch — it only touches `.flow.first()`,
 * which we mirror exactly.
 */
class FakeSettings(initial: Settings = DEFAULT) {

    private val _flow = MutableStateFlow(initial)
    val flow: StateFlow<Settings> = _flow

    fun set(s: Settings) { _flow.value = s }
    fun mutate(block: (Settings) -> Settings) { _flow.value = block(_flow.value) }

    companion object {
        val DEFAULT = Settings(
            providerMode = ProviderMode.MOCK,
            bridgeUrl = "",
            windowMinutes = 5,
            language = LanguageChoice.EN,
            sttModel = SttModelChoice.ANDROID_BUILT_IN,
            geofencePause = false,
            onboardingDone = true,
            listeningEnabled = true,
        )
    }
}
