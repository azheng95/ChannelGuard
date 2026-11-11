# ChannelGuard 实施计划

> 通知频道批量管理 Xposed 模块。基于 YukiHookAPI，Hook 系统框架 (android)，统一关闭/屏蔽指定 App 的通知频道。
> 项目名称：ChannelGuard

## 总体架构

```
app/src/main/java/com/xfxo/channelguard/
├── HookEntry.kt              # Xposed 入口（@InjectYukiHookWithXposed）
├── hook/
│   ├── NotificationHook.kt   # Hook NotificationManagerService：频道创建 + 通知投递
│   └── ChannelCollector.kt   # 收集已观测到的频道，回写 prefs 供 UI 展示
├── data/
│   ├── PrefsHelper.kt        # 跨进程 SP 封装（XSharedPreferences 模式）
│   ├── RuleRepository.kt    # 屏蔽规则仓库（pkg + channelId → enabled）
│   └── ChannelStore.kt      # 已观测频道缓存（pkg → List<ChannelInfo>）
└── ui/
    ├── MainActivity.kt       # 入口 Activity，导航容器
    ├── AppListScreen.kt      # 已安装 App 列表
    ├── ChannelDetailScreen.kt# 单个 App 的频道列表 + 开关
    └── theme/Theme.kt        # Compose 主题
```

## 作用域与 Hook 目标

- **作用域**：`android`（system_server），已在 `xposed_scope.xml` 声明
- **Hook 类**：`com.android.server.notification.NotificationManagerService`（AOSP 路径）
- **Hook 方法**：
  1. `createNotificationChannels` / `createNotificationChannelImpl` — 频道创建时，若在屏蔽列表中则强制 importance = `IMPORTANCE_NONE`
  2. `enqueueNotificationInternal` / `enqueueNotificationWithTag` — 通知投递时，若频道被屏蔽则直接 return（不投递）
  3. `getNotificationChannelsForPackage` — 顺带记录该 pkg 的所有频道到 prefs（供 UI 展示）

## 数据模型

```kotlin
// 屏蔽规则：pkg + channelId → 是否屏蔽
data class BlockRule(val pkg: String, val channelId: String, val blocked: Boolean)

// 已观测频道
data class ChannelInfo(
    val pkg: String,
    val channelId: String,
    val name: String,
    val importance: Int,   // 0..5
    val description: String?
)
```

## 跨进程数据交换

使用 YukiHookAPI 的 `YukiHookAPI.Configs`（底层 XSharedPreferences）：
- 规则文件：`/data/misc/.../ChannelGuard_prefs.xml`，MODE_WORLD_READABLE（旧设备）/ 新设备通过 `prefs("ChannelGuard")` API
- UI 进程写规则 → system_server 进程读规则
- system_server 写已观测频道 → UI 进程读展示

## 实施步骤

### 第 1 步：数据层
**文件**：
- `app/src/main/java/com/xfxo/channelguard/data/PrefsHelper.kt`
- `app/src/main/java/com/xfxo/channelguard/data/RuleRepository.kt`
- `app/src/main/java/com/xfxo/channelguard/data/ChannelStore.kt`

**要求**：
- `PrefsHelper`：封装 YukiHookAPI 的 `prefs("ChannelGuard")` 读写，提供 `readBlockRules()`、`writeBlockRule(pkg, channelId, blocked)`、`readObservedChannels(pkg)`、`writeObservedChannel(info)` 方法。注意 UI 进程和 Hook 进程都要能用。
- `RuleRepository`：在 `PrefsHelper` 之上提供领域语义 API：`isBlocked(pkg, channelId)`、`setBlocked(pkg, channelId, blocked)`、`getAllRules()`。内存缓存 + 写穿。
- `ChannelStore`：维护 `Map<String, List<ChannelInfo>>`，提供 `observe(pkg)` 返回 Flow，`update(pkg, channels)` 合并去重。

**验收标准**：
- 三个文件编译通过
- `RuleRepository.isBlocked` 返回正确值
- 单元测试覆盖 `setBlocked` 后 `isBlocked` 立即生效

