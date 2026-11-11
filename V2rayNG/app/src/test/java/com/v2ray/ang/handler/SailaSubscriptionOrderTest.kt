package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SailaSubscriptionOrderTest {

    @Test
    fun visibleTabsTakeTheNewOrder() {
        assertEquals(listOf("c", "a", "b"), mergeSubscriptionOrder(listOf("a", "b", "c"), listOf("c", "a", "b")))
    }

    @Test
    fun hiddenSubscriptionKeepsItsSlot() {
        // "default" is hidden while empty, so it is missing from the tabs.
        assertEquals(
            listOf("default", "b", "a"),
            mergeSubscriptionOrder(listOf("default", "a", "b"), listOf("b", "a"))
        )
    }

    @Test
    fun allTabAndUnknownIdsAreIgnored() {
        assertEquals(listOf("b", "a"), mergeSubscriptionOrder(listOf("a", "b"), listOf("", "b", "gone", "a")))
    }

    @Test
    fun emptyOrSingleOrderChangesNothing() {
        val current = listOf("a", "b")
        assertEquals(current, mergeSubscriptionOrder(current, emptyList()))
        assertEquals(current, mergeSubscriptionOrder(current, listOf("b")))
        assertEquals(emptyList<String>(), mergeSubscriptionOrder(emptyList(), listOf("a", "b")))
    }

    @Test
    fun subscriptionLinkIgnoresNameAndSpaces() {
        assertEquals(
            "https://p.example/sub/abc",
            subscriptionUrlKey("  https://p.example/sub/abc#My%20Panel ")
        )
        assertEquals(subscriptionUrlKey("https://p.example/sub/abc"), subscriptionUrlKey("https://p.example/sub/abc#x"))
    }

    @Test
    fun blankLinkHasNoKey() {
        assertNull(subscriptionUrlKey(null))
        assertNull(subscriptionUrlKey("   "))
        assertNull(subscriptionUrlKey("#only-a-name"))
    }
}
