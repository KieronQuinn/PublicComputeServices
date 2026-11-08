package com.kieronquinn.app.pcs.screenshots

/**
 *  The Pixel Screenshots flag values that let it process screenshots in the phone's language.
 *  Both flags are lists of strings ([com.kieronquinn.app.pcs.tiktok.TikTokListFlag]).
 */
object ScreenshotsFlags {

    /** The Mendel package Pixel Screenshots reads its flags from. */
    const val PACKAGE = "pixel_pearl_android"

    /**
     *  Regular expressions for the locales Pixel Screenshots is offered in. The phone's locale is
     *  expanded with likely subtags (`tr` becomes `tr-TR`) before it is matched; without a match
     *  every screenshot stays paused and the app says the language is not supported.
     */
    const val SUPPORTED_LOCALES = "45658371"

    /**
     *  Language tags the text recognition step accepts per line, compared exactly. A screenshot
     *  whose text is mostly in another language ends as an unsupported language.
     */
    const val OCR_LANGUAGES = "45659312"

    /**
     *  The entry [flag] needs for a phone whose locale, with likely subtags, is
     *  [maximizedLocaleTag] (`tr-TR`), or null for any other flag.
     */
    fun entryFor(flag: String, maximizedLocaleTag: String): String? {
        return when (flag) {
            SUPPORTED_LOCALES -> maximizedLocaleTag
            OCR_LANGUAGES -> maximizedLocaleTag.substringBefore('-')
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

}
