package com.v2ray.ang.handler

import com.v2ray.ang.enums.RoutingType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AutoRoutingManagerTest {

    @Test
    fun `each country gets its preset`() {
        assertEquals(RoutingType.WHITE_IRAN, AutoRoutingManager.presetFor("ir"))
        assertEquals(RoutingType.WHITE_RUSSIA, AutoRoutingManager.presetFor("ru"))
        assertEquals(RoutingType.WHITE, AutoRoutingManager.presetFor("cn"))
    }

    // The IP lookup can leave through a VPN or a tunnelled Wi-Fi (0.1.4-b2 put an Iranian user on
    // Germany); the cell tower's country cannot be moved that way.
    @Test
    fun `mobile network country wins over a foreign IP`() {
        var ipLooked = false
        val pick = AutoRoutingManager.pickCountry(network = "ir", sim = "ir") { ipLooked = true; "de" }
        assertEquals(AutoRoutingManager.Detected("ir", "network"), pick)
        assertEquals(false, ipLooked)
    }

    @Test
    fun `without a mobile network the IP decides`() {
        assertEquals(
            AutoRoutingManager.Detected("de", "ip"),
            AutoRoutingManager.pickCountry(network = "", sim = "ir") { "de" },
        )
    }

    @Test
    fun `SIM is used only when the IP lookup fails`() {
        assertEquals(
            AutoRoutingManager.Detected("ir", "sim"),
            AutoRoutingManager.pickCountry(network = null, sim = "IR") { null },
        )
    }

    // The phone's region setting says nothing about where it is, so it never picks the routing.
    @Test
    fun `nothing known means no change`() {
        assertEquals(null, AutoRoutingManager.pickCountry(network = null, sim = null) { null })
    }

    @Test
    fun `other countries route everything through the VPN`() {
        assertEquals(RoutingType.GLOBAL, AutoRoutingManager.presetFor("de"))
        assertEquals(RoutingType.GLOBAL, AutoRoutingManager.presetFor("us"))
        assertEquals(RoutingType.GLOBAL, AutoRoutingManager.presetFor(""))
    }
}
