package com.xfxo.channelguard.data

enum class AppGroupFilter(val storageValue: Int) {
    SYSTEM(1),
    USER(2);

    fun select(group: AppGroupFilter): AppGroupFilter =
        if (group == this) this else group

    companion object {
        fun fromStorageValue(value: Int): AppGroupFilter =
            entries.firstOrNull { it.storageValue == value } ?: USER
    }
}
