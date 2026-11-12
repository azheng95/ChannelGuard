package com.xfxo.channelguard.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 主动应用规则到已有频道。
 *
 * PermissionHook 让模块 UI 进程能以系统身份调用 NotificationManager API，
 * 此工具类封装「读取所有 App 频道 → 判断规则 → 修改频道 importance」的完整流程。
 *
 * 用途：
 * 1. 智能模式开启时，立即扫描所有已安装 App，关闭匹配的频道（无需等 App 重启）
 * 2. 规则同步后，重新应用新规则到所有频道
 */
object RuleApplier {
    private data class ChannelAccessors(
        val reader: Method?,
        val updater: Method?
    )

    private val accessors = ConcurrentHashMap<Class<*>, ChannelAccessors>()

    /**
     * 扫描所有已安装 App，对匹配规则的频道设 importance=0。
     *
     * @param onProgress 进度回调 (当前索引, 总数, 当前包名)
     * @return 总共处理的频道数
     */
    suspend fun applyRulesToAllApps(
        context: Context,
        repository: RuleRepository,
        onProgress: ((current: Int, total: Int, pkg: String) -> Unit)? = null
    ): ApplyResult = withContext(Dispatchers.IO) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val packageManager = context.packageManager

        // 获取所有已安装应用
        val apps = packageManager.getInstalledApplications(0)
        val progressInterval = maxOf(1, apps.size / 100)
        var totalChannels = 0
        var attemptedChannels = 0
        var errorCount = 0

        apps.forEachIndexed { index, appInfo ->
            if (index == 0 || (index + 1) % progressInterval == 0 || index == apps.lastIndex) {
                onProgress?.invoke(index + 1, apps.size, appInfo.packageName)
            }

            runCatching {
                // 读取该 App 的所有频道（需要 PermissionHook 提供系统权限）
                val channels = getChannels(notificationManager, appInfo.packageName, appInfo.uid)

                totalChannels += channels.size

                channels.forEach { channel ->
                    // 跳过已经关闭的频道
                    if (channel.importance == NotificationManager.IMPORTANCE_NONE) return@forEach

                    val channelId = channel.id
                    val channelName = channel.name?.toString() ?: ""

                    if (repository.shouldBlockChannel(appInfo.packageName, channelName, channelId)) {
                        channel.importance = NotificationManager.IMPORTANCE_NONE
                        attemptedChannels++
                        if (!updateChannel(notificationManager, appInfo.packageName, appInfo.uid, channel)) {
                            errorCount++
                        }
                    }
                }
            }.onFailure {
                errorCount++
            }
        }

        ApplyResult(
            totalApps = apps.size,
            totalChannels = totalChannels,
            attemptedChannels = attemptedChannels,
            errorCount = errorCount
        )
    }

    /**
     * 对单个 App 应用规则。
     */
    suspend fun applyRulesToApp(
        context: Context,
        pkg: String,
        repository: RuleRepository
    ): Int = withContext(Dispatchers.IO) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val packageManager = context.packageManager

        val appInfo = runCatching {
            packageManager.getApplicationInfo(pkg, 0)
        }.getOrNull() ?: return@withContext 0

        var appliedCount = 0

        runCatching {
            val channels = getChannels(notificationManager, pkg, appInfo.uid)

            channels.forEach { channel ->
                if (channel.importance == NotificationManager.IMPORTANCE_NONE) return@forEach

                val channelId = channel.id
                val channelName = channel.name?.toString() ?: ""

                if (repository.shouldBlockChannel(pkg, channelName, channelId)) {
                    channel.importance = NotificationManager.IMPORTANCE_NONE
                    if (updateChannel(notificationManager, pkg, appInfo.uid, channel)) {
                        appliedCount++
                    }
                }
            }
        }

        appliedCount
    }

    data class ApplyResult(
        val totalApps: Int,
        val totalChannels: Int,
        val attemptedChannels: Int,
        val errorCount: Int
    )

    private fun accessors(manager: NotificationManager): ChannelAccessors =
        accessors.computeIfAbsent(manager.javaClass) { managerClass ->
            ChannelAccessors(
                reader = runCatching {
                    managerClass.getMethod(
                        "getNotificationChannelsForPackage",
                        String::class.java,
                        Int::class.javaPrimitiveType
                    )
                }.getOrNull(),
                updater = runCatching {
                    managerClass.getMethod(
                        "updateNotificationChannelForPackage",
                        String::class.java,
                        Int::class.javaPrimitiveType,
                        NotificationChannel::class.java
                    )
                }.getOrNull()
            )
        }

    private fun getChannels(manager: NotificationManager, pkg: String, uid: Int): List<NotificationChannel> {
        val reader = accessors(manager).reader ?: error("getNotificationChannelsForPackage unavailable")
        val result = reader.invoke(manager, pkg, uid) as? List<*>
            ?: error("getNotificationChannelsForPackage returned an unexpected value")
        return result.filterIsInstance<NotificationChannel>()
    }

    private fun updateChannel(
        manager: NotificationManager,
        pkg: String,
        uid: Int,
        channel: NotificationChannel
    ): Boolean = runCatching {
        val updater = accessors(manager).updater ?: error("updateNotificationChannelForPackage unavailable")
        updater.invoke(manager, pkg, uid, channel)
    }.isSuccess
}
