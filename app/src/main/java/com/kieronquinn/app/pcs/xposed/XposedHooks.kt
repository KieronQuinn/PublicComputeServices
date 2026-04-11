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

    fun getCallingInformation(offset: Int = 0): Triple<String, String, List<String>>? {
        val stackTrace = Thread.currentThread().stackTrace
        val classList = stackTrace.map { it.className }
        // Because Google strips the code filenames, we can abuse this to find the immediate caller
        val callerIndex = (stackTrace.indexOfFirst {
            it.fileName == "PG"
        }.takeIf { it >= 0 } ?: return null) + offset
        val caller = stackTrace.getOrNull(callerIndex) ?: return null
        return Triple(caller.className, caller.methodName, classList)
    }

}