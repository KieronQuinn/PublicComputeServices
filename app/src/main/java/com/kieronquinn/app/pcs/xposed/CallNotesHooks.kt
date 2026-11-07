package com.kieronquinn.app.pcs.xposed

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.DexKitBridge

internal object CallNotesHooks {
    fun install(dexKit: DexKitBridge, classLoader: ClassLoader, enabled: Boolean) {
        if (!enabled) return
        try {
            val selector = FermatFeatureResolver.find(dexKit, classLoader)
            XposedBridge.hookMethod(selector, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = FermatFeatureResolver.CALL_NOTES_WITH_SUMMARIES
                }
            })
            XposedBridge.log("PhoneHooks: Call Notes mode override installed: " +
                "${selector.declaringClass.name}.${selector.name}; " +
                "native SDK, Direct Boot, consent and model checks retained")
        } catch (error: Exception) {
            // Older builds may still use the legacy flag override. Do not guess another method
            // or prevent the remaining Phone hooks from loading on an unknown resolver layout.
            XposedBridge.log("PhoneHooks: Unable to install Call Notes mode override: $error")
        }
    }
}
