package com.kieronquinn.app.pcs.phonestrings

import com.google.gson.Gson
import java.io.InputStream
import java.io.Reader
import java.util.Locale

/**
 *  Translations of Phone strings Google ships in English only (Direct My Call, Call Screen, Hold
 *  for Me and spam screens), shipped with PCS as `assets/phonestrings/<language>.json` and keyed
 *  by Phone's resource names. A value may contain `<b>` for the parts Phone shows in bold.
 */
class PhoneStrings private constructor(val locale: Locale, val strings: Map<String, String>) {

    class InvalidException(message: String) : Exception(message)

    private class Data(val version: Int?, val locale: String?, val strings: Map<String, String>?)

    companion object {
        const val ASSET_DIRECTORY = "phonestrings"
        private const val FORMAT_VERSION = 1

        fun parse(reader: Reader): PhoneStrings {
            val data = Gson().fromJson(reader, Data::class.java) ?: throw InvalidException("Empty translation")
            if (data.version != FORMAT_VERSION) throw InvalidException("Unsupported version ${data.version}")
            val locale = data.locale?.let { Locale.forLanguageTag(it) }?.takeIf { it.language.isNotEmpty() }
                ?: throw InvalidException("Missing locale")
            val strings = data.strings.orEmpty()
            if (strings.isEmpty()) throw InvalidException("No strings")
            strings.entries.firstOrNull { it.value.isBlank() }?.let { throw InvalidException("Blank ${it.key}") }
            return PhoneStrings(locale, strings)
        }

        /**
         *  The translation for [locale]'s language, read with [open] (a path relative to the
         *  assets directory to its stream, or null if there is no such file).
         */
        fun forLocale(locale: Locale, open: (String) -> InputStream?): PhoneStrings? {
            val candidates = listOf("${locale.toLanguageTag()}.json", "${locale.language}.json")
            for (name in candidates) {
                val stream = open("$ASSET_DIRECTORY/$name") ?: continue
                val strings = stream.reader().use(::parse)
                return strings.takeIf { it.locale.language == locale.language }
            }
            return null
        }
    }

}
