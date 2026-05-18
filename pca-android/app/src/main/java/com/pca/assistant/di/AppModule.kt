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
import javax.inject.Singleton

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

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient {
        // B-34: bridge URL (potentially the user's home IP) ends up in
        // logcat at BASIC level. Restrict to debug builds — release ships
        // with NONE so a release-build APK doesn't leak the user's LAN
        // topology into logs that other on-device apps used to be able to
        // read on older Android versions.
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
            .build()
    }

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
