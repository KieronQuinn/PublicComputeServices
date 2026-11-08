package com.kieronquinn.app.pcs.carcrash

import java.util.Locale

/**
 *  The Personal Safety flag values that offer car crash detection in the phone's language and
 *  country. Both flags are lists of strings ([com.kieronquinn.app.pcs.tiktok.TikTokListFlag]).
 */
object CarCrashFlags {

    /** The Mendel package Personal Safety reads its flags from. */
    const val PACKAGE = "com.google.android.apps.safetyhub"

    /**
     *  Language tags car crash detection is offered in. Each is built into a [Locale] and compared
     *  with the phone's locale without extensions; without a match the setting is hidden and
     *  detection is paused.
     */
    const val SUPPORTED_LOCALES = "22"

    /**
     *  Upper case country codes car crash detection is offered in, compared with the mobile
     *  network's country (or the last one seen); without a match detection is paused.
     */
    const val SUPPORTED_COUNTRIES = "65"

    /**
     *  The entries [flag] needs for a phone using [locale] whose network and SIM are in
     *  [countries] (ISO 3166 codes in any case, blank or null when unknown), or none for any other
     *  flag. The SIM's country is added next to the network's, so detection is not paused while
     *  the network's country is briefly unknown at home.
     */
    fun entriesFor(flag: String, locale: Locale, countries: List<String?>): List<String> {
        return when (flag) {
            SUPPORTED_LOCALES -> listOf(locale.stripExtensions().toLanguageTag())
                .filter { it.isNotBlank() && it != "und" }
            SUPPORTED_COUNTRIES -> countries.mapNotNull { it?.trim()?.uppercase(Locale.US) }
                .filter { it.length == 2 && it.all { char -> char in 'A'..'Z' } }
                .distinct()
            else -> emptyList()
        }
    }

}
