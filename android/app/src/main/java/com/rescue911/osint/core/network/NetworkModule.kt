package com.rescue911.osint.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.rescue911.osint.BuildConfig
import com.rescue911.osint.data.remote.Rescue911Api
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        rewrite: HostRewriteInterceptor,
    ): OkHttpClient {
        // Dispatch endpoints fan out to multiple upstream APIs (Vertex AI
        // Search across query variants, then Wayback/Common Crawl across
        // every URL anchor). End-to-end latency for a fresh archive
        // dispatch is regularly ~60-120s. The previous 15s read-timeout
        // surfaced as `Dispatch failed: SocketTimeoutException` in the
        // Web/Archive inboxes — a UX regression with no upside, since
        // the operator just retried until it worked. Bumped to 180s to
        // cover the slowest legitimate dispatch we've measured.
        val builder = OkHttpClient.Builder()
            .addInterceptor(rewrite)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(200, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        json: Json,
        holder: ApiBaseUrlHolder,
    ): Retrofit {
        // The base URL is a placeholder — HostRewriteInterceptor swaps the
        // scheme/host/port on every request to ApiBaseUrlHolder.current.
        // Retrofit still needs *something* parseable here.
        return Retrofit.Builder()
            .baseUrl(holder.current.toString())
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): Rescue911Api =
        retrofit.create(Rescue911Api::class.java)
}
