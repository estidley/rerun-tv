package com.rerun.tmdb

import com.rerun.core.catalog.Catalog
import com.rerun.core.tmdb.NetworkReference
import java.time.LocalDate

/** Only the public reference schema is serialized into the versioned snapshot. */
internal object SnapshotPublisher {
    fun validate(text: String): NetworkReference {
        val reference = NetworkReference.parse(text)
        require(!reference.isEmpty) { "Cannot publish an empty or unreadable TMDB snapshot." }
        require(runCatching { LocalDate.parse(reference.generated) }.isSuccess) { "Snapshot needs a generation date." }
        require(reference.country?.matches(Regex("[A-Z]{2}|ANY")) == true) { "Snapshot needs a country." }
        val expected = Catalog.networks.associate { it.id to it.realName }
        require(reference.channels.size == expected.size && reference.channels.map { it.channelId }.toSet() == expected.keys) {
            "Snapshot must contain every stock network exactly once."
        }
        for (channel in reference.channels) {
            require(channel.realName == expected[channel.channelId]) { "Unexpected network name in snapshot." }
            require((channel.shows + channel.movies).all { it > 0 }) { "TMDB IDs must be positive." }
            require((channel.networks + channel.companies).all { it.id > 0 && it.name.isNotBlank() }) {
                "Snapshot has invalid source metadata."
            }
        }
        return reference
    }
}
