package com.kieronquinn.app.pcs.xposed

import android.app.Application
import android.content.Context
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.PHONE_FLAGS_PROPERTY_NAME
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_getBoolean
import com.kieronquinn.app.pcs.utils.extensions.isInDirectBoot
import com.kieronquinn.app.pcs.utils.extensions.loadDexKit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.luckypray.dexkit.DexKitBridge

object PhoneHooks: XposedHooks {

    override val tag = "PhoneHooks"

    private val FEATURES = setOf(
        "com/android/dialer/sharpie/enabledfn/SharpieEnabledFn", // Scam Detection
        "com/android/dialer/beesly/impl/BeeslyEnabledFn", // Take a Message
        "com/android/dialer/dobby/enabledfn/DobbyEnabledFn", // Call Screen
        "com/android/incallui/atlas/ui/impl/AtlasEnabledFn", // Hold for Me
        "com/android/dialer/nautilus/impl/NautilusEnabledFn", // Voice Translate
    )

    override fun hook(loadPackageParam: LoadPackageParam) {
        loadPackageParam.hookApplication()
    }

    private fun LoadPackageParam.hookApplication() {
        XposedHelpers.findAndHookMethod(
            "com.android.dialer.Dialer_Application",
            classLoader,
            "onCreate",
            object: XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    super.afterHookedMethod(param)
                    val application = param.thisObject as Application
                    hookFeatures(application)
                }
            }
        )
    }

    private fun LoadPackageParam.hookFeatures(context: Context) {
        val dexKit = loadDexKit(appInfo.sourceDir)
        FEATURES.forEach {
            hookFeature(context, dexKit,it)
        }
    }

    private fun LoadPackageParam.hookFeature(
        context: Context,
        dexKit: DexKitBridge,
        feature: String
    ) {
        val featureMethod = dexKit.findClass {
            matcher {
                usingStrings(feature)
            }
        }.findMethod { matcher {
            usingStrings("disabled by flag")
        }}.singleOrNull()?.getMethodInstance(classLoader) ?: run {
            log("Unable to find feature $feature")
            return
        }
        XposedBridge.hookMethod(
            featureMethod,
            object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!context.shouldOverrideFlags()) return
                    param.result = true
                }
            }
        )
    }

    /**
     *  We only override the flags if the option is enabled and the device is not in direct boot,
     *  to prevent crashes
     */
    private fun Context.shouldOverrideFlags(): Boolean {
        if (!SystemProperties_getBoolean(PHONE_FLAGS_PROPERTY_NAME, false)) {
            return false
        }
        return !isInDirectBoot()
    }

}