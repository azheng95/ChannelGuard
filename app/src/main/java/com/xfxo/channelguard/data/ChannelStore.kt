package com.xfxo.channelguard.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class ChannelInfo(
    val pkg: String,
    val channelId: String,
    val name: String,
    val importance: Int,
    val description: String?
)

class ChannelStore(
    private val prefsHelper: PrefsHelper = PrefsHelper
) {
    private val channels = ConcurrentHashMap<String, List<ChannelInfo>>()
    private val flows = ConcurrentHashMap<String, MutableStateFlow<List<ChannelInfo>>>()

    fun observe(pkg: String): StateFlow<List<ChannelInfo>> =
        flows.computeIfAbsent(pkg) {
            MutableStateFlow(loadInitial(pkg))
        }.asStateFlow()

    fun update(pkg: String, newChannels: List<ChannelInfo>) {
        val merged = LinkedHashMap<String, ChannelInfo>()
        channels.computeIfAbsent(pkg) { loadInitial(pkg) }
            .forEach { merged[it.channelId] = it }
        newChannels.forEach { channel ->
            // 跳过包名不匹配的条目（防御性过滤，理论上 Hook 层保证一致）
            if (channel.pkg == pkg) merged[channel.channelId] = channel
        }
        val updated = merged.values.toList()
        channels[pkg] = updated
        flows.computeIfAbsent(pkg) { MutableStateFlow(updated) }.value = updated
        prefsHelper.writeObservedChannels(pkg, updated)
    }

    fun get(pkg: String): List<ChannelInfo> =
        channels.computeIfAbsent(pkg) { loadInitial(pkg) }.toList()

    private fun loadInitial(pkg: String): List<ChannelInfo> {
        val observed = prefsHelper.readObservedChannels(pkg)
        return observed
            .associateBy { it.channelId }
            .values
            .toList()
    }
}
