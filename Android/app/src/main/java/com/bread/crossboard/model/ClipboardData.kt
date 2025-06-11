package com.bread.crossboard.model

import java.util.UUID

/**
 * Data class for clipboard data
 */
data class ClipboardData(
    val text: String,
    val type: ClipboardType,
    val timestamp: Long,
    var sourceDeviceId: String = "",
    var sourceDeviceName: String = ""
) 