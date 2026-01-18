package com.kieronquinn.app.pcs.xposed

import android.util.Log
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.DEBUG_PROPERTY_NAME
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_getBoolean
import com.kieronquinn.app.pcs.utils.extensions.loadDexKit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

object DebugHooks: XposedHooks {

    override val tag = "PcsDebugHooks"

    override fun hook(loadPackageParam: LoadPackageParam) {
        if (!SystemProperties_getBoolean(DEBUG_PROPERTY_NAME, false)) return
        log("Searching for logger class in ${loadPackageParam.packageName}")
        val dexKitBridge = loadDexKit(loadPackageParam.appInfo.sourceDir)
        val logger = dexKitBridge.findClass {
            matcher { usingStrings("cannot get arguments before calling log()") }
        }.singleOrNull()?.let {
            try {
                XposedHelpers.findClass(it.name, loadPackageParam.classLoader)
            } catch (e: XposedHelpers.ClassNotFoundError) {
                null
            }
        } ?: run {
            log("Unable to find logger class for ${loadPackageParam.packageName}")
            return
        }
        val tag = loadPackageParam.packageName.let {
            if (it.contains(".")) {
                it.split(".").last()
            } else it
        }.uppercase()
        var hookedMethods = 0
        logger.declaredMethods.filter {
            it.parameterTypes.firstOrNull() == String::class.java
                    && it.returnType.toString() == "void"
        }.forEach {
            XposedBridge.hookMethod(it, object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    super.beforeHookedMethod(param)
                    val args = param.args
                    val formatted = if (args.size > 1) {
                        try {
                            String.format(
                                args[0] as String,
                                *args.drop(1).toTypedArray()
                            )
                        } catch (e: Exception) {
                            return
                        }
                    } else {
                        args[0] as String
                    }
                    Log.d(tag, formatted)
                }
            })
            hookedMethods++
        }
        log("Hooked $hookedMethods methods in ${loadPackageParam.packageName}")
    }

}