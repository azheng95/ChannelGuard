package com.xfxo.channelguard.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppGroupFilterTest {
    @Test
    fun unknownStoredValueDefaultsToUserApps() {
        assertEquals(AppGroupFilter.USER, AppGroupFilter.fromStorageValue(0))
        assertEquals(AppGroupFilter.USER, AppGroupFilter.fromStorageValue(-1))
    }

    @Test
    fun selectingCurrentGroupKeepsAtLeastOneGroupSelected() {
        assertEquals(AppGroupFilter.USER, AppGroupFilter.USER.select(AppGroupFilter.USER))
        assertEquals(AppGroupFilter.SYSTEM, AppGroupFilter.SYSTEM.select(AppGroupFilter.SYSTEM))
    }

    @Test
    fun selectingOtherGroupSwitchesSelection() {
        assertEquals(AppGroupFilter.SYSTEM, AppGroupFilter.USER.select(AppGroupFilter.SYSTEM))
        assertEquals(AppGroupFilter.USER, AppGroupFilter.SYSTEM.select(AppGroupFilter.USER))
    }

    @Test
    fun storedValuesRoundTrip() {
        AppGroupFilter.entries.forEach { group ->
            assertEquals(group, AppGroupFilter.fromStorageValue(group.storageValue))
        }
    }
}
