package com.rerun.tmdb

import com.rerun.core.catalog.Catalog
import com.rerun.core.tmdb.ChannelReference
import com.rerun.core.tmdb.NetworkReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SnapshotPublisherTest {
    private fun valid() = NetworkReference(Catalog.networks.map {
        ChannelReference(it.id, it.realName, emptyList(), emptyList(), setOf(1), emptySet())
    }, "2026-10-25", "US")

    @Test fun publishesCompleteSnapshotAndDropsUnknownFields() {
        val input = valid().toJson().dropLast(1) + ",\"privateExtra\":\"not-public\"}"
        val result = SnapshotPublisher.validate(input)
        assertEquals(Catalog.networks.size, result.channels.size)
        assertFalse(result.toJson().contains("privateExtra"))
    }

    @Test fun rejectsIncompleteOrDuplicateNetworks() {
        val all = valid()
        assertFailsWith<IllegalArgumentException> { SnapshotPublisher.validate(NetworkReference(all.channels.drop(1), all.generated, all.country).toJson()) }
        assertFailsWith<IllegalArgumentException> { SnapshotPublisher.validate(NetworkReference(all.channels + all.channels.first(), all.generated, all.country).toJson()) }
    }

    @Test fun rejectsInvalidOrEmptySnapshots() {
        for (input in listOf("{}", "not json", NetworkReference.EMPTY.toJson(),
            NetworkReference(valid().channels, "invalid", "US").toJson(),
            NetworkReference(valid().channels, "2026-10-25", "").toJson())) {
            assertFailsWith<IllegalArgumentException> { SnapshotPublisher.validate(input) }
        }
    }
}
