package com.xfxo.channelguard.data

object ChannelMatcher {
    fun match(pkg: String, channelName: String, channelId: String, subscriptions: List<Subscription>): SubscriptionMatch? {
        subscriptions.asSequence().filter { it.enabled }.forEach { subscription ->
            subscription.groups.asSequence().filter { it.enabled }.forEach { group ->
                group.rules.asSequence().filter { it.enabled }.forEach { rule ->
                    if (rule.packages.isNotEmpty() && rule.packages.none { packageMatches(it, pkg) }) return@forEach
                    if (rule.excludeKeywords.any { channelName.contains(it, true) || channelId.contains(it, true) }) return@forEach
                    val hasChannelFilter = rule.channelNames.isNotEmpty() || rule.channelIds.isNotEmpty()
                    val hit = if (hasChannelFilter) {
                        rule.channelNames.any { channelName.contains(it, true) } ||
                            rule.channelIds.any { channelId.contains(it, true) }
                    } else {
                        rule.matchAll
                    }
                    if (hit) return SubscriptionMatch(subscription.id, subscription.name, group.name, rule.name)
                }
            }
        }
        return null
    }

    private fun packageMatches(pattern: String, pkg: String): Boolean =
        pattern == "*" || pattern == pkg || (pattern.endsWith(".*") && pkg.startsWith(pattern.dropLast(1)))
}
