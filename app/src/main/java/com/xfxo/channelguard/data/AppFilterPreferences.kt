package com.xfxo.channelguard.data

import android.content.Context
import com.tencent.mmkv.MMKV

object AppFilterPreferences {
    private const val KEY_APP_GROUP = "app_list_group_filter"

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                MMKV.initialize(context.applicationContext)
                initialized = true
            }
        }
    }

    fun readAppGroup(): AppGroupFilter =
        AppGroupFilter.fromStorageValue(
            MMKV.defaultMMKV().decodeInt(KEY_APP_GROUP, AppGroupFilter.USER.storageValue)
        )

    fun writeAppGroup(group: AppGroupFilter) {
        MMKV.defaultMMKV().encode(KEY_APP_GROUP, group.storageValue)
    }
}
