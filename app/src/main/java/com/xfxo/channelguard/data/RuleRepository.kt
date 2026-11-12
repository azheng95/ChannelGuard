package com.xfxo.channelguard.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class RuleRepository {
    fun isBlocked(pkg: String, channelId: String) = PrefsHelper.readBlockRule(pkg, channelId) == true

    fun setBlocked(pkg: String, channelId: String, blocked: Boolean) {
        PrefsHelper.writeBlockRule(pkg, channelId, blocked)
    }

    fun hasManualRule(pkg: String, channelId: String): Boolean =
        getAllRules().containsKey("$pkg/$channelId")
    fun getAllRules(): Map<String, Boolean> = PrefsHelper.readBlockRules()
    fun getSubscriptions(): List<Subscription> = PrefsHelper.readSubscriptions()

    fun setSubscriptionEnabled(id: String, enabled: Boolean) {
        PrefsHelper.writeSubscriptions(getSubscriptions().map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun removeSubscription(id: String) {
        PrefsHelper.writeSubscriptions(getSubscriptions().filterNot { it.id == id })
    }

    fun matchChannel(pkg: String, channelName: String, channelId: String): SubscriptionMatch? =
        ChannelMatcher.match(pkg, channelName, channelId, getSubscriptions())

    fun isMatchedBySubscription(pkg: String, channelName: String, channelId: String): Boolean =
        isAutomaticEnabled() && matchChannel(pkg, channelName, channelId) != null

    fun shouldBlockChannel(pkg: String, channelName: String, channelId: String): Boolean {
        val manualOverride = PrefsHelper.readBlockRule(pkg, channelId)
        return ChannelMatcher.resolveBlockedState(manualOverride) {
            isMatchedBySubscription(pkg, channelName, channelId)
        }
    }

    fun isAutomaticEnabled(): Boolean = PrefsHelper.readAutomaticEnabled()
    fun setAutomaticEnabled(enabled: Boolean) = PrefsHelper.writeAutomaticEnabled(enabled)

    suspend fun addSubscriptionFromUrl(url: String): ImportResult = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.connect()
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val subscription = Subscription.parse(text, url) ?: error("订阅格式无效")
            saveSubscription(subscription)
            ImportResult.Success(subscription)
        }.getOrElse { ImportResult.Failure(it.message ?: "导入失败") }
    }

    fun addSubscriptionFromText(text: String): ImportResult {
        val subscription = Subscription.parse(text) ?: return ImportResult.Failure("订阅格式无效")
        saveSubscription(subscription)
        return ImportResult.Success(subscription)
    }

    suspend fun refreshSubscription(subscription: Subscription): ImportResult =
        if (subscription.updateUrl.isBlank()) ImportResult.Failure("该订阅没有更新地址")
        else addSubscriptionFromUrl(subscription.updateUrl)

    private fun saveSubscription(subscription: Subscription) {
        val current = getSubscriptions()
        val previous = current.firstOrNull { it.id == subscription.id }
        val updated = if (previous != null) subscription.copy(enabled = previous.enabled) else subscription
        PrefsHelper.writeSubscriptions(current.filterNot { it.id == updated.id } + updated)
    }

    sealed interface ImportResult {
        data class Success(val subscription: Subscription) : ImportResult
        data class Failure(val message: String) : ImportResult
    }
}
