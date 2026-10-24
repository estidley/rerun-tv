package com.rerun.tv.data

import com.rerun.core.plex.Pin
import com.rerun.core.plex.Playback
import com.rerun.core.plex.PlexParse
import com.rerun.core.plex.PlexServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Thin Plex HTTP client. Parsing lives in :core (PlexParse). Never sends playback reports. */
class PlexApi(private val clientId: () -> String, private val http: OkHttpClient) {

    private fun Request.Builder.plexHeaders(token: String? = null): Request.Builder = apply {
        header("Accept", "application/json")
        header("X-Plex-Client-Identifier", clientId())
        header("X-Plex-Product", "Rerun")
        header("X-Plex-Version", "0.1.0")
        header("X-Plex-Platform", "Android")
        header("X-Plex-Device", "Fire TV")
        header("X-Plex-Device-Name", "Rerun TV")
        if (token != null) header("X-Plex-Token", token)
    }

    private suspend fun call(request: Request, client: OkHttpClient = http): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for ${request.url.encodedPath}")
            resp.body.string()
        }
    }

    suspend fun createPin(): Pin = PlexParse.pin(
        // plex.tv/link only accepts a 4-character code. strong=true is longer than that page allows.
        call(Request.Builder().url("https://plex.tv/api/v2/pins?strong=false").plexHeaders()
            .post(ByteArray(0).toRequestBody(null)).build()),
    )

    suspend fun checkPin(id: Long): Pin =
        PlexParse.pin(call(Request.Builder().url("https://plex.tv/api/v2/pins/$id").plexHeaders().get().build()))

    suspend fun servers(token: String): List<PlexServer> = PlexParse.servers(
        call(Request.Builder().url("https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1")
            .plexHeaders(token).get().build()),
    )

    /** First connection (local → remote → relay) that answers /identity within 3 seconds. */
    suspend fun pickConnection(server: PlexServer): String? {
        val quick = http.newBuilder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS).build()
        for (c in Playback.connectionOrder(server)) {
            val ok = runCatching {
                call(Request.Builder().url("${c.uri}/identity").plexHeaders(server.accessToken).get().build(), quick)
            }.isSuccess
            if (ok) return c.uri
        }
        return null
    }

    suspend fun get(base: String, path: String, token: String, start: Int? = null, size: Int? = null): String {
        val b = Request.Builder().url(base + path).plexHeaders(token).get()
        if (start != null) b.header("X-Plex-Container-Start", start.toString())
        if (size != null) b.header("X-Plex-Container-Size", size.toString())
        return call(b.build())
    }
}
