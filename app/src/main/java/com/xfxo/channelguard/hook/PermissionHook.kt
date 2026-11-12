package com.xfxo.channelguard.hook

import android.os.Binder
import android.util.Log
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker

/**
 * system_server 侧的通知通道权限 Hook。
 *
 * ## 目的
 *
 * 让「本模块自己发起的」Binder 调用被 NotificationManagerService 当成系统调用，
 * 从而能读写其它 App 的通知频道（getNotificationChannelsForPackage /
 * updateNotificationChannelForPackage）。
 *
 * ## 要拦截什么
 *
 * NotificationManagerService 几个权限校验方法（Android 12~17 签名稳定）：
 * - isCallerSystemOrPhone()
 * - isCallerSystem()
 * - isCallerSystemOrSystemUi()
 * - isCallerSystemOrSystemUiOrShell()
 * - assertCallerIsSystemOrSystemUiOrShell()
 *
 * 只放行「本模块自身 UID」的调用，其余进程一律透传。
 *
 * ## 为什么延迟 Hook
 *
 * onSystemServerStarting 时 NMS 还未实例化，直接 Class.forName 会 ClassNotFoundException。
 * 先 Hook SystemServiceManager#startService，等 NMS 被启动那一刻再挂上去。
 */
object PermissionHook : YukiBaseHooker() {

    @Volatile
    private var installed = false

    override fun onHook() {
        val classLoader = hostClassLoader
        val moduleUid = ModuleUid.resolve()

        if (moduleUid <= 0) {
            Log.e(TAG, "PermissionHook: module uid unresolved, abort")
            return
        }

        Log.d(TAG, "PermissionHook: moduleUid=$moduleUid")

        val waitInstalled = runCatching { installWaitHook(classLoader, moduleUid) }
            .onFailure { Log.e(TAG, "PermissionHook: wait hook failed: ${it.message}") }
            .getOrDefault(false)

        if (!waitInstalled) {
            hookTargets(classLoader, moduleUid)
        }
    }

    /**
     * 挂「等待 NMS 就绪」的钩子。
     * 优先 SystemServiceManager#startService(Class)，退化为 startBootPhase。
     */
    private fun installWaitHook(classLoader: ClassLoader?, moduleUid: Int): Boolean {
        val manager = Class.forName(SERVICE_MANAGER_CLASS, false, classLoader)

        // 优先：startService(Class) — NMS 被启动那一刻触发
        val startService = manager.declaredMethods.firstOrNull {
            it.name == "startService" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Class::class.java
        }

        if (startService != null) {
            startService.isAccessible = true
            startService.hook {
                after {
                    runCatching {
                        val started = args[0] as? Class<*> ?: return@runCatching
                        if (started.name == TARGET_CLASS) {
                            Log.d(TAG, "PermissionHook: $TARGET_CLASS started, hooking targets")
                            hookTargets(classLoader, moduleUid)
                        }
                    }.onFailure { Log.e(TAG, "PermissionHook: startService callback failed: ${it.message}") }
                }
            }
            Log.d(TAG, "PermissionHook: waiting $TARGET_CLASS via startService")
            return true
        }

        // 兜底：startBootPhase — 每次 boot phase 推进时检查
        val startBootPhase = manager.declaredMethods.firstOrNull {
            it.name == "startBootPhase" && it.parameterTypes.size == 1
        } ?: run {
            Log.e(TAG, "PermissionHook: no usable entry on $SERVICE_MANAGER_CLASS")
            return false
        }

        startBootPhase.isAccessible = true
        startBootPhase.hook {
            after {
                runCatching { hookTargets(classLoader, moduleUid, quiet = true) }
            }
        }
        Log.d(TAG, "PermissionHook: waiting $TARGET_CLASS via startBootPhase")
        return true
    }

