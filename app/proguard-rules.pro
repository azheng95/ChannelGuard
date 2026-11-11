# YukiHookAPI 生成的入口类不要混淆
-keep class com.xfxo.channelguard.HookEntry { *; }
-keep class com.highcapable.yukihook.** { *; }

# 保留注解处理生成的类
-keep @com.highcapable.yukihook.api.annotation.InjectYukiHookWithXposed class * { *; }
-keep class * implements com.highcapable.yukihook.api.hook.core.api.compatible.YukiMemberHook { *; }
