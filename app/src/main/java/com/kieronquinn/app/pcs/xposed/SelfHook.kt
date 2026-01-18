package com.kieronquinn.app.pcs.xposed

import android.app.Application.getProcessName
import com.kieronquinn.app.pcs.BuildConfig
import com.kieronquinn.app.pcs.providers.XposedStateProvider
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

object SelfHook: XposedHooks {

    override val tag = "SelfHook"

    override fun hook(loadPackageParam: LoadPackageParam) {
        if (getProcessName() != "${BuildConfig.APPLICATION_ID}:xposed") return
        XposedHelpers.findAndHookMethod(
            XposedStateProvider::class.java.name,
            loadPackageParam.classLoader,
            "isEnabled",
            object: XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any {
                    param.result = true
                    return true
                }
            }
        )
    }

}