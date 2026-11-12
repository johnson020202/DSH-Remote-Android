package com.dshremote.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingTest {
    private val token = "A".repeat(43)

    @Test
    fun rawTokenUsesFallbackHost() {
        val invite = Pairing.parse(token, "192.168.1.7:61698")
        assertEquals(token, invite?.token)
        assertEquals("ws://192.168.1.7:61698/ios/v1", invite?.wsUrl)
    }

    @Test
    fun rawTokenWithoutHostHasNoUrl() {
        assertNull(Pairing.parse(token)?.wsUrl)
    }

    @Test
    fun jsonInviteAdoptsUrl() {
        val invite = Pairing.parse("""{"token":"$token","url":"http://192.168.1.7:61698"}""")
        assertEquals(token, invite?.token)
        assertEquals("ws://192.168.1.7:61698/ios/v1", invite?.wsUrl)
    }

    @Test
    fun websocketUrlKeepsPathAndToken() {
        val invite = Pairing.parse("ws://192.168.1.7:61698/ios/v1?token=$token")
        assertEquals(token, invite?.token)
        assertEquals("ws://192.168.1.7:61698/ios/v1", invite?.wsUrl)
    }

    @Test
    fun deepLinkReadsUrlParameter() {
        val invite = Pairing.parse("dshremote://pair?token=$token&url=192.168.1.7:61698")
        assertEquals(token, invite?.token)
        assertEquals("ws://192.168.1.7:61698/ios/v1", invite?.wsUrl)
    }

    @Test
    fun hostWithoutPortDefaultsTo3080() {
        assertEquals("ws://192.168.1.7:3080/ios/v1", Pairing.wsUrlForHost("192.168.1.7"))
    }

    @Test
    fun blankInviteIsRejected() {
        assertNull(Pairing.parse("  "))
    }
}
