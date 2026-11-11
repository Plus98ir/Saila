package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SailaRowLabelTest {

    private fun custom(network: String?) = ProfileItem.create(EConfigType.CUSTOM).apply { this.network = network }

    @Test
    fun masqueAndXdriveRowsShowTheirName() {
        assertEquals("MASQUE", serverProtocolDescription(custom("masque")))
        assertEquals("XDRIVE", serverProtocolDescription(custom("xdrive")))
    }

    @Test
    fun otherCustomRowsStayCustom() {
        assertEquals("CUSTOM", serverProtocolDescription(custom(null)))
        assertEquals("CUSTOM", serverProtocolDescription(custom("ws")))
    }
}
