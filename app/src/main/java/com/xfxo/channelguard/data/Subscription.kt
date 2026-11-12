package com.xfxo.channelguard.data

import org.json.JSONArray
import org.json.JSONObject

data class Subscription(
    val id: String,
    val name: String,
    val author: String = "",
    val version: Int = 1,
    val updateUrl: String = "",
    val updatedAt: String = "",
    val enabled: Boolean = true,
    val groups: List<SubscriptionGroup> = emptyList()
) {
    val ruleCount: Int get() = groups.sumOf { it.rules.size }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("author", author); put("version", version)
        put("updateUrl", updateUrl); put("updatedAt", updatedAt); put("enabled", enabled)
        put("groups", JSONArray().apply { groups.forEach { put(it.toJson()) } })
    }

    fun withEnabled(value: Boolean) = copy(enabled = value)

    companion object {
        fun parse(text: String, fallbackUrl: String = ""): Subscription? = runCatching {
            val root = JSONObject(text)
            val name = root.optString("name").ifBlank { "未命名订阅" }
            val id = root.optString("id").ifBlank {
                val source = fallbackUrl.ifBlank { name }
                "sub-${source.hashCode().toUInt().toString(16)}"
            }
            val groupArray = root.optJSONArray("groups") ?: root.optJSONArray("rules")?.let { rules ->
                JSONArray().put(JSONObject().put("key", "default").put("name", "默认分组").put("rules", rules))
            } ?: return null
            val groups = buildList {
                for (i in 0 until groupArray.length()) {
                    groupArray.optJSONObject(i)?.let(SubscriptionGroup::fromJson)?.let(::add)
                }
            }
            if (groups.isEmpty()) return null
            Subscription(
                id, name, root.optString("author"), root.optInt("version", 1),
                root.optString("updateUrl").ifBlank { fallbackUrl }, root.optString("updatedAt"),
                root.optBoolean("enabled", true), groups
            )
        }.getOrNull()
    }
}

data class SubscriptionGroup(
    val key: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    val rules: List<SubscriptionRule> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key); put("name", name); put("description", description); put("enabled", enabled)
        put("rules", JSONArray().apply { rules.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(json: JSONObject): SubscriptionGroup {
            val key = json.optString("key").ifBlank { json.optString("id") }.ifBlank { "group" }
            val array = json.optJSONArray("rules") ?: JSONArray()
            val rules = buildList {
                for (i in 0 until array.length()) array.optJSONObject(i)?.let(SubscriptionRule::fromJson)?.let(::add)
            }
            return SubscriptionGroup(
                key, json.optString("name").ifBlank { key },
                json.optString("description").ifBlank { json.optString("desc") },
                json.optBoolean("enabled", true), rules
            )
        }
    }
}

data class SubscriptionRule(
    val key: String,
    val name: String,
    val packages: List<String> = emptyList(),
    val channelNames: List<String> = emptyList(),
    val channelIds: List<String> = emptyList(),
    val excludeKeywords: List<String> = emptyList(),
    val matchAll: Boolean = false,
    val enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key); put("name", name); put("packages", JSONArray(packages))
        put("channelNames", JSONArray(channelNames)); put("channelIds", JSONArray(channelIds))
        put("excludeKeywords", JSONArray(excludeKeywords)); put("matchAll", matchAll); put("enabled", enabled)
    }

    companion object {
        fun fromJson(json: JSONObject): SubscriptionRule? {
            val key = json.optString("key").ifBlank { json.optString("id") }.ifBlank { return null }
            return SubscriptionRule(
                key, json.optString("name").ifBlank { key },
                json.stringArray("packages", "apps", "appPackages"),
                json.stringArray("channelNames", "nameKeywords"),
                json.stringArray("channelIds", "idKeywords"),
                json.stringArray("excludeKeywords", "exclude"),
                json.optBoolean("matchAll", false),
                json.optBoolean("enabled", true)
            )
        }

        private fun JSONObject.stringArray(vararg keys: String): List<String> {
            val array = keys.asSequence().mapNotNull(::optJSONArray).firstOrNull() ?: return emptyList()
            return buildList {
                for (i in 0 until array.length()) array.optString(i).trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }
    }
}

data class SubscriptionMatch(
    val subscriptionId: String,
    val subscriptionName: String,
    val groupName: String,
    val ruleName: String
)
