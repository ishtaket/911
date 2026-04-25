package com.rescue911.osint.core.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewrites every outgoing request's scheme/host/port to match the current
 * value held in [ApiBaseUrlHolder]. The path/query is preserved.
 */
@Singleton
class HostRewriteInterceptor @Inject constructor(
    private val holder: ApiBaseUrlHolder,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val target = holder.current

        val rewritten = original.url.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .build()

        return chain.proceed(original.newBuilder().url(rewritten).build())
    }
}
