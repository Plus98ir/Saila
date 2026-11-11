package com.v2ray.ang.fmt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CustomFmtSailaTest {

    private fun config(outbound: String) = """
        {"remarks":"R","inbounds":[],"routing":{},
         "outbounds":[$outbound,{"protocol":"freedom","tag":"direct"}]}
    """.trimIndent()

    @Test
    fun masqueOutboundGivesHostPortAndKind() {
        val p = CustomFmt.parse(config("""{"protocol":"masque","settings":{"address":"tr.example","port":19157},"streamSettings":{"network":"masque"}}"""))
        assertEquals("tr.example", p.server)
        assertEquals("19157", p.serverPort)
        assertEquals("masque", p.network)
    }

    @Test
    fun xdriveOutboundIsMarked() {
        val p = CustomFmt.parse(config("""{"protocol":"vless","settings":{"address":"tr.example","port":41460},"streamSettings":{"network":"xdrive"}}"""))
        assertEquals("tr.example", p.server)
        assertEquals("xdrive", p.network)
    }

    @Test
    fun otherCustomConfigKeepsNoNetwork() {
        val p = CustomFmt.parse(config("""{"protocol":"vless","settings":{"address":"a.example","port":443},"streamSettings":{"network":"ws"}}"""))
        assertNull(p.network)
    }
}
