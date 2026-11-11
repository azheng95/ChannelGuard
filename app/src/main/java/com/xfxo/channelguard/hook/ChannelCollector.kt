package com.xfxo.channelguard.hook

import android.app.NotificationChannel
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.xfxo.channelguard.data.ChannelInfo
import com.xfxo.channelguard.data.PrefsHelper

object ChannelCollector : YukiBaseHooker() {
    private data class ChannelAccessors(
        val id: Method?,
        val name: Method?,
        val importance: Method?,
        val description: Method?
    )

    private val accessors = ConcurrentHashMap<Class<*>, ChannelAccessors>()

    override fun onHook() {
        val serviceClass = Class.forName("com.android.server.notification.NotificationManagerService")

        serviceClass.declaredMethods.firstOrNull {
            it.name == "getNotificationChannelsForPackage" && it.parameterTypes.size in 2..8
        }?.hook {
            after {
                val pkg = args.filterIsInstance<String>().firstOrNull() ?: return@after
                val channels = extractChannels(result) ?: return@after
                val observed = channels.mapNotNull { channel -> channel.toChannelInfo(pkg) }
                if (observed.isNotEmpty()) {
                    PrefsHelper.writeObservedChannels(pkg, observed)
                }
            }
        }
    }

    private fun extractChannels(value: Any?): List<Any?>? = when {
        value == null -> null
        value is Iterable<*> -> value.toList()
        value.javaClass.isArray -> (0 until java.lang.reflect.Array.getLength(value))
            .map { java.lang.reflect.Array.get(value, it) }
        else -> runCatching {
            val list = value.javaClass.getMethod("getList").invoke(value)
            (list as? Iterable<*>)?.toList()
        }.getOrNull()
    }

    private fun Any?.toChannelInfo(pkg: String): ChannelInfo? {
        val channel = this ?: return null
        if (!NotificationChannel::class.java.isInstance(channel)) return null

        val methods = accessors.computeIfAbsent(channel.javaClass) { type ->
            ChannelAccessors(
                id = type.methodOrNull("getId"),
                name = type.methodOrNull("getName"),
                importance = type.methodOrNull("getImportance"),
                description = type.methodOrNull("getDescription")
            )
        }
        val id = methods.id.invokeOrNull(channel) as? String ?: return null
        val name = methods.name.invokeOrNull(channel)?.toString().orEmpty()
        val importance = (methods.importance.invokeOrNull(channel) as? Number)?.toInt() ?: 0
        val description = methods.description.invokeOrNull(channel)?.toString()

        return ChannelInfo(
            pkg = pkg,
            channelId = id,
            name = name,
            importance = importance,
            description = description
        )
    }

    private fun Class<*>.methodOrNull(name: String): Method? =
        runCatching { getMethod(name) }.getOrNull()

    private fun Method?.invokeOrNull(target: Any): Any? =
        runCatching { this?.invoke(target) }.getOrNull()
}
