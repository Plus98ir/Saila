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

    @Test
    fun `other countries route everything through the VPN`() {
        assertEquals(RoutingType.GLOBAL, AutoRoutingManager.presetFor("de"))
        assertEquals(RoutingType.GLOBAL, AutoRoutingManager.presetFor("us"))
        assertEquals(RoutingType.GLOBAL, AutoRoutingManager.presetFor(""))
    }
}
