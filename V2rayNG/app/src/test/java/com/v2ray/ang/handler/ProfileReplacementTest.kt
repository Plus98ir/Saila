package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProfileReplacementTest {

    @Test
    fun `prefers a full match over a remarks-only match`() {
        val profiles = linkedMapOf(
            "remarks" to profile(remarks = "selected", server = "other"),
            "full" to profile(remarks = "selected", server = "host", port = "443", password = "secret"),
        )

        val result = ProfileReplacement.findSelectedReplacement(
            profiles = profiles,
            currentSelection = "old",
            selectedProfile = profile(
                remarks = " selected ",
                server = "HOST",
                port = "443",
                password = "secret",
            ),
        )

        assertEquals("full", result)
    }

    @Test
    fun `uses a remarks match when no full match exists`() {
        val profiles = linkedMapOf(
            "other" to profile(remarks = "other", server = "host"),
            "remarks" to profile(remarks = "selected", server = "other"),
        )

        val result = ProfileReplacement.findSelectedReplacement(
            profiles = profiles,
            currentSelection = "old",
            selectedProfile = profile(remarks = "selected", server = "host"),
        )

        assertEquals("remarks", result)
    }

    @Test
    fun `matches endpoint and password when remarks are unavailable`() {
        val profiles = linkedMapOf(
            "endpoint" to profile(server = "host", port = "443", password = "secret"),
            "other" to profile(server = "other", port = "443", password = "secret"),
        )

        val result = ProfileReplacement.findSelectedReplacement(
            profiles = profiles,
            currentSelection = "old",
            selectedProfile = profile(server = "HOST", port = "443", password = "secret"),
        )

        assertEquals("endpoint", result)
    }

    @Test
    fun `falls back to the first replacement profile`() {
        val profiles = linkedMapOf(
            "first" to profile(server = "first"),
            "second" to profile(server = "second"),
        )

        val result = ProfileReplacement.findSelectedReplacement(
            profiles = profiles,
            currentSelection = "old",
            selectedProfile = profile(server = "unmatched"),
        )

        assertEquals("first", result)
    }

    @Test
    fun `selects the first profile when there is no current selection`() {
        val result = ProfileReplacement.findSelectedReplacement(
            profiles = linkedMapOf(
                "first" to profile(server = "first"),
                "second" to profile(server = "second"),
            ),
            currentSelection = null,
            selectedProfile = null,
        )

        assertEquals("first", result)
    }

    @Test
    fun `keeps an existing selection when it is outside the replaced group`() {
        val result = ProfileReplacement.findSelectedReplacement(
            profiles = mapOf("candidate" to profile(server = "host")),
            currentSelection = "other-group",
            selectedProfile = null,
        )

        assertNull(result)
    }

    @Test
    fun `removes only unreferenced superseded payloads`() {
        val result = ProfileReplacement.findRemovablePayloads(
            replacedServers = listOf("orphan", "selected", "replacement", "cross-group"),
            replacementServers = setOf("replacement"),
            protectedServer = "selected",
            serversReferencedByOtherGroups = setOf("cross-group"),
        )

        assertEquals(setOf("orphan"), result)
    }

    @Test
    fun `keeps all payloads when another group index is unreadable`() {
        val result = ProfileReplacement.findRemovablePayloads(
            replacedServers = listOf("candidate"),
            replacementServers = emptySet(),
            protectedServer = null,
            serversReferencedByOtherGroups = null,
        )

        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun `same server keeps its old profile after a subscription update`() {
        val old = linkedMapOf(
            "oldA" to profile(remarks = "DE-12GB", server = "a.host", port = "443", password = "p"),
            "oldB" to profile(remarks = "TR", server = "b.host", port = "443", password = "p"),
            "oldGone" to profile(remarks = "NL", server = "gone.host", port = "443", password = "p"),
        )
        val new = linkedMapOf(
            // Name changed (remaining traffic), settings the same.
            "newA" to profile(remarks = "DE-11GB", server = "a.host", port = "443", password = "p").apply { addedTime = 1L },
            "newB" to profile(remarks = "TR", server = "b.host", port = "443", password = "p").apply { subscriptionId = "sub" },
            // Same name, other port: a different server, no ping carried over.
            "newC" to profile(remarks = "NL", server = "gone.host", port = "8443", password = "p"),
        )

        val result = ProfileReplacement.findSameServers(old, new)

        assertEquals(mapOf("newA" to "oldA", "newB" to "oldB"), result)
    }

    @Test
    fun `reality server with another shortId keeps its old profile`() {
        val old = linkedMapOf("old" to profile(remarks = "TR Reality", server = "h", port = "443").apply { shortId = "ab" })
        val new = linkedMapOf("new" to profile(remarks = "TR Reality", server = "h", port = "443").apply { shortId = "cd" })

        assertEquals(mapOf("new" to "old"), ProfileReplacement.findSameServers(old, new))
    }

    @Test
    fun `each old profile is matched once`() {
        val old = linkedMapOf("old" to profile(remarks = "x", server = "h", port = "1"))
        val new = linkedMapOf(
            "first" to profile(remarks = "y", server = "h", port = "1"),
            "second" to profile(remarks = "x", server = "h", port = "1"),
        )

        val result = ProfileReplacement.findSameServers(old, new)

        assertEquals(mapOf("second" to "old"), result)
    }

    private fun profile(
        remarks: String = "",
        server: String = "",
        port: String = "",
        password: String = "",
    ) = ProfileItem.create(EConfigType.VMESS).apply {
        this.remarks = remarks
        this.server = server
        this.serverPort = port
        this.password = password
    }
}
