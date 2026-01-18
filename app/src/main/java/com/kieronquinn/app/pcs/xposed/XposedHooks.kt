package com.kieronquinn.app.pcs.xposed

import android.util.Log
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

interface XposedHooks {

    val tag: String

    fun hook(loadPackageParam: LoadPackageParam)

    fun log(message: String) {
        Log.d(tag, message)
        XposedBridge.log("$tag: $message")
    }

}