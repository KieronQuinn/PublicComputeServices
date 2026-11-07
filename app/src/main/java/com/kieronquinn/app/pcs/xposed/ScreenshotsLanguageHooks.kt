package com.kieronquinn.app.pcs.xposed

import android.app.AndroidAppHelper
import android.content.res.Resources
import android.icu.util.ULocale
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.AGENT_SCREENSHOTS_LANGUAGE
import com.kieronquinn.app.pcs.screenshots.ScreenshotsFlags
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_getBoolean
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.Locale

/**
 *  Lets Pixel Screenshots process screenshots in the phone's language ([ScreenshotsFlags]) by
 *  adding the phone's locale to its two language list flags ([TikTokListFlagHooks]).
 */
object ScreenshotsLanguageHooks : XposedHooks {

    override val tag = "AgentHooks"

    override fun hook(loadPackageParam: LoadPackageParam) {
        if (!SystemProperties_getBoolean(AGENT_SCREENSHOTS_LANGUAGE, false)) return
        try {
            TikTokListFlagHooks(
                this,
                ScreenshotsFlags.PACKAGE,
                setOf(ScreenshotsFlags.SUPPORTED_LOCALES, ScreenshotsFlags.OCR_LANGUAGES)
            ) { flag -> listOfNotNull(ScreenshotsFlags.entryFor(flag, maximizedLocaleTag())) }.install(loadPackageParam)
        } catch (error: Throwable) {
            log("Pixel Screenshots language unavailable: $error")
        }
    }

    /**
     *  The locale Pixel Screenshots checks: its own first locale with likely subtags, as
     *  language-COUNTRY (`tr` becomes `tr-TR`).
     */
    private fun maximizedLocaleTag(): String {
        val resources = AndroidAppHelper.currentApplication()?.resources ?: Resources.getSystem()
        val locale = resources.configuration.locales.get(0)
        val maximized = ULocale.addLikelySubtags(ULocale(locale.toLanguageTag())).toLocale()
        return Locale(maximized.language, maximized.country).toLanguageTag()
    }

}
