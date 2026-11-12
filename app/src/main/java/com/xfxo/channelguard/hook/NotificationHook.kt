package com.xfxo.channelguard.hook

import android.app.NotificationChannel
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.xfxo.channelguard.data.RuleRepository

object NotificationHook : YukiBaseHooker() {
    private val rules by lazy { RuleRepository() }

    override fun onHook() {
        val serviceClass = Class.forName("com.android.server.notification.NotificationManagerService")
        val channelClass = NotificationChannel::class.java

        serviceClass.declaredMethods.firstOrNull {
            it.name == "createNotificationChannels" && it.parameterTypes.size in 5..8
        }?.hook {
            before {
                val pkg = args.filterIsInstance<String>().firstOrNull() ?: return@before
                val channels = args.firstOrNull { value ->
                    value is Iterable<*> || value?.javaClass?.isArray == true
                } ?: return@before

                channels.asSequence().forEach { channel ->
                    if (channel == null || !channelClass.isInstance(channel)) return@forEach
                    val channelId = runCatching {
                        channelClass.getMethod("getId").invoke(channel) as? String
                    }.getOrNull() ?: return@forEach

                    val channelName = runCatching {
                        channelClass.getMethod("getName").invoke(channel)?.toString() ?: ""
                    }.getOrNull() ?: ""

                    // 优先级1：手动规则（用户手动设置过的频道）
                    val manuallyBlocked = rules.isBlocked(pkg, channelId)

                    // 优先级2：智能规则（自动匹配营销频道）
                    val smartMatched = !manuallyBlocked &&
                        rules.isMatchedBySubscription(pkg, channelName, channelId)

                    if (manuallyBlocked || smartMatched) {
                        // 将 importance 设为 0（IMPORTANCE_NONE），
                        // 系统会据此关闭频道，后续通知不会再投递到该频道。
                        // 不在 enqueueNotificationInternal 拦截，避免 result=null 引发 NPE 风险。
                        runCatching {
                            channelClass.getDeclaredField("mImportance").apply {
                                isAccessible = true
                                setInt(channel, 0)
                            }
                        }.recoverCatching {
                            channelClass.getMethod("setImportance", Int::class.javaPrimitiveType)
                                .invoke(channel, 0)
                        }
                    }
                }
            }
        }
    }
}

private fun Any.asSequence(): Sequence<Any?> = when {
    this is Iterable<*> -> asSequence()
    javaClass.isArray -> sequence {
        for (index in 0 until java.lang.reflect.Array.getLength(this@asSequence)) {
            yield(java.lang.reflect.Array.get(this@asSequence, index))
        }
    }
    else -> emptySequence()
}
