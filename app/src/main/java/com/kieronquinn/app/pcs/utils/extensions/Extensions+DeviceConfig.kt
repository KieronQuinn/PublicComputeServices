package com.kieronquinn.app.pcs.utils.extensions

import android.annotation.SuppressLint

@SuppressLint("PrivateApi") // Only used in module with access
fun DeviceConfig_getString(namespace: String, name: String, defaultValue: String?): String? {
    return Class.forName("android.provider.DeviceConfig")
        .getMethod("getString", String::class.java, String::class.java, String::class.java)
        .invoke(null, namespace, name, defaultValue) as? String
}