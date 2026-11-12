# YukiHook 生成的 libxposed 入口类不要混淆
-keep class com.xfxo.channelguard.HookEntry { *; }
-keep class com.highcapable.yukihook.** { *; }

# 保留注解处理生成的类
-keep @com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry class * { *; }
-keep class * implements com.highcapable.yukihookapi.hook.core.api.compatible.YukiMemberHook { *; }
