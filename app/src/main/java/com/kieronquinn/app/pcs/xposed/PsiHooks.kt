package com.kieronquinn.app.pcs.xposed

import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.PSI_ENABLE_APPS_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.PSI_FORCE_ACCOUNT_PRESENCE_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.PSI_FORCE_ACCOUNT_TYPE_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.PSI_FORCE_ADMIN_ALLOWANCE_PROPERTY_NAME
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_getBoolean
import com.kieronquinn.app.pcs.utils.extensions.loadDexKit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Constructor

object PsiHooks: XposedHooks {

    override val tag = "PsiHooks"

    private val ELIGIBILITY_PROPERTIES = mapOf(
        "AccountPresence" to PSI_FORCE_ACCOUNT_PRESENCE_PROPERTY_NAME,
        "AccountType" to PSI_FORCE_ACCOUNT_TYPE_PROPERTY_NAME,
        "AdminAllowance" to PSI_FORCE_ADMIN_ALLOWANCE_PROPERTY_NAME
    )

    override fun hook(loadPackageParam: LoadPackageParam) {
        val dexKit = loadDexKit(loadPackageParam.appInfo.sourceDir)
        hookApps()
        loadPackageParam.hookEligibility(dexKit)
    }

    /**
     *  Enables visibility of Google Wallet & Tasks in Magic Cue settings
     */
    private fun hookApps() {
        if (!SystemProperties_getBoolean(PSI_ENABLE_APPS_PROPERTY_NAME, false)) {
            return
        }
        var hasHookedAppList = false
        XposedHelpers.findAndHookConstructor(
            java.lang.Enum::class.java,
            String::class.java,
            Integer.TYPE,
            object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    super.beforeHookedMethod(param)
                    if (!hasHookedAppList && param.args[0] == "PIXEL_SCREENSHOT") {
                        val constructor = param.thisObject::class.java.getAppListConstructorOrNull()
                            ?: return
                        constructor.hookAppListConstructor()
                        hasHookedAppList = true
                    }
                }
            }
        )
    }

    /**
     *  Force enables eligibility checks, useful on emulator
     */
    private fun LoadPackageParam.hookEligibility(dexKit: DexKitBridge) {
        val eligible = dexKit.findClass {
            matcher { usingStrings("Eligible(rule=") }
        }.singleOrNull()?.let {
            try {
                XposedHelpers.findClass(it.name, classLoader)
                    ?.getConstructor(ByteArray::class.java)
                    ?.newInstance(null)
            } catch (e: XposedHelpers.ClassNotFoundError) {
                null
            }
        } ?: run {
            log("Unable to find Eligible class")
            return
        }
        val eligibilityCheckString = "CurrentModel: %s, BlockedModels: %s"
        val eligibilityCheck = dexKit.findClass {
            matcher {
                usingStrings(eligibilityCheckString)
            }
        }.findMethod { matcher {
            usingStrings(eligibilityCheckString)
        }}.singleOrNull()?.getMethodInstance(classLoader) ?: run {
            log("Unable to find eligibilityCheck")
            return
        }
        XposedBridge.hookMethod(
            eligibilityCheck,
            object: XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    super.afterHookedMethod(param)
                    if (param.args[0].toString().getProperty()) {
                        param.result = eligible
                    }
                }
            }
        )
    }

    private fun String.getProperty(): Boolean {
        ELIGIBILITY_PROPERTIES.forEach {
            if (startsWith(it.key)) {
                return SystemProperties_getBoolean(it.value, false)
            }
        }
        return false
    }

    private fun Class<*>.getAppListConstructorOrNull(): Constructor<*>? {
        return constructors.firstOrNull {
            it.parameterTypes.contentEquals(arrayOf(
                String::class.java,
                Integer.TYPE,
                Integer.TYPE,
                String::class.java,
                Boolean::class.java,
                Boolean::class.java
            ))
        }
    }

    private fun Constructor<*>.hookAppListConstructor() {
        XposedBridge.hookMethod(
            this,
            object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    super.beforeHookedMethod(param)
                    if(param.args[3] == "com.google.android.apps.walletnfcre") {
                        param.args[3] = "com.google.android.apps.walletnfcrel"
                        param.args[5] = true
                    }
                    if(param.args[3] == "com.google.android.apps.tasks") {
                        param.args[5] = true
                    }
                }
            }
        )
    }

}