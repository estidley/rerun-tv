package com.rerun.core.resolve

import com.rerun.core.catalog.Catalog
import com.rerun.core.catalog.Router
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.MediaItem
import com.rerun.core.tmdb.NetworkReference

/** Turns a channel's rules into the list of items it can play. */
object Resolver {
    const val DORMANT_MS = 4 * 3_600_000L
    const val MIN_ITEM_MS = 60_000L

    /**
     * Every network channel [item] belongs on. First, each real network TMDB says aired it
     * (see [NetworkReference]), plus the network Plex reports (alias on network, then on studio).
     * Only when neither finds anything does owner-family routing pick one channel.
     */
    fun networksOf(item: MediaItem): Set<String> {
        val aired = NetworkReference.current.channelsFor(item)
        val reported = Catalog.canonicalNetwork(item.network) ?: Catalog.canonicalNetwork(item.studio)
        val direct = when {
            reported == null -> aired
            aired.isEmpty() -> setOf(reported)
            reported in aired -> aired
            else -> aired + reported
        }
        if (direct.isNotEmpty()) return direct
        return Router.route(item)?.let { setOf(it) } ?: emptySet()
    }

    /** The lowest-numbered channel in [networksOf], for places that show a single network. */
    fun networkOf(item: MediaItem): String? = networksOf(item).minByOrNull { Catalog.numberOf(it) }

    /** True when the item belongs on the channel outside of any time block. */
    fun inBase(def: ChannelDef, item: MediaItem): Boolean =
        def.rules.matches(item, if (def.rules.networks.isEmpty()) emptySet() else networksOf(item))

    /**
     * Everything the channel may ever play: its base rules plus anything its time blocks pull in
     * (e.g. Toonami shows during Cartoon Network's afternoon block).
     */
    fun pool(def: ChannelDef, library: List<MediaItem>): List<MediaItem> {
        val needsNetwork = def.rules.networks.isNotEmpty() || def.blocks.any { it.rules.networks.isNotEmpty() }
        return library.filter { item ->
            if (item.durationMs < MIN_ITEM_MS) return@filter false
            val nids = if (needsNetwork) networksOf(item) else emptySet()
            def.rules.matches(item, nids) || def.blocks.any { it.rules.matches(item, nids) }
        }
    }

    fun isDormant(pool: List<MediaItem>): Boolean = pool.sumOf { it.durationMs } < DORMANT_MS
}
