package com.rerun.tv.data

import com.rerun.core.plex.ServerUri
import com.rerun.core.plex.TokenRedaction
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * One client for Plex API calls, playback, and posters.
 * OkHttp 5.5.1 rejects the compromised eMudhra intermediates. The token is added
 * as a header for trusted hosts and stripped again if a redirect leaves that set.
 * Public http is refused on every hop. Playlist bodies lose query tokens before
 * the player parses them.
 */
fun plexHttpClient(settings: Settings): OkHttpClient {
    val attach = Interceptor { chain ->
        val req = chain.request()
        if (req.header(TokenRedaction.HEADER) != null) return@Interceptor chain.proceed(req)
        val url = req.url
        if (!ServerUri.shouldAttachToken(url.scheme, url.host, settings.pinnedHost())) {
            return@Interceptor chain.proceed(req)
        }
        val token = settings.serverToken ?: settings.accountToken ?: return@Interceptor chain.proceed(req)
        chain.proceed(req.newBuilder().header(TokenRedaction.HEADER, token).build())
    }
    val confine = Interceptor { chain ->
        val req = chain.request()
        val url = req.url
        if (url.scheme.equals("http", ignoreCase = true) && !ServerUri.allowsCleartext(url.host)) {
            throw IOException("Cleartext to ${url.host} is blocked. Rerun only uses http for a Plex server on the local network.")
        }
        val trusted = ServerUri.mayCarryToken(url.scheme, url.host, settings.pinnedHost())
        val b = req.newBuilder()
        val tokenNames = url.queryParameterNames.filter { it.equals(TokenRedaction.HEADER, ignoreCase = true) }
        if (tokenNames.isNotEmpty()) {
            val builder = url.newBuilder()
            var lifted: String? = null
            for (name in tokenNames) {
                val value = url.queryParameter(name)
                if (lifted == null && !value.isNullOrEmpty()) lifted = value
                builder.removeAllQueryParameters(name)
            }
            b.url(builder.build())
            if (trusted && req.header(TokenRedaction.HEADER) == null && lifted != null) {
                b.header(TokenRedaction.HEADER, lifted)
            }
        }
        if (!trusted) b.removeHeader(TokenRedaction.HEADER)
        if (url.encodedPath.endsWith(".m3u8", ignoreCase = true)) b.header("Accept-Encoding", "identity")
        val response = chain.proceed(b.build())
        if (!isPlaylist(response)) return@Interceptor response
        val encoding = response.header("Content-Encoding")
        if (encoding != null && !encoding.equals("identity", ignoreCase = true)) return@Interceptor response
        val body = response.body
        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val cleaned = TokenRedaction.stripQuery(body.string())
        response.newBuilder().body(cleaned.toByteArray(charset).toResponseBody(body.contentType())).build()
    }
    return OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .addInterceptor(attach)
        .addNetworkInterceptor(confine)
        .build()
}

private fun isPlaylist(response: okhttp3.Response): Boolean {
    val path = response.request.url.encodedPath
    if (path.endsWith(".m3u8", ignoreCase = true)) return true
    val type = response.body.contentType()?.toString().orEmpty()
    return type.contains("mpegurl", ignoreCase = true)
}
