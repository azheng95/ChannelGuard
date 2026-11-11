package com.xfxo.channelguard.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 规则仓库：负责手动规则 + 智能规则引擎。
 *
 * ## 双模式并存
 *
 * 1. **手动模式**：用户在 UI 里逐个频道开关，存 SP 的 `pkg/channelId → blocked`
 * 2. **智能模式**：基于关键词规则自动匹配营销频道，规则存文件 `files/rules/`
 *
 * 优先级：手动规则 > 智能规则（手动设置过的频道，智能规则不再干预）
 *
 * ## 智能规则存储布局
 *
 * `filesDir/rules/`:
 * - `cloud.json`：最近一次成功同步的云端规则集
 * - `custom.json`：用户自建规则集
 * - `smart_mode_enabled`：智能模式开关状态（文件存在=开启）
 */
class RuleRepository(
    private val prefsHelper: PrefsHelper = PrefsHelper
) {
    // ============ 手动规则（原有功能） ============
    private val manualRules = ConcurrentHashMap<String, Boolean>().apply {
        putAll(prefsHelper.readBlockRules())
    }

    fun isBlocked(pkg: String, channelId: String): Boolean {
        val key = ruleKey(pkg, channelId)
        // 命中内存缓存直接返回；否则从 prefs 单条读穿并回填
        return manualRules[key]
            ?: prefsHelper.readBlockRule(pkg, channelId).also { value ->
                if (value != null) manualRules[key] = value
            } ?: false
    }

    fun setBlocked(pkg: String, channelId: String, blocked: Boolean) {
        val key = ruleKey(pkg, channelId)
        manualRules[key] = blocked
        prefsHelper.writeBlockRule(pkg, channelId, blocked)
    }

    fun getAllRules(): Map<String, Boolean> = manualRules.toMap()

    private fun ruleKey(pkg: String, channelId: String) = "$pkg/$channelId"

    // ============ 智能规则引擎 ============

    companion object {
        /** 云端默认规则地址：使用 XMSFNotiChannel 的规则库 */
        const val CLOUD_URL =
            "https://raw.githubusercontent.com/Aruvelut-123/NotiSentry-Rules/main/rules.json"

        private const val DIR_NAME = "rules"
        private const val CLOUD_FILE = "cloud.json"
        private const val CUSTOM_FILE = "custom.json"
        private const val SMART_MODE_FLAG = "smart_mode_enabled"

        private const val TIMEOUT_MS = 10_000
        private const val AUTO_SYNC_INTERVAL_MS = 12L * 60L * 60L * 1000L // 12小时

        /** 内置兜底规则（首次启动 / 无网络时使用） */
        private val BUILTIN_RULES = listOf(
            ChannelRule(
                id = "builtin-official",
                name = "内置规则",
                description = "模块自带的兜底规则表，云端规则同步成功后会被替换。",
                enabled = true,
                priority = 0,
                positive = listOf(
                    "运营消息", "运营活动", "运营通道", "运营推送",
                    "个性化通道", "个性化推荐", "个性化推送",
                    "营销", "推广", "广告", "促销", "特惠", "优惠", "优惠券", "红包",
                    "折扣", "满减", "秒杀", "团购", "补贴", "返利", "福利",
                    "活动通知", "平台活动", "精选活动", "活动优惠", "业务活动", "互动活动",
                    "周年庆", "大促",
                    "签到", "抢兑", "抽奖", "积分兑换", "任务奖励", "每日任务",
                    "推荐", "精选", "猜你喜欢", "为你推荐", "兴趣内容", "智能推送",
                    "资讯", "热点", "头条", "每日推荐", "社区内容", "内容社区",
                    "商城", "店铺", "上新", "新品", "开售", "商品", "悬浮锁屏",
                    "直播提醒", "直播动态", "直播预告",
                    "promotion", "promo", "advert", "campaign", "coupon",
                    "discount", "recommend", "lottery", "checkin",
                ),
                exclude = listOf(
                    "后台活动", "模拟活动", "实时活动", "安装进度",
                    "下载", "上传", "转码", "安装", "更新服务", "版本更新", "新版本",
                    "安全检测", "支付", "付款", "转账", "收款", "订单", "物流", "退款", "验证码",
                    "电量", "充电", "网络", "蓝牙", "热点", "定位",
                    "播放器", "通知栏", "控件", "前台服务",
                    "新消息", "聊天消息", "私信", "群聊消息",
                ),
                source = ChannelRule.Source.BUILTIN,
            )
        )
    }

    @Volatile
    private var smartModeEnabled = false

    @Volatile
    private var cloudRules: List<ChannelRule> = emptyList()

    @Volatile
    private var customRules: List<ChannelRule> = emptyList()

    @Volatile
    private var effectiveRulesCache: List<ChannelRule>? = null

    @Volatile
    private var lastSyncTime: Long = 0L

    @Volatile
    private var initialized = false

    /** 幂等初始化：读取本地规则文件 */
    fun initSmartRules(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }

            // 读取智能模式开关
            smartModeEnabled = File(dir, SMART_MODE_FLAG).exists()

            // 读取云端规则（无则用内置）
            cloudRules = readRuleBundle(File(dir, CLOUD_FILE))?.rules ?: BUILTIN_RULES
            lastSyncTime = File(dir, CLOUD_FILE).takeIf { it.exists() }?.lastModified() ?: 0L

            // 读取自定义规则
            customRules = readRuleBundle(File(dir, CUSTOM_FILE))?.rules.orEmpty()
            effectiveRulesCache = null

            initialized = true
        }
    }

    /** 智能模式是否开启 */
    fun isSmartModeEnabled(): Boolean = smartModeEnabled

    /** 设置智能模式开关 */
    fun setSmartModeEnabled(context: Context, enabled: Boolean) {
        initSmartRules(context)
        smartModeEnabled = enabled
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val flag = File(dir, SMART_MODE_FLAG)
        if (enabled) {
            flag.createNewFile()
        } else {
            flag.delete()
        }
    }

    /** 获取当前生效的全部智能规则 */
    fun getEffectiveSmartRules(): List<ChannelRule> {
        effectiveRulesCache?.let { return it }
        return synchronized(this) {
            effectiveRulesCache ?: (cloudRules + customRules).also { effectiveRulesCache = it }
        }
    }

    /** 判断频道是否应被智能规则匹配（营销频道） */
    fun isMatchedBySmartRule(channelName: String, channelId: String): Boolean {
        if (!smartModeEnabled) return false
        return ChannelMatcher.matches(channelName, channelId, getEffectiveSmartRules())
    }

    /** 云端同步 */
    suspend fun syncFromCloud(context: Context): SyncResult = withContext(Dispatchers.IO) {
        initSmartRules(context)
        try {
            val connection = URL(CLOUD_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.connect()

            if (connection.responseCode != 200) {
                return@withContext SyncResult.Failure("HTTP ${connection.responseCode}")
            }

            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val bundle = RuleBundle.parse(text, ChannelRule.Source.BUILTIN)
                ?: return@withContext SyncResult.Failure("解析失败")

            // 写入本地
            val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
            File(dir, CLOUD_FILE).writeText(text)

            cloudRules = bundle.rules
            lastSyncTime = System.currentTimeMillis()
            effectiveRulesCache = null

            SyncResult.Success(bundle.rules.size, bundle.version, bundle.updatedAt)
        } catch (e: Exception) {
            SyncResult.Failure(e.message ?: "未知错误")
        }
    }

    /** 获取云端规则元信息 */
    fun getCloudMeta(): CloudMeta = CloudMeta(
        ruleCount = cloudRules.size,
        lastSyncTime = lastSyncTime,
        fromBuiltin = lastSyncTime == 0L
    )

    data class CloudMeta(
        val ruleCount: Int = 0,
        val lastSyncTime: Long = 0L,
        val fromBuiltin: Boolean = true
    )

    sealed interface SyncResult {
        data class Success(val count: Int, val version: Int, val updatedAt: String) : SyncResult
        data class Failure(val message: String) : SyncResult
    }

    private fun readRuleBundle(file: File): RuleBundle? {
        if (!file.exists()) return null
        return runCatching {
            val text = file.readText()
            RuleBundle.parse(text, ChannelRule.Source.BUILTIN)
        }.getOrNull()
    }
}
