package com.pca.assistant.di

import android.content.Context
import com.pca.assistant.BuildConfig
import com.pca.assistant.data.db.PcaDatabase
import com.pca.assistant.data.db.dao.DaySummaryDao
import com.pca.assistant.data.db.dao.HourSummaryDao
import com.pca.assistant.data.db.dao.InterventionDao
import com.pca.assistant.data.db.dao.LlmHealthDao
import com.pca.assistant.data.db.dao.OpenThreadDao
import com.pca.assistant.data.db.dao.OwnerDao
import com.pca.assistant.data.db.dao.PlaceDao
import com.pca.assistant.data.db.dao.TranscriptDao
import com.pca.assistant.data.db.dao.WindowDao
import com.pca.assistant.data.security.DbPassphrase
import com.pca.assistant.speaker.AdaptiveSpeakerIdentifier
import com.pca.assistant.speaker.SpeakerIdentifier
import com.pca.assistant.stt.AdaptiveSpeechRecognizer
import com.pca.assistant.stt.SpeechRecognizer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Marks the OkHttpClient configured for model-weight downloads (follows redirects). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ModelDownloadHttp

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        explicitNulls = false
    }

    /**
     * Shared base client config (logging, timeouts). Two derived clients
     * differ on a single dimension — redirect following — because the two
     * use cases need opposite policies:
     *   - BRIDGE: no redirects (PCA-S-36 — a compromised bridge could
     *     respond `302 Location: http://attacker.com/decide` and we'd
     *     exfiltrate window payloads).
     *   - MODEL DOWNLOAD: follow redirects (HuggingFace `/resolve/main/...`
     *     URLs 302 to their CDN).
     */
    private fun baseHttpBuilder(): OkHttpClient.Builder {
        val log = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(log)
    }

    /**
     * Default OkHttp used by [com.pca.assistant.llm.HttpBridgeProvider]
     * (and any callers without a redirect-following requirement). Refuses
     * to follow 30x responses — see PCA-S-36.
     */
    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = baseHttpBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /**
     * Permissive OkHttp for model weight downloads. Hugging Face routes
     * `/resolve/main/<file>` through 30x to a CDN URL — we must follow
     * to get the actual bytes. The downloader still gates the final URL
     * via [com.pca.assistant.models.ModelDownloader.isAcceptableModelUrl],
     * which only allows https (or http to loopback / RFC-1918) — so an
     * attacker-controlled redirect to a public-http host is rejected
     * once the download starts hitting the CDN URL.
     */
    @Provides
    @Singleton
    @ModelDownloadHttp
    fun provideModelDownloadOkHttp(): OkHttpClient = baseHttpBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, passphrase: DbPassphrase): PcaDatabase =
        PcaDatabase.build(context, passphrase.obtain())

    @Provides @Singleton fun provideOwnerDao(db: PcaDatabase): OwnerDao = db.ownerDao()
    @Provides @Singleton fun provideTranscriptDao(db: PcaDatabase): TranscriptDao = db.transcriptDao()
    @Provides @Singleton fun provideWindowDao(db: PcaDatabase): WindowDao = db.windowDao()
    @Provides @Singleton fun provideInterventionDao(db: PcaDatabase): InterventionDao = db.interventionDao()
    @Provides @Singleton fun provideOpenThreadDao(db: PcaDatabase): OpenThreadDao = db.openThreadDao()
    @Provides @Singleton fun provideHourSummaryDao(db: PcaDatabase): HourSummaryDao = db.hourSummaryDao()
    @Provides @Singleton fun provideDaySummaryDao(db: PcaDatabase): DaySummaryDao = db.daySummaryDao()
    @Provides @Singleton fun providePlaceDao(db: PcaDatabase): PlaceDao = db.placeDao()
    @Provides @Singleton fun provideLlmHealthDao(db: PcaDatabase): LlmHealthDao = db.llmHealthDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    /** Production STT — whisper.cpp via JNI, with Android SpeechRecognizer fallback. */
    @Binds @Singleton
    abstract fun bindSpeechRecognizer(impl: AdaptiveSpeechRecognizer): SpeechRecognizer

    /** Production speaker ID — ECAPA-TDNN ONNX, with synthetic embedding fallback. */
    @Binds @Singleton
    abstract fun bindSpeakerIdentifier(impl: AdaptiveSpeakerIdentifier): SpeakerIdentifier
}
