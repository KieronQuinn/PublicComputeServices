package com.kieronquinn.app.pcs.xposed

import com.kieronquinn.app.pcs.BuildConfig
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_PCS
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_PHONE
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_PSI
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_TTS
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class Xposed: IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
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
        }
        if (lpparam.packageName != BuildConfig.APPLICATION_ID) {
            DebugHooks.hook(lpparam)
        }
    }

}