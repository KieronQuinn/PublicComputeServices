package com.kieronquinn.app.pcs.xposed

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.AICORE_UNLOAD_INFERENCE
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_getBoolean
import com.kieronquinn.app.pcs.utils.extensions.loadDexKit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

/**
 * Prevents AICore from keeping the on-device inference service bound indefinitely after use. AICore
 * normally writes -1 to disable the framework's idle unbind timeout, then preloads the model again
 * when the inference service disconnects.
 */
object AiCoreHooks: XposedHooks {

    private const val SETTING_INFERENCE_SERVICE_UNBIND_TIMEOUT =
        "on_device_inference_unbind_timeout_ms"
    private const val INFERENCE_SERVICE_UNBIND_TIMEOUT_MILLIS = 5 * 60 * 1000L
    private const val PERSISTENT_MODE_PRELOAD_LOG =
        "Persistent mode is enabled. Scheduling preload model"

    @Volatile
    private var changedInferenceServiceUnbindTimeout = false

    override val tag = "AiCoreHooks"

    override fun hook(loadPackageParam: LoadPackageParam) {
        if (!SystemProperties_getBoolean(AICORE_UNLOAD_INFERENCE, false)) return
        val onInferenceServiceDisconnected = loadDexKit(loadPackageParam.appInfo.sourceDir)
            .findClass {
                matcher { usingStrings(PERSISTENT_MODE_PRELOAD_LOG) }
            }.singleOrNull()?.findMethod {
                matcher { usingStrings(PERSISTENT_MODE_PRELOAD_LOG) }
            }?.singleOrNull()?.getMethodInstance(loadPackageParam.classLoader)?.takeIf {
                it.name == "onInferenceServiceDisconnected" &&
                        it.parameterCount == 0 && it.returnType == Void.TYPE
            } ?: run {
                log("Unable to find supported inference service disconnect callback")
                return
            }
        XposedHelpers.findAndHookMethod(
            Settings.Secure::class.java,
            "putLong",
            ContentResolver::class.java,
            String::class.java,
            Long::class.java,
            object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[1] == SETTING_INFERENCE_SERVICE_UNBIND_TIMEOUT &&
                        param.args[2] == -1L) {
                        param.args[2] = INFERENCE_SERVICE_UNBIND_TIMEOUT_MILLIS
                        changedInferenceServiceUnbindTimeout = true
                        log("Changed inference service unbind timeout to " +
                            "$INFERENCE_SERVICE_UNBIND_TIMEOUT_MILLIS ms")
                    }
                }
            }
        )
        XposedBridge.hookMethod(
            onInferenceServiceDisconnected,
            object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (changedInferenceServiceUnbindTimeout && Settings.Secure.getLong(
                            (param.thisObject as Context).contentResolver,
                            SETTING_INFERENCE_SERVICE_UNBIND_TIMEOUT, -1L
                        ) == INFERENCE_SERVICE_UNBIND_TIMEOUT_MILLIS) {
                        param.result = null
                        log("Prevented inference model preload after idle disconnect")
                    }
                }
            }
        )
    }

}