    /**
     * 真正挂载校验方法，幂等（[installed] 保护）。
     */
    private fun hookTargets(classLoader: ClassLoader?, moduleUid: Int, quiet: Boolean = false) {
        if (installed) return

        val clazz = runCatching {
            Class.forName(TARGET_CLASS, false, classLoader)
        }.getOrElse {
            if (!quiet) Log.e(TAG, "PermissionHook: $TARGET_CLASS not loadable yet: ${it.message}")
            return
        }

        val hooked = mutableListOf<String>()
        TARGET_METHODS.forEach { name ->
            clazz.declaredMethods
                .filter { it.name == name }
                .forEach { method ->
                    runCatching {
                        method.isAccessible = true
                        method.hook {
                            intercept {
                                val callerUid = Binder.getCallingUid()
                                if (callerUid == moduleUid) {
                                    // 本模块的调用：伪装为系统
                                    if (method.returnType == Boolean::class.javaPrimitiveType) {
                                        true  // isCallerSystemOrPhone 等返回 true
                                    } else {
                                        null  // assertXxx 等 void 方法直接返回，不抛异常
                                    }
                                } else {
                                    // 其他进程：走原逻辑
                                    callOriginal()
                                }
                            }
                        }
                        hooked += name
                    }.onFailure {
                        Log.e(TAG, "PermissionHook: hook $TARGET_CLASS#$name failed: ${it.message}")
                    }
                }
        }

        val distinct = hooked.distinct()
        if (distinct.isEmpty()) {
            if (!quiet) Log.e(TAG, "PermissionHook: no method hooked in $TARGET_CLASS")
            return
        }

        installed = true
        Log.d(TAG, "PermissionHook: hooked $distinct for uid=$moduleUid")
    }

    private const val TAG = "ChannelGuard"
    private const val TARGET_CLASS =
            "com.android.server.notification.NotificationManagerService"
    private const val SERVICE_MANAGER_CLASS =
            "com.android.server.SystemServiceManager"

    private val TARGET_METHODS = listOf(
            "isCallerSystemOrPhone",
            "isCallerSystem",
            "isCallerSystemOrSystemUi",
            "isCallerSystemOrSystemUiOrShell",
            "assertCallerIsSystemOrSystemUiOrShell",
    )
}

/**
 * 解析本模块自身的 UID。
 *
 * 在 system_server 里拿不到模块 ApplicationInfo，按可靠性从高到低尝试：
 * 1. /data/system/packages.list（system_server 以 system(1000) 身份运行，可直接读）
 * 2. 反射 ActivityThread#getPackageManager() 后调任意 getPackageUid 重载
 */
internal object ModuleUid {

    private const val MODULE_PACKAGE = "com.xfxo.channelguard"
    private const val PACKAGES_LIST = "/data/system/packages.list"

    @Volatile
    private var cached: Int? = null

    fun resolve(): Int {
        cached?.let { return it }
        val uid = fromPackagesList().takeIf { it > 0 }
            ?: fromPackageManager().takeIf { it > 0 }
            ?: -1
        if (uid > 0) cached = uid
        return uid
    }

    /** 直接从 packages.list 按包名取第二列，无需反射，最稳。 */
    private fun fromPackagesList(): Int = runCatching {
        val file = java.io.File(PACKAGES_LIST)
        if (!file.canRead()) return@runCatching -1
        file.useLines { lines ->
            lines.forEach { line ->
                val parts = line.split(' ')
                if (parts.size >= 2 && parts[0] == MODULE_PACKAGE) {
                    return@useLines parts[1].toIntOrNull() ?: -1
                }
            }
            -1
        }
    }.getOrDefault(-1)

    /** 反射系统 PackageManager 查询 UID，兼容不同版本签名。 */
    private fun fromPackageManager(): Int = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        val getPackageManager = activityThread.getDeclaredMethod("getPackageManager")
            .apply { isAccessible = true }
        val pm = getPackageManager.invoke(null) ?: return@runCatching -1

        val candidate = pm.javaClass.methods.firstOrNull {
            it.name == "getPackageUid" && it.parameterTypes.firstOrNull() == String::class.java
        } ?: return@runCatching -1

        val args: Array<Any?> = Array(candidate.parameterTypes.size) { index ->
            when (candidate.parameterTypes[index]) {
                String::class.java -> if (index == 0) MODULE_PACKAGE else null
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                else -> null
            }
        }
        candidate.invoke(pm, *args) as? Int ?: -1
    }.getOrDefault(-1)
}
