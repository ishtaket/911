package com.searchaid.core.di

import com.searchaid.BuildConfig
import com.searchaid.data.remote.api.GoogleSearchApi
import com.searchaid.data.remote.api.WaybackApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)

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
    @Named("google")
    fun provideGoogleRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/customsearch/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    @Named("wayback")
    fun provideWaybackRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://web.archive.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideGoogleSearchApi(@Named("google") retrofit: Retrofit): GoogleSearchApi =
        retrofit.create(GoogleSearchApi::class.java)

    @Provides
    @Singleton
    fun provideWaybackApi(@Named("wayback") retrofit: Retrofit): WaybackApi =
        retrofit.create(WaybackApi::class.java)
}
