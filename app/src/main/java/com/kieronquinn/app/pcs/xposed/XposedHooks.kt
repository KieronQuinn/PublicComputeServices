package com.kieronquinn.app.pcs.xposed

import android.util.Log
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Member

interface XposedHooks {

    val tag: String

    fun hook(loadPackageParam: LoadPackageParam)

    fun log(message: String) {
        Log.d(tag, message)
        XposedBridge.log("$tag: $message")
    }

    /**
     *  Runs [member] in the interpreter, so the small methods it calls are called rather than
     *  inlined into its compiled code, which would skip their hooks. Not every framework offers
     *  this (Vector's legacy bridge has no `deoptimizeMethod`); then it is logged and the hooks
     *  apply wherever the call was not inlined.
     */
    fun deoptimize(member: Member) {
        val deoptimized = runCatching {
            XposedBridge::class.java.getMethod("deoptimizeMethod", Member::class.java)
                .invoke(null, member) as? Boolean
        }.getOrNull()
        if (deoptimized != true) log("Unable to deoptimize $member")
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