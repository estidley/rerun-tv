package com.rerun.tmdb

import com.rerun.core.catalog.Catalog
import com.rerun.core.tmdb.ChannelReference
import com.rerun.core.tmdb.NetworkReference
import com.rerun.core.tmdb.TmdbParse
import com.rerun.core.tmdb.TmdbSource
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import kotlin.system.exitProcess

/**
 * Builds `rerun/tmdb-networks.json`: for every built-in channel, the TMDB ids of the shows its
 * real network aired and of the movies its real network's companies made.
 *
 * 1. Download TMDB's public daily ID exports (networks and companies; no token needed).
 * 2. Pick the entries named like the channel's [com.rerun.core.catalog.NetworkDef.aliases] and
 *    [com.rerun.core.catalog.NetworkDef.tmdbCompanies], keeping ones based in `--country`.
 * 3. Page through `/discover/tv?with_networks=` and `/discover/movie?with_companies=`.
 *
 * The token comes from TMDB_TOKEN and is only ever sent in the Authorization header.
 */
fun main(args: Array<String>) {
    val opts = try {
        Options.parse(args)
    } catch (e: IllegalArgumentException) {
        fail(e.message ?: "Bad arguments")
    }
    if (opts.publishFrom != null) {
        val input = File(opts.publishFrom)
        require(input.isFile) { "Run :tmdb:run to create the local snapshot before publishing." }
        val sanitized = SnapshotPublisher.validate(input.readText()).toJson()
        val target = File(opts.out)
        target.parentFile.mkdirs()
        val temporary = File(target.path + ".tmp")
        temporary.writeText(sanitized)
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        log("Published ${target.path}. Review and commit this data file; no token is required by consumers.")
        return
    }
    val token = System.getenv("TMDB_TOKEN")?.trim().orEmpty()
    if (token.isEmpty()) {
        fail(
            "TMDB_TOKEN is not set. Put your TMDB 'API Read Access Token' in TMDB_TOKEN " +
                "(themoviedb.org > Settings > API), or run scripts\\install.ps1 -TmdbToken <token>.",
        )
    }
    val tmdb = Tmdb(token, opts.apiBase, opts.filesBase)
    val warnings = ArrayList<String>()

    log("Downloading TMDB network and company lists...")
    val networkEntries = tmdb.export("tv_network_ids")
    val companyEntries = tmdb.export("production_company_ids")
    log("  ${networkEntries.size} networks, ${companyEntries.size} companies")

    val channels = Catalog.networks.map { n ->
        val networks = tmdb.inCountry("network", TmdbParse.named(networkEntries, n.aliases), opts.country)
        val companies = tmdb.inCountry("company", TmdbParse.named(companyEntries, n.tmdbCompanies), opts.country)
        if (networks.isEmpty() && n.aliases.isNotEmpty()) {
            warnings += "${n.realName}: no TMDB network named ${n.aliases.joinToString(" / ")} in ${opts.country}"
        }
        val shows = networks.flatMapTo(HashSet()) { tmdb.discover("tv", "with_networks", it.id, opts.maxPages) }
        val movies = companies.flatMapTo(HashSet()) { tmdb.discover("movie", "with_companies", it.id, opts.maxPages) }
        log(
            "%4d %-18s %-18s %5d shows %5d movies  %s".format(
                n.number, n.name, n.realName, shows.size, movies.size,
                (networks + companies).joinToString(", ") { "${it.name} (${it.id})" },
            ),
        )
        ChannelReference(n.id, n.realName, networks, companies, shows, movies)
    }

    val reference = NetworkReference(channels, LocalDate.now(ZoneOffset.UTC).toString(), opts.country)
    val out = File(opts.out)
    out.parentFile?.mkdirs()
    val tmp = File(out.path + ".tmp")
    tmp.writeText(reference.toJson())
    Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING)

    warnings.forEach { log("warning: $it") }
    log("Wrote ${out.path}. Rebuild the app to bundle it.")
    log("This product uses the TMDB API but is not endorsed or certified by TMDB.")
}

private class Options(
    val out: String,
    val country: String,
    val maxPages: Int,
    val apiBase: String,
    val filesBase: String,
    val publishFrom: String?,
) {
    companion object {
        fun parse(args: Array<String>): Options {
            val map = HashMap<String, String>()
            var i = 0
            while (i < args.size) {
                val key = args[i]
                require(key.startsWith("--") && i + 1 < args.size) { "Usage: [--out file] [--country US] [--max-pages 500]" }
                map[key.removePrefix("--")] = args[i + 1]
                i += 2
            }
            val out = map["out"] ?: System.getenv("RERUN_TMDB_OUT")
                ?: throw IllegalArgumentException("Pass --out <file> or run through Gradle (:tmdb:run)")
            return Options(
                out = out,
                country = (map["country"] ?: "US").uppercase(),
                maxPages = map["max-pages"]?.toIntOrNull()?.coerceIn(1, 500) ?: 500,
                apiBase = (map["api-base"] ?: "https://api.themoviedb.org").trimEnd('/'),
                filesBase = (map["files-base"] ?: "https://files.tmdb.org").trimEnd('/'),
                publishFrom = map["publish-from"],
            )
        }
    }
}

