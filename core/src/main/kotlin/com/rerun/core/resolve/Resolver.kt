package com.rerun.core.resolve

import com.rerun.core.catalog.Catalog
import com.rerun.core.catalog.Router
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.MediaItem

/** Turns a channel's rules into the list of items it can play. */
object Resolver {
    const val DORMANT_MS = 4 * 3_600_000L
    const val MIN_ITEM_MS = 60_000L

    /** Canonical network id: alias on network, then alias on studio, then owner-family routing. */
    fun networkOf(item: MediaItem): String? =
        Catalog.canonicalNetwork(item.network)
            ?: Catalog.canonicalNetwork(item.studio)
            ?: Router.route(item)

    /** True when the item belongs on the channel outside of any time block. */
    fun inBase(def: ChannelDef, item: MediaItem): Boolean = def.rules.matches(item, networkOf(item))

    /**
     * Everything the channel may ever play: its base rules plus anything its time blocks pull in
     * (e.g. Toonami shows during Cartoon Network's afternoon block).
     */
    fun pool(def: ChannelDef, library: List<MediaItem>): List<MediaItem> =
        library.filter { item ->
            if (item.durationMs < MIN_ITEM_MS) return@filter false
            val nid = networkOf(item)
            def.rules.matches(item, nid) || def.blocks.any { it.rules.matches(item, nid) }
        }

    fun isDormant(pool: List<MediaItem>): Boolean = pool.sumOf { it.durationMs } < DORMANT_MS
}
