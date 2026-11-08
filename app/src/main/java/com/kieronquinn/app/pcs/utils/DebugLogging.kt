package com.kieronquinn.app.pcs.utils

import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.DEBUG_PROPERTY_NAME
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_getBoolean

/** Phone uses its Provider snapshot, including gRPC logs emitted inside the Phone process. */
object DebugLogging {
    @Volatile private var phoneEnabled: Boolean? = null

    fun beginPhoneProcess() {
        phoneEnabled = false
    }

    fun configurePhone(enabled: Boolean) {
        phoneEnabled = enabled
    }

    fun isEnabled(): Boolean = phoneEnabled
        ?: SystemProperties_getBoolean(DEBUG_PROPERTY_NAME, false)
}
