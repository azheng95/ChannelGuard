package com.xfxo.channelguard

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.highcapable.yukihookapi.YukiHookAPI
import com.xfxo.channelguard.hook.ChannelCollector
import com.xfxo.channelguard.hook.NotificationHook
import com.xfxo.channelguard.hook.PermissionHook

@InjectYukiHookWithXposed
object HookEntry : IYukiHookXposedInit {
    override fun onHook() {
        YukiHookAPI.encase {
            loadSystem {
                // 初始化 PrefsHelper（system_server 进程中获取 Application Context）
                onAppLifecycle {
                    onCreate {
                        com.xfxo.channelguard.data.PrefsHelper.initialize(this)
                        // 初始化智能规则引擎
                        com.xfxo.channelguard.data.RuleRepository().initSmartRules(this)
                    }
                }
                loadHooker(NotificationHook)
                loadHooker(ChannelCollector)
                loadHooker(PermissionHook)
            }
        }
    }
}
