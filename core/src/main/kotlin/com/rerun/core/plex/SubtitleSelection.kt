package com.rerun.core.plex

import java.util.Locale

/** Plex reports both two- and three-letter ISO language codes. */
fun subtitleLanguageCode(value: String): String {
    val code = value.lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')
    return when (code) {
        "fre" -> "fra"
        "ger" -> "deu"
        "dut" -> "nld"
        "chi" -> "zho"
        "cze" -> "ces"
        "gre" -> "ell"
        "rum" -> "ron"
        "slo" -> "slk"
        "wel" -> "cym"
        "baq" -> "eus"
        "per" -> "fas"
        "ice" -> "isl"
        "alb" -> "sqi"
        "arm" -> "hye"
        "bur" -> "mya"
        "geo" -> "kat"
        "mac" -> "mkd"
        "mao" -> "mri"
        "may" -> "msa"
        "tib" -> "bod"
        else -> if (code.length == 2 && code in Locale.getISOLanguages()) Locale.forLanguageTag(code).isO3Language else code
    }
}

fun selectSubtitle(streams: List<SubtitleStream>, language: String): SubtitleStream? {
    val candidates = if (language == "auto") streams else streams.filter {
        it.language != null && subtitleLanguageCode(it.language) == subtitleLanguageCode(language)
    }
    return candidates.firstOrNull { it.selected } ?: candidates.firstOrNull()
}
