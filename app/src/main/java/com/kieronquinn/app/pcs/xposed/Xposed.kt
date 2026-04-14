package com.kieronquinn.app.pcs.xposed

import com.kieronquinn.app.pcs.BuildConfig
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_AGENT
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_AS
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_PCS
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_PHONE
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_PSI
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_TTS
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class Xposed: IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            "android" -> return // Don't ever hook system
            BuildConfig.APPLICATION_ID -> {
                SelfHook.hook(lpparam)
            }
            PACKAGE_NAME_PCS -> {
                PcsHooks.hook(lpparam)
            }
            PACKAGE_NAME_PHONE -> {
                PhoneHooks.hook(lpparam)
            }
            PACKAGE_NAME_PSI -> {
                PsiHooks.hook(lpparam)
            }
            PACKAGE_NAME_TTS -> {
                TtsHooks.hook(lpparam)
            }
            PACKAGE_NAME_AS -> {
                AsHooks.hook(lpparam)
            }
            PACKAGE_NAME_AGENT -> {
                AgentHooks.hook(lpparam)
            }
        }
        if (lpparam.packageName != BuildConfig.APPLICATION_ID) {
            DebugHooks.hook(lpparam)
        }
    }

}