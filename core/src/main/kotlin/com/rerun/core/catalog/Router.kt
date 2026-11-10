package com.rerun.core.catalog

import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind

/**
 * Owner-family routing: streaming originals and studio-only items land on the classic network
 * their owner runs, picked by audience (rating) and genre. Spec §3.2a.
 */
object Router {
    private enum class Family { DISNEY, PARAMOUNT, WARNER, NBCU, HULU, STREAMER, ABC_TV, NBC_TV, FOX_TV, CBS_TV }

    private val families: Map<String, Family> = buildMap {
        listOf(
            "Disney+", "Walt Disney Pictures", "Walt Disney Animation Studios",
            "Disney Television Animation", "Pixar", "Marvel Studios", "Marvel Animation", "Marvel Television",
            "Lucasfilm", "Lucasfilm Animation",
        ).forEach { put(key(it), Family.DISNEY) }
        listOf("Paramount+", "Nickelodeon Animation Studio", "Nickelodeon Productions", "Nickelodeon Movies")
            .forEach { put(key(it), Family.PARAMOUNT) }
        listOf("Max", "HBO Max", "Warner Bros. Animation", "Cartoon Network Studios", "Williams Street")
            .forEach { put(key(it), Family.WARNER) }
        put(key("Peacock"), Family.NBCU)
        put(key("Hulu"), Family.HULU)
        listOf("Netflix", "Prime Video", "Amazon", "Amazon Studios", "Amazon MGM Studios", "Apple TV+", "Apple TV")
            .forEach { put(key(it), Family.STREAMER) }
        listOf(
            "ABC Studios", "ABC Signature", "ABC Entertainment", "ABC Studios International",
            "Touchstone Television", "Walt Disney Television",
        ).forEach { put(key(it), Family.ABC_TV) }
        listOf("NBC Studios", "Universal Television", "Universal Media Studios", "NBCUniversal Television", "NBC Universal Television")
            .forEach { put(key(it), Family.NBC_TV) }
        listOf("20th Century Fox Television", "20th Television", "Fox Television Studios", "Fox 21 Television Studios")
            .forEach { put(key(it), Family.FOX_TV) }
        listOf(
            "CBS Studios", "CBS Television Studios", "CBS Paramount Network Television",
            "Paramount Television", "Paramount Television Studios", "Paramount Network Television",
        ).forEach { put(key(it), Family.CBS_TV) }
    }

    private fun key(s: String) = s.trim().lowercase()

    private val preschool = setOf("tv-y")
    private val kids = setOf("tv-y7", "tv-y7-fv", "tv-g", "g")
    private val family = kids + setOf("tv-pg", "pg")
    private val actionGenres = setOf("action", "adventure", "science fiction", "sci-fi & fantasy", "action & adventure")
    private val scifiGenres = setOf("science fiction", "sci-fi & fantasy", "fantasy", "horror")

    fun route(item: MediaItem): String? {
        val fam = item.network?.let { families[key(it)] }
            ?: item.studio?.let { families[key(it)] }
            ?: return null
        val rating = item.contentRating?.let(::key)
        val genres = item.genres.mapTo(HashSet(), ::key)
        val isPreschool = rating in preschool
        val isFamily = rating in family
        val animated = "animation" in genres
        val action = genres.any { it in actionGenres }
        val scifi = genres.any { it in scifiGenres }
        val comedy = "comedy" in genres
        val marvelOrLucas = listOfNotNull(item.studio, item.network).any {
            val k = key(it); k.startsWith("marvel") || k.startsWith("lucasfilm")
        }
        return when (fam) {
            Family.DISNEY -> when {
                isPreschool -> "disneyjr"
                isFamily && animated && (action || marvelOrLucas) -> "disneyxd"
                isFamily -> "disney"
                else -> "touchstone"
            }
            Family.PARAMOUNT -> when {
                isPreschool -> "nickjr"
                isFamily -> "nick"
                comedy -> "comedycentral"
                else -> "paramountnet"
            }
            Family.WARNER -> when {
                isFamily -> "cn"
                animated -> "adultswim"
                else -> "hbo"
            }
            Family.NBCU -> if (scifi) "syfy" else "usa"
            Family.HULU -> "fx"
            Family.STREAMER -> when {
                isPreschool -> "pbskids"
                isFamily && animated -> "cn"
                scifi -> "syfy"
                comedy -> "comedycentral"
                "documentary" in genres -> if ("history" in genres) "history" else "discovery"
                else -> "usa"
            }
            Family.ABC_TV -> if (item.kind == MediaKind.EPISODE) "abc" else null
            Family.NBC_TV -> if (item.kind == MediaKind.EPISODE) "nbc" else null
            Family.FOX_TV -> if (item.kind == MediaKind.EPISODE) "fox" else null
            Family.CBS_TV -> if (item.kind == MediaKind.EPISODE) "cbs" else null
        }
    }
}
