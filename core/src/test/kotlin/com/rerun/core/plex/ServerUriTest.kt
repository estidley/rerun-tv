package com.rerun.core.plex

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerUriTest {
    @Test
    fun httpsAndLanHttpAreAcceptable() {
        assertTrue(ServerUri.isAcceptable("https://plex.tv"))
        assertTrue(ServerUri.isAcceptable("https://192-168-1-20.abc.plex.direct:32400"))
        assertTrue(ServerUri.isAcceptable("http://192.168.1.20:32400"))
        assertTrue(ServerUri.isAcceptable("http://10.1.2.3:32400"))
        assertTrue(ServerUri.isAcceptable("http://127.0.0.1:32400"))
        assertTrue(ServerUri.isAcceptable("http://[::1]:32400"))
        assertTrue(ServerUri.isAcceptable("http://localhost:32400"))
        assertTrue(ServerUri.isAcceptable("http://living-room.local:32400"))
    }

    @Test
    fun publicHttpAndOddSchemesAreRejected() {
        assertFalse(ServerUri.isAcceptable("http://203.0.113.5:32400"))
        assertFalse(ServerUri.isAcceptable("http://evil.example:32400"))
        assertFalse(ServerUri.isAcceptable("http://192.168.1.20.evil.example:32400"))
        assertFalse(ServerUri.isAcceptable("http://evil.com.local:32400"))
        assertFalse(ServerUri.isAcceptable("https://user:token@plex.example:32400"))
        assertFalse(ServerUri.isAcceptable("file:///tmp/library"))
        assertFalse(ServerUri.isAcceptable("https://plex.example/extra?x=1"))
    }

    @Test
    fun cleartextIsLocalOnly() {
        assertTrue(ServerUri.allowsCleartext("192.168.1.20"))
        assertTrue(ServerUri.allowsCleartext("10.1.2.3"))
        assertTrue(ServerUri.allowsCleartext("127.0.0.1"))
        assertTrue(ServerUri.allowsCleartext("::1"))
        assertTrue(ServerUri.allowsCleartext("[::1]"))
        assertTrue(ServerUri.allowsCleartext("localhost"))
        assertTrue(ServerUri.allowsCleartext("living-room.local"))
        assertTrue(ServerUri.allowsCleartext("fe80::1"))
        assertFalse(ServerUri.allowsCleartext("203.0.113.5"))
        assertFalse(ServerUri.allowsCleartext("evil.example"))
        assertFalse(ServerUri.allowsCleartext("evil.com.local"))
        assertFalse(ServerUri.allowsCleartext("plex.tv"))
        assertFalse(ServerUri.allowsCleartext("2130706433"))
        assertFalse(ServerUri.allowsCleartext("100.64.0.1"))
    }

    @Test
    fun tokenStaysOffUnrelatedHosts() {
        assertTrue(ServerUri.mayCarryToken("https", "plex.tv", null))
        assertFalse(ServerUri.shouldAttachToken("https", "plex.tv", null))
        assertTrue(ServerUri.shouldAttachToken("https", "abc.plex.direct", null))
        assertTrue(ServerUri.shouldAttachToken("http", "192.168.1.20", null))
        assertFalse(ServerUri.mayCarryToken("https", "evil.example", "plex.example"))
        assertTrue(ServerUri.shouldAttachToken("https", "plex.example", "plex.example"))
        assertFalse(ServerUri.mayCarryToken("http", "plex.example", "plex.example"))
    }
}