### 第 2 步：Hook 层
**文件**：
- `app/src/main/java/com/xfxo/channelguard/HookEntry.kt`
- `app/src/main/java/com/xfxo/channelguard/hook/NotificationHook.kt`
- `app/src/main/java/com/xfxo/channelguard/hook/ChannelCollector.kt`

**要求**：
- `HookEntry`：`@InjectYukiHookWithXposed` 注解，实现 `IYukiHookXposedInit`，`onHook()` 内 `YukiHookAPI.encase { loadSystem { loadHooker(NotificationHook, ChannelCollector) } }`
- `NotificationHook : YukiBaseHooker()`：
  - Hook `createNotificationChannels`（参数 `NotificationChannel[]`）：遍历数组，对每个 channel 若 `RuleRepository.isBlocked(pkg, channelId)` 则反射设 importance = 0
  - Hook `enqueueNotificationInternal`：before 块内取 notification 的 channelId，若被屏蔽则 `result = null`（阻断投递）
- `ChannelCollector : YukiBaseHooker()`：
  - Hook `getNotificationChannelsForPackage` 的 after 块：把返回的 `List<NotificationChannel>` 转成 `ChannelInfo` 写入 prefs

**验收标准**：
- 编译通过
- LSPosed 中模块可见且作用域勾选 `android` 后能激活
- 日志（`YukiHookAPI.log` 或 Log.e TAG="ChannelGuard"）能在 logcat 看到 Hook 装载成功

### 第 3 步：UI 层
**文件**：
- `app/src/main/java/com/xfxo/channelguard/ui/MainActivity.kt`
- `app/src/main/java/com/xfxo/channelguard/ui/AppListScreen.kt`
- `app/src/main/java/com/xfxo/channelguard/ui/ChannelDetailScreen.kt`
- `app/src/main/java/com/xfxo/channelguard/ui/theme/Theme.kt`

**要求**：
- `MainActivity`：Compose `setContent`，`NavHost` 两个路由：`appList`、`channelDetail/{pkg}`
- 启动时检查 YukiHookAPI 模块激活状态，未激活显示提示卡片
- `AppListScreen`：`PackageManager.getInstalledApplications` 列出所有 App，显示图标 + 名称，点击跳转详情。顶部搜索框 + 系统应用过滤开关
- `ChannelDetailScreen`：根据 pkg 从 `ChannelStore` 读已观测频道（无则提示"启动目标 App 触发通知后可看到频道"），每行显示频道名 + ID + importance，右侧 Switch 切换屏蔽，写入 `RuleRepository`
- `theme/Theme.kt`：Material3 动态取色（Android 12+）或回退紫色主题

**验收标准**：
- 编译通过
- 安装后能打开主界面，显示 App 列表
- 进入某 App 详情能看到频道列表，Switch 切换持久化（重启 Activity 后状态保留）

### 第 4 步：构建验证
**要求**：
- 运行 `./gradlew assembleDebug` 成功产出 APK
- 修复 Gradle 配置或代码中的编译错误
- 若缺少 gradle wrapper，运行 `gradle wrapper --gradle-version 8.4` 生成

**验收标准**：
- `app/build/outputs/apk/debug/app-debug.apk` 存在
- APK 内 `assets/xposed_init` 文件由 KSP 生成，内容为 `com.xfxo.channelguard.HookEntry`

## 技术约束

- **包名**：`com.xfxo.channelguard`（已固定，勿改）
- **最低 SDK**：26（通知频道 API 引入版本）
- **YukiHookAPI**：1.2.0（Maven `https://maven.highcapable.io/`）
- **不要引入**额外三方库（Gson、RxJava 等），用 kotlinx 和 AndroidX 即可
- **不要给 Codex 关沙箱**，保持 `workspace-write`
- **每步完成后** Claude 审查 `git diff`，再交下一步

## 备注

- YukiHookAPI 1.x 入口用 `IYukiHookXposedInit` + `@InjectYukiHookWithXposed`，KSP 自动生成 `assets/xposed_init`，**不要手写**该文件
- Hook system_server 类时用字符串类名 `"com.android.server.notification.NotificationManagerService".toClass()`，不要直接 import（编译期拿不到该类）
- 跨进程 SP 在 Android 10+ 受 SELinux 限制，YukiHookAPI 的 `prefs()` 已处理兼容，直接用即可
