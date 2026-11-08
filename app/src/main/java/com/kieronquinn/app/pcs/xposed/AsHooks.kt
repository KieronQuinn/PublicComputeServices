package com.kieronquinn.app.pcs.xposed

import android.app.AndroidAppHelper
import android.content.ContentResolver
import android.content.res.Resources
import android.provider.Settings
import com.kieronquinn.app.pcs.asi.AsFlagOverrides
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.AS_FORCE_GSA
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.AS_NOTIFICATION_ORGANIZER
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.AS_SHOW_NOW_PLAYING_NOTIFICATION
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_get
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_getBoolean
import com.kieronquinn.app.pcs.utils.extensions.loadDexKit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Modifier
import java.util.Locale

/**
 *  Hooks ASI, optionally:
 *  - Intercepting the DeviceConfig call to check if New Now Playing is enabled, and selectively
 *  returns false for notifications, which allows them to show again.
 *  - Intercepting Settings call to check if the selected search app is GSA, fakes the response
 *  to make RemoteViews-based Smartspace components work.
 */
object AsHooks: XposedHooks {

    private const val NAMESPACE_NOW_PLAYING = "systemui"
    private const val FLAG_NEW_NOW_PLAYING =
        "com.google.android.systemui.now_playing_lockscreen_extended_interaction"
    private const val PACKAGE_NAME_GSA = "com.google.android.googlequicksearchbox"

    override val tag = "AsHooks"

    override fun hook(loadPackageParam: LoadPackageParam) {
        hookLanguageFeatures(loadPackageParam)
        XposedHelpers.findAndHookMethod(
            "android.provider.DeviceConfig",
            loadPackageParam.classLoader,
            "getBoolean",
            String::class.java,
            String::class.java,
            Boolean::class.java,
            object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[0] == NAMESPACE_NOW_PLAYING &&
                        param.args[1] == FLAG_NEW_NOW_PLAYING &&
                        SystemProperties_getBoolean(AS_SHOW_NOW_PLAYING_NOTIFICATION, false) &&
                        getCallingInformation(1)
                            ?.isNotificationClass(loadPackageParam.classLoader) == true) {
                        param.result = false
                    }
                }
            }
        )
        XposedHelpers.findAndHookMethod(
            Settings.Secure::class.java,
            "getString",
            ContentResolver::class.java,
            String::class.java,
            object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (SystemProperties_getBoolean(AS_FORCE_GSA, false)) {
                        param.result = PACKAGE_NAME_GSA
                    }
                }
            }
        )
    }

    /** Features turned on for the phone's language and SIM country; only in ASI's main process. */
    private fun hookLanguageFeatures(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.processName != loadPackageParam.packageName) return
        if (!SystemProperties_getBoolean(AS_NOTIFICATION_ORGANIZER, false)) return
        val locale = Resources.getSystem().configuration.locales.get(0)
        val overrides = AsFlagOverrides.notificationOrganizer(locale, simCountry()).build()
        val dexKit = loadDexKit(loadPackageParam.appInfo.sourceDir)
        AsFlagHooks.install(dexKit, loadPackageParam.classLoader, overrides)
        NotificationClassifierHooks.install(dexKit, loadPackageParam.classLoader) {
            AndroidAppHelper.currentApplication()
        }
    }

    /** The SIM's country (first SIM), else the network's, upper case as ASI lists countries. */
    private fun simCountry(): String? = listOf("gsm.sim.operator.iso-country", "gsm.operator.iso-country")
        .firstNotNullOfOrNull { key ->
            SystemProperties_get(key)?.split(',')?.firstOrNull { it.isNotBlank() }
        }?.trim()?.uppercase(Locale.ROOT)

    private fun Triple<String, String, List<String>>.isNotificationClass(
        classLoader: ClassLoader
    ): Boolean {
        val clazz = XposedHelpers.findClass(first, classLoader)
        return clazz.methods.any {
            it.name == second && Modifier.isFinal(it.modifiers)
                    && it.parameterCount == 5
                    && it.parameterTypes[0] == String::class.java
                    && it.parameterTypes[2].simpleName == "Duration"
                    && it.parameterTypes[4] == Boolean::class.java
        }
    }

}