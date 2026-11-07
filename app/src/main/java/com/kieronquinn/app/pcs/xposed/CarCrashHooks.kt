package com.kieronquinn.app.pcs.xposed

import android.app.AndroidAppHelper
import android.telephony.TelephonyManager
import com.kieronquinn.app.pcs.carcrash.CarCrashFlags
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.SAFETY_CAR_CRASH
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_getBoolean
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.Locale

/**
 *  Offers Personal Safety's car crash detection in the phone's language and country
 *  ([CarCrashFlags]) by adding them to its two list flags ([TikTokListFlagHooks]). The values are
 *  read whenever the flags are, so a new locale or network country applies without a restart.
 */
object CarCrashHooks : XposedHooks {

    override val tag = "SafetyHooks"

    override fun hook(loadPackageParam: LoadPackageParam) {
        if (!SystemProperties_getBoolean(SAFETY_CAR_CRASH, false)) return
        try {
            TikTokListFlagHooks(
                this,
                CarCrashFlags.PACKAGE,
                setOf(CarCrashFlags.SUPPORTED_LOCALES, CarCrashFlags.SUPPORTED_COUNTRIES)
            ) { flag ->
                val countries = if (flag == CarCrashFlags.SUPPORTED_COUNTRIES) phoneCountries() else emptyList()
                CarCrashFlags.entriesFor(flag, Locale.getDefault(), countries)
            }.install(loadPackageParam)
        } catch (error: Throwable) {
            log("Car crash detection unavailable: $error")
        }
    }

    /** The mobile network's and the SIM's countries; neither needs a permission to read. */
    private fun phoneCountries(): List<String?> {
        val telephony = AndroidAppHelper.currentApplication()
            ?.getSystemService(TelephonyManager::class.java) ?: return emptyList()
        return listOf(
            runCatching { telephony.networkCountryIso }.getOrNull(),
            runCatching { telephony.simCountryIso }.getOrNull()
        )
    }

}
