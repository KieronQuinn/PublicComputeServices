package com.kieronquinn.app.pcs.weather

import java.util.Locale

/**
 *  How Pixel Weather's on-device AI summary is offered in the phone's language.
 *
 *  The summary is written by Gemini Nano (AICore). The app only offers it for the languages listed
 *  in [SUPPORTED_LANGUAGES] (compared with the phone's language code), and its code knows one
 *  model and one prompt per language: English, German, French and Japanese. Every other language
 *  throws [unsupported][isUnsupportedLanguage] and the summary card stays hidden. For languages
 *  other than English the app gives the facts in English and only asks for the summary in the
 *  language, so another language takes the same route as [STAND_IN] with its name in the prompt.
 */
object WeatherSummary {

    /** The Mendel package Pixel Weather reads its flags from. */
    const val PACKAGE = "com.google.android.apps.weather"

    /** The language codes the AI summary is offered in. */
    const val SUPPORTED_LANGUAGES = "45647263"

    /** A language the app supports that takes the facts in English. */
    val STAND_IN: Locale = Locale.GERMAN

    private const val STAND_IN_PROMPT = "weather summary in German"
    private const val MODEL_TURN = "<ctrl99>model"
    private const val UNSUPPORTED_DEVICE_LANGUAGE = "Device language: "
    private const val UNSUPPORTED_LANGUAGE = "Language "
    private const val NOT_SUPPORTED = " is not supported"

    /** The name the prompt uses for [locale]'s language, in English. */
    fun languageName(locale: Locale): String? {
        return locale.getDisplayLanguage(Locale.ENGLISH).takeIf {
            it.isNotBlank() && it != locale.language
        }
    }

    /** Whether [error] is the app refusing the phone's language for a model or prompt. */
    fun isUnsupportedLanguage(error: Throwable?): Boolean {
        return error is IllegalStateException &&
            error.message?.startsWith(UNSUPPORTED_DEVICE_LANGUAGE) == true
    }

    /** Whether [error] is the app refusing to name the phone's language in a prompt. */
    fun isUnsupportedLanguageName(error: Throwable?): Boolean {
        val message = (error as? IllegalStateException)?.message ?: return false
        return message.startsWith(UNSUPPORTED_LANGUAGE) && message.endsWith(NOT_SUPPORTED)
    }

    /**
     *  [prompt], written for [STAND_IN], asking for the summary in [languageName] instead, or null
     *  when it is not a summary prompt.
     *
     *  The prompt opens the model's turn without a line break; in other languages the model then
     *  carries on the word "model" and starts every summary with "er:", so the break is added.
     */
    fun promptFor(prompt: String, languageName: String): String? {
        if (!prompt.contains(STAND_IN_PROMPT)) return null
        val renamed = prompt.replace(STAND_IN_PROMPT, "weather summary in $languageName")
        return if (renamed.endsWith(MODEL_TURN)) "$renamed\n" else renamed
    }

}
