package com.kieronquinn.app.pcs.directmycall

import java.util.Locale

/**
 *  Finds the keypad options in an automated phone menu's transcript, for a language Direct My
 *  Call's own parser does not read. Phone's parser only knows English menu wording ("for billing,
 *  press 2") and English number words, so a menu in another language gets no buttons.
 */
interface MenuParser {

    /**
     *  An option: [key] is the DTMF key ("0"-"9", "*" or "#"), [action] the start of its
     *  description as Phone shows it on the button, [start] and [end] its range in the full text.
     */
    data class Option(val key: String, val action: String, val start: Int, val end: Int)

    /**
     *  The options that end after [rangeStart] in [text], read up to [rangeEnd] (Phone's range of
     *  text it has not turned into options yet). Offsets are in [text]; an empty list if none.
     */
    fun parse(text: String, rangeStart: Int, rangeEnd: Int): List<Option>

    companion object {
        /** The parser for [locale]'s language, or null if PCS has none. */
        fun forLocale(locale: Locale): MenuParser? = when (locale.language) {
            "tr" -> TurkishMenuParser
            else -> null
        }
    }

}
