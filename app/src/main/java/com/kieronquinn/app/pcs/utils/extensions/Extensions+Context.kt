package com.kieronquinn.app.pcs.utils.extensions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserManager
import androidx.core.net.toUri

fun Context.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW).apply {
        data = url.toUri()
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    })
}

fun Context.hasPermission(vararg permission: String): Boolean {
    return permission.all {
        checkCallingOrSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }
}

fun Context.isInDirectBoot(): Boolean {
    val userManager = getSystemService(Context.USER_SERVICE) as UserManager
    return !userManager.isUserUnlocked
}