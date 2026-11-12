package com.xfxo.channelguard.data

import android.app.Application
import android.content.Context
import com.highcapable.yukihookapi.hook.factory.preferences
import com.highcapable.yukihookapi.hook.xposed.preference.YukiHookPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object PrefsHelper {
    private const val PREFS_NAME = "ChannelGuard"
    private const val OBSERVED_PREFIX = "observed_channels:"
    private const val SUBSCRIPTIONS_KEY = "subscriptions:v1"
    private const val AUTOMATIC_ENABLED_KEY = "automatic_enabled"

    @Volatile
    private var applicationContext: Context? = null
    private val lastObservedChannels = ConcurrentHashMap<String, List<ChannelInfo>>()

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun readBlockRules(): Map<String, Boolean> {
        val values = readAll()
        return values.mapNotNull { (key, value) ->
            if (key.startsWith(OBSERVED_PREFIX) || '/' !in key) {
                null
            } else {
                val blocked = when (value) {
                    is Boolean -> value
                    is String -> value.toBooleanStrictOrNull()
                    else -> null
                }
                blocked?.let { key to it }
            }
        }.toMap()
    }

    /** 读取单条规则，比 readBlockRules() 遍历全量更高效 */
    fun readBlockRule(pkg: String, channelId: String): Boolean? {
        val key = ruleKey(pkg, channelId)
        return runCatching {
            val prefs = fallbackPrefs() ?: return null
            if (!prefs.contains(key)) return null
            runCatching { prefs.getBoolean(key, false) }.getOrElse {
                runCatching { prefs.getString(key).toBooleanStrictOrNull() }.getOrNull()
            }
        }.getOrNull()
    }

    fun writeBlockRule(pkg: String, channelId: String, blocked: Boolean) {
        val key = ruleKey(pkg, channelId)
        edit { putBoolean(key, blocked) }
    }

    fun removeBlockRule(pkg: String, channelId: String) {
        val key = ruleKey(pkg, channelId)
        edit { remove(key) }
    }

    fun readObservedChannels(pkg: String): List<ChannelInfo> {
        val raw = readString("$OBSERVED_PREFIX$pkg") ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList(json.length()) {
                for (index in 0 until json.length()) {
                    val item = json.optJSONObject(index) ?: continue
                    add(
                        ChannelInfo(
                            pkg = item.optString("pkg", pkg),
                            channelId = item.optString("channelId"),
                            name = item.optString("name"),
                            importance = item.optInt("importance", 0),
                            description = if (item.has("description") && !item.isNull("description")) {
                                item.optString("description")
                            } else {
                                null
                            }
                        )
                    )
                }
            }.filter { it.channelId.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    fun writeObservedChannels(pkg: String, channels: List<ChannelInfo>) {
        if (lastObservedChannels[pkg] == channels) return
        val json = JSONArray()
        channels.forEach { channel ->
            json.put(
                JSONObject().apply {
                    put("pkg", channel.pkg)
                    put("channelId", channel.channelId)
                    put("name", channel.name)
                    put("importance", channel.importance)
                    put("description", channel.description)
                }
            )
        }
        val key = "$OBSERVED_PREFIX$pkg"
        val value = json.toString()
        if (edit { putString(key, value) }) {
            lastObservedChannels[pkg] = channels.toList()
        }
    }

    fun readSubscriptions(): List<Subscription> {
        val raw = readString(SUBSCRIPTIONS_KEY) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { Subscription.parse(it.toString()) }?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun writeSubscriptions(subscriptions: List<Subscription>) {
        val array = JSONArray()
        subscriptions.forEach { array.put(it.toJson()) }
        edit { putString(SUBSCRIPTIONS_KEY, array.toString()) }
    }

    fun readAutomaticEnabled(): Boolean = readBoolean(AUTOMATIC_ENABLED_KEY) == true

    fun writeAutomaticEnabled(enabled: Boolean) {
        edit { putBoolean(AUTOMATIC_ENABLED_KEY, enabled) }
    }

    private fun readBoolean(key: String): Boolean? = runCatching {
        val prefs = fallbackPrefs() ?: return null
        if (!prefs.contains(key)) return null
        runCatching { prefs.getBoolean(key, false) }.getOrElse {
            runCatching { prefs.getString(key).toBooleanStrictOrNull() }.getOrNull()
        }
    }.getOrNull()

    private fun ruleKey(pkg: String, channelId: String) = "$pkg/$channelId"

    private fun readString(key: String): String? = runCatching {
        fallbackPrefs()?.takeIf { it.contains(key) }?.getString(key)
    }.getOrNull()

    private fun readAll(): Map<String, Any?> =
        runCatching { fallbackPrefs()?.all() ?: emptyMap() }.getOrDefault(emptyMap())

    private fun edit(block: YukiHookPreferences.Editor.() -> Unit): Boolean =
        runCatching {
            val prefs = fallbackPrefs() ?: return false
            prefs.edit().apply {
                block(this)
                apply()
            }
            true
        }.getOrDefault(false)

    private fun fallbackPrefs(): YukiHookPreferences? =
        resolveContext()?.preferences(PREFS_NAME)

    private fun resolveContext(): Context? {
        applicationContext?.let { return it }
        return runCatching {
            val method = Class.forName("android.app.ActivityThread")
                .getDeclaredMethod("currentApplication")
            (method.invoke(null) as? Application)?.applicationContext
        }.getOrNull()
    }

}
