package com.xfxo.channelguard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelMatcherTest {
    private val rule = SubscriptionRule(
        key = "marketing",
        name = "营销频道",
        packages = listOf("com.example.*"),
        channelNames = listOf("promo", "推广"),
        excludeKeywords = listOf("payment", "支付")
    )
    private val subscription = Subscription(
        id = "test-sub",
        name = "测试订阅",
        groups = listOf(SubscriptionGroup("apps", "应用规则", rules = listOf(rule)))
    )

    @Test
    fun matchesPackageAndChannelAndReturnsSource() {
        val match = ChannelMatcher.match("com.example.shop", "PROMO updates", "news", listOf(subscription))
        assertEquals("测试订阅", match?.subscriptionName)
        assertEquals("营销频道", match?.ruleName)
    }

    @Test
    fun excludesTakePrecedenceAndPackageMustMatch() {
        assertNull(ChannelMatcher.match("com.example.shop", "Promo payment", "news", listOf(subscription)))
        assertNull(ChannelMatcher.match("com.other.shop", "Promo updates", "news", listOf(subscription)))
    }

    @Test
    fun disabledSubscriptionGroupAndRuleDoNotMatch() {
        assertNull(ChannelMatcher.match("com.example.shop", "Promo", "news", listOf(subscription.copy(enabled = false))))
        assertNull(
            ChannelMatcher.match(
                "com.example.shop", "Promo", "news",
                listOf(subscription.copy(groups = listOf(subscription.groups.first().copy(enabled = false))))
            )
        )
        assertNull(
            ChannelMatcher.match(
                "com.example.shop", "Promo", "news",
                listOf(subscription.copy(groups = listOf(subscription.groups.first().copy(rules = listOf(rule.copy(enabled = false))))))
            )
        )
    }

    @Test
    fun emptyChannelFiltersDoNotMatchUnlessMatchAllIsExplicit() {
        val broadRule = rule.copy(packages = listOf("com.example.shop"), channelNames = emptyList())
            .copy(channelIds = emptyList())
        val broadSubscription = subscription.copy(
            groups = listOf(subscription.groups.first().copy(rules = listOf(broadRule)))
        )
        assertNull(ChannelMatcher.match("com.example.shop", "Alerts", "general", listOf(broadSubscription)))
        assertEquals(
            "营销频道",
            ChannelMatcher.match(
                "com.example.shop",
                "Alerts",
                "general",
                listOf(broadSubscription.copy(groups = listOf(
                    broadSubscription.groups.first().copy(rules = listOf(broadRule.copy(matchAll = true)))
                )))
            )?.ruleName
        )
    }

    @Test
    fun parsesAndSerializesSubscription() {
        val parsed = Subscription.parse(subscription.toJson().toString())
        assertEquals(subscription, parsed)
        assertTrue(parsed!!.ruleCount == 1)
    }
}
