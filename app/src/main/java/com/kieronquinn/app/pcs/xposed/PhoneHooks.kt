package com.kieronquinn.app.pcs.xposed

import android.app.Application
import android.content.Context
import com.kieronquinn.app.pcs.repositories.AstreaRepository.Companion.PORT_PHONE
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.PHONE_FLAGS_PROPERTY_NAME
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_getBoolean
import com.kieronquinn.app.pcs.utils.extensions.isInDirectBoot
import com.kieronquinn.app.pcs.utils.extensions.loadDexKit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

object PhoneHooks: GrpcHooks() {

    override val tag = "PhoneHooks"
    override val applicationClassName = "com.android.dialer.Dialer_Application"
    override val activityClassName =
        "com.android.dialer.multibindingsettings.impl.DialerSettingsActivity"
    override val port = PORT_PHONE

    private val FEATURES = setOf(
        "com/android/dialer/sharpie/enabledfn/SharpieEnabledFn", // Scam Detection
        "com/android/dialer/dobby/enabledfn/DobbyEnabledFn", // Call Screen
        "com/android/incallui/atlas/ui/impl/AtlasEnabledFn", // Hold for Me
        "com/android/dialer/nautilus/impl/NautilusEnabledFn", // Voice Translate
        "com/android/dialer/xatu/impl/XatuEnabledFn", // Direct My Call
        "com/android/dialer/callertags/impl/CallerTagsFeatureEnabledFn", // Caller Tags
        "com/android/dialer/fermat/enabledfn/FermatEnabledFn", // Call Notes
        "com/android/dialer/expresso/enablefn/ExpressoEnabledFn", // Call Reason
        "com/android/dialer/patrick/impl/PatrickEnabledFn", // Calling Card
        "com/android/dialer/patrick/phaseone/PatrickPhaseOneEnabledFn", // Calling Card (Contacts)
        "com/android/dialer/patrick/phasetwo/PatrickPhaseTwoEnabledFn", // Calling Card (Self)
        "com/android/dialer/callrecording/impl/CallRecordingImpl", // Call Recording
    )

    override fun LoadPackageParam.onBeforeApplicationOnCreate(application: Application) {
        val dexKit = loadDexKit(appInfo.sourceDir)
        hookFermat(application, dexKit)
        hookFeatures(application, dexKit)
        hookBeesly(application, dexKit)
    }

    private fun LoadPackageParam.hookFeatures(context: Context, dexKit: DexKitBridge) {
        FEATURES.forEach {
            hookFeature(context, dexKit,it)
        }
    }

    private fun LoadPackageParam.hookFermat(context: Context, dexKit: DexKitBridge) {
        val featureMethod = dexKit.findClass {
            matcher {
                usingStrings("com/android/dialer/fermat/enabledfn/FermatFeatureResolver")
            }
        }.singleOrNull()
            ?.findMethodMultiple("isCallNotesEnabled")
            ?.getMethodInstance(classLoader)
            ?: run {
                log("Unable to find Fermat feature resolver")
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

    private fun LoadPackageParam.hookBeesly(context: Context, dexKit: DexKitBridge) {
        val beeslyClass = dexKit.findClass {
            matcher {
                usingStrings("disabled by flag for actions")
            }
        }.singleOrNull()?.getInstance(classLoader) ?: run {
            log("Unable to find Beesly check")
            return
        }
        beeslyClass.declaredMethods.forEach {
            XposedBridge.hookMethod(
                it,
                object: XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!context.shouldOverrideFlags()) return
                        param.result = true
                    }
                }
            )
        }
        val beeslyv2Method = dexKit.findClass {
            matcher {
                usingStrings("isBeeslyV2Enabled")
            }
        }.singleOrNull()?.findMethod {
            matcher {
                usingStrings("isBeeslyV2Enabled")
            }
        }?.singleOrNull()?.getMethodInstance(classLoader) ?: run {
            log("Unable to find Beesly v2 check")
            return
        }
        XposedBridge.hookMethod(
            beeslyv2Method,
            object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!context.shouldOverrideFlags()) return
                    param.result = true
                }
            }
        )
    }

    private fun LoadPackageParam.hookFeature(
        context: Context,
        dexKit: DexKitBridge,
        feature: String
    ) {
        val featureClasses = dexKit.findClass {
            matcher {
                usingStrings(feature)
            }
        }
        if (featureClasses.isEmpty()) {
            log("Unable to find feature $feature")
        }
        featureClasses.forEach { featureClass ->
            val featureMethod = featureClass
                .findMethodMultiple("disabled by flag", "isEnabled")
                ?.getMethodInstance(classLoader)
            val fallbackMethod = featureClass.getInstance(classLoader).declaredMethods.firstOrNull {
                it.parameterCount == 0
                        && Modifier.isPublic(it.modifiers)
                        && Modifier.isFinal(it.modifiers)
                        && it.returnType == Boolean::class.java
            }
            if (featureMethod == null && fallbackMethod == null) return@forEach
            XposedBridge.hookMethod(
                featureMethod ?: fallbackMethod,
                object: XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!context.shouldOverrideFlags()) return
                        param.result = true
                    }
                }
            )
            return
        }
        log("Unable to find feature method $feature")
    }

    private fun ClassData.findMethodMultiple(vararg search: String): MethodData? {
        return search.firstNotNullOfOrNull { term ->
            findMethod {
                matcher {
                    usingStrings(term)
                }
            }.singleOrNull()
        }
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