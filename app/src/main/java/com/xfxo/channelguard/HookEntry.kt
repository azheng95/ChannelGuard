package com.xfxo.channelguard

import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import com.xfxo.channelguard.hook.ChannelCollector
import com.xfxo.channelguard.hook.NotificationHook
import com.xfxo.channelguard.hook.PermissionHook

@YukiHookLibXposedEntry(
    minApiVersion = 101,
    targetApiVersion = 101,
    scope = ["system"]
)
class HookEntry : YukiHookXposedModule {
    override fun onHook() {
        encase {
            loadSystem {
                registerAppLifecycle {
                    onCreate {
                        com.xfxo.channelguard.data.PrefsHelper.initialize(this)
                    }
                }
                loadHooker(NotificationHook)
                loadHooker(ChannelCollector)
                loadHooker(PermissionHook)
            }
        }
    }
}
