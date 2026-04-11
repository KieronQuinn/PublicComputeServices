package com.kieronquinn.app.pcs.utils.extensions

import android.annotation.SuppressLint

@SuppressLint("PrivateApi")
fun SystemProperties_getBoolean(key: String, defaultValue: Boolean): Boolean {
    runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        systemProperties.getMethod("getBoolean", String::class.java, Boolean::class.java)
            .invoke(null, key, defaultValue) as? Boolean ?: defaultValue
    }.onSuccess {
        return it
    }.onFailure {
        return defaultValue
    }
    return defaultValue
}

@SuppressLint("PrivateApi")
fun SystemProperties_get(key: String): String? {
    runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        systemProperties.getMethod("get", String::class.java)
            .invoke(null, key) as? String
    }.onSuccess {
        return it
    }.onFailure {
        return null
    }
    return null
}