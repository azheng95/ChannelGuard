package com.xfxo.channelguard.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelMatcherTest {
    private val rule = ChannelRule(
        id = "test",
        name = "test",
        positive = listOf("promo", "推广"),
        exclude = listOf("payment", "支付")
    )

    @Test
    fun matchesNameAndChannelIdWithoutAllocatingCandidateList() {
        assertTrue(ChannelMatcher.matches("PROMO updates", "news", listOf(rule)))
        assertTrue(ChannelMatcher.matches("", "channel_推广", listOf(rule)))
        assertFalse(ChannelMatcher.matches("", "", listOf(rule)))
    }

    @Test
    fun exclusionsTakePrecedenceAndDisabledRulesDoNotMatch() {
        assertFalse(ChannelMatcher.matches("Promo payment updates", "", listOf(rule)))
        assertFalse(
            ChannelMatcher.matches(
                "Promo updates",
                "",
                listOf(rule.copy(enabled = false))
            )
        )
    }
}
