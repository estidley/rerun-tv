package com.rerun.core.plex

import java.net.InetAddress
import java.net.URI

/**
 * Which server URLs may be contacted, and which of those may see a Plex token.
 * https is allowed for a server the account listed. http is allowed only for a
 * literal private, loopback, or link-local address, localhost, or a .local name.
 */
object ServerUri {
    fun isAcceptable(uri: String): Boolean {
        val parsed = parse(uri) ?: return false
        if (parsed.userInfo != null || parsed.query != null || parsed.fragment != null) return false
        val path = parsed.path ?: ""
        if (path.isNotEmpty() && path != "/") return false
        val scheme = parsed.scheme?.lowercase() ?: return false
        val host = parsed.host ?: return false
        if (host.isBlank()) return false
        return when (scheme) {
            "https" -> true
            "http" -> isLocalHost(host)
            else -> false
        }
    }

    /**
     * Plain http is only for a local Plex server. The HTTP client checks this on
     * every hop, because a network security config cannot list private addresses.
     */
    fun allowsCleartext(host: String): Boolean = isLocalHost(host)

    /** A request to this host may keep an X-Plex-Token header. */
    fun mayCarryToken(scheme: String, host: String, pinnedHost: String?): Boolean {
        val s = scheme.lowercase()
        if (s == "https" && (isPlexOwned(host) || isLocalHost(host) || sameHost(host, pinnedHost))) return true
        return s == "http" && isLocalHost(host)
    }

    /**
     * Add the saved token automatically. plex.tv is excluded: those calls set the
     * account token themselves, and a server token must not be attached to them.
     */
    fun shouldAttachToken(scheme: String, host: String, pinnedHost: String?): Boolean =
        mayCarryToken(scheme, host, pinnedHost) && !isPlexTv(host)

    fun hostOf(uri: String): String? = parse(uri)?.host?.removePrefix("[")?.removeSuffix("]")

    /** Relative Plex path. Rejects scheme changes, userinfo, and parent segments. */
    fun isLibraryPath(path: String?): Boolean {
        if (path == null || !path.startsWith("/") || path.startsWith("//")) return false
        if (path.any { it == '\\' || it == '@' || it == '?' || it == '#' || it == '\n' || it == '\r' }) return false
        if (path.contains("://")) return false
        return path.split('/').none { it == ".." || it == "." }
    }

    private fun isPlexTv(host: String): Boolean {
        val h = host.lowercase()
        return h == "plex.tv" || h.endsWith(".plex.tv")
    }

    private fun isPlexOwned(host: String): Boolean {
        val h = host.lowercase()
        return isPlexTv(h) || h.endsWith(".plex.direct")
    }

    private fun sameHost(host: String, pinnedHost: String?): Boolean =
        pinnedHost != null && host.equals(pinnedHost, ignoreCase = true)

    private fun isLocalHost(host: String): Boolean {
        val h = host.trim().removePrefix("[").removeSuffix("]")
        if (h.equals("localhost", ignoreCase = true)) return true
        // A single mDNS label, such as living-room.local. Not evil.com.local.
        if (h.lowercase().let { it.endsWith(".local") && it.count { c -> c == '.' } == 1 }) return true
        if (!isIpLiteral(h)) return false
        val addr = try {
            InetAddress.getByName(h)
        } catch (_: Exception) {
            return false
        }
        val uniqueLocal = addr.address.size == 16 && (addr.address[0].toInt() and 0xfe) == 0xfc
        return addr.isSiteLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress || uniqueLocal
    }

    /** Numeric only, so a hostname can never be resolved here. */
    private fun isIpLiteral(host: String): Boolean =
        host.contains(':') || IPV4.matches(host)

    private fun parse(uri: String): URI? = try {
        val u = URI(uri)
        if (u.scheme == null || u.host == null) null else u
    } catch (_: Exception) {
        null
    }

    private val IPV4 = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")
}
