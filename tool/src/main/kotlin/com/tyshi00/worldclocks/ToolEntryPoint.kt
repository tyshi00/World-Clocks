package com.tyshi00.worldclocks

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.sdk.shared.LightServerData
import kotlinx.coroutines.flow.StateFlow

/**
 * World Clocks doesn't send push credentials anywhere or handle push
 * notifications, so both hooks are intentionally empty.
 */
@EntryPoint
object ToolEntryPoint : LightEntryPoint {
    override suspend fun onToolCreate(serverData: StateFlow<LightServerData?>) {}
    override suspend fun onPushNotification(data: ByteArray) {}
}