private class Tmdb(private val token: String, private val apiBase: String, private val filesBase: String) {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val countries = HashMap<String, String?>()

    /** Today's export, or the most recent of the last few days (exports appear around 8:00 UTC). */
    fun export(kind: String): List<TmdbSource> {
        val fmt = DateTimeFormatter.ofPattern("MM_dd_yyyy")
        val today = LocalDate.now(ZoneOffset.UTC)
        for (back in 0L..3L) {
            val url = "$filesBase/p/exports/${kind}_${today.minusDays(back).format(fmt)}.json.gz"
            val req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).GET().build()
            val res = http.send(req, HttpResponse.BodyHandlers.ofInputStream())
            if (res.statusCode() != 200) {
                res.body().close()
                continue
            }
            return GZIPInputStream(res.body()).bufferedReader().useLines { TmdbParse.exportEntries(it) }
        }
        fail("Could not download TMDB's $kind export from $filesBase. Check your internet connection and try again.")
    }

    /** Entries based in [country]; looks each one up once (TMDB repeats names across regions). */
    fun inCountry(type: String, entries: List<TmdbSource>, country: String): List<TmdbSource> =
        entries.filter { e ->
            val origin = countries.getOrPut("$type:${e.id}") {
                api("/3/$type/${e.id}")?.let(TmdbParse::originCountry)
            }
            country == "ANY" || origin.equals(country, ignoreCase = true)
        }

    fun discover(kind: String, param: String, id: Long, maxPages: Int): Set<Long> {
        val ids = LinkedHashSet<Long>()
        val extra = if (kind == "tv") "&include_null_first_air_dates=true" else ""
        var page = 1
        while (true) {
            val text = api("/3/discover/$kind?$param=$id&include_adult=false&sort_by=popularity.desc&page=$page$extra")
                ?: fail("TMDB discovery was unavailable. Previous network lists were kept; try again later.")
            val p = try {
                TmdbParse.discoverPage(text)
            } catch (_: IllegalArgumentException) {
                fail("TMDB sent an invalid discovery response. Previous network lists were kept; try again later.")
            }
            if (p.page != page || (p.ids.isEmpty() && p.totalPages > page)) {
                fail("TMDB sent incomplete discovery pages. Previous network lists were kept; try again later.")
            }
            ids += p.ids
            if (p.ids.isEmpty() || page >= minOf(p.totalPages, maxPages)) break
            page++
        }
        return ids
    }

    /** GET on the TMDB API. Null for 404. Retries rate limits and server errors. */
    private fun api(path: String): String? {
        var attempt = 0
        while (true) {
            attempt++
            val req = HttpRequest.newBuilder(URI.create(apiBase + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .GET().build()
            val res = try {
                http.send(req, HttpResponse.BodyHandlers.ofString())
            } catch (e: java.io.IOException) {
                if (attempt < 4) { sleep(1000L * attempt); continue }
                fail("TMDB did not respond (${e.javaClass.simpleName}). Check your connection and try again.")
            }
            when (val code = res.statusCode()) {
                200 -> return res.body()
                404 -> return null
                401 -> fail(
                    "TMDB rejected the token. Use the long 'API Read Access Token' from themoviedb.org > " +
                        "Settings > API, not the shorter 'API Key'.",
                )
                429 -> {
                    if (attempt >= 10) fail("TMDB kept rate-limiting the refresh. Wait a minute and try again.")
                    val wait = res.headers().firstValue("Retry-After").orElse("2").toLongOrNull() ?: 2
                    sleep(wait.coerceIn(1, 30) * 1000)
                }
                in 500..599 -> {
                    if (attempt >= 4) fail("TMDB returned $code for ${path.substringBefore('?')}. Try again later.")
                    sleep(1000L * attempt)
                }
                else -> fail("TMDB returned $code for ${path.substringBefore('?')}.")
            }
        }
    }

    private fun sleep(ms: Long) = Thread.sleep(ms)
}

private fun log(msg: String) = println(msg)

private fun fail(msg: String): Nothing {
    System.err.println("error: $msg")
    exitProcess(1)
}
