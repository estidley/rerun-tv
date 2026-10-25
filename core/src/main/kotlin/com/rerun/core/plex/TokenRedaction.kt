package com.rerun.core.plex

/** Removes X-Plex-Token from URLs and playlist text. The header is how requests carry it. */
object TokenRedaction {
    const val HEADER = "X-Plex-Token"

    private val QUERY = Regex("""(?i)([?&])X-Plex-Token=[^&\s"'#]*""")

    fun stripQuery(text: String): String {
        if (!text.contains("plex-token", ignoreCase = true)) return text
        val out = StringBuilder(text.length)
        var cursor = 0
        for (match in QUERY.findAll(text)) {
            val start = match.range.first
            if (start < cursor) continue
            out.append(text, cursor, start)
            val end = match.range.last + 1
            if (match.groupValues[1] == "?" && end < text.length && text[end] == '&') {
                out.append('?')
                cursor = end + 1
            } else {
                cursor = end
            }
        }
        if (cursor < text.length) out.append(text, cursor, text.length)
        return out.toString()
    }
}
