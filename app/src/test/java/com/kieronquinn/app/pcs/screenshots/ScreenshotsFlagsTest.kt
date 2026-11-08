package com.kieronquinn.app.pcs.screenshots

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenshotsFlagsTest {

    @Test
    fun `locale flag gets the maximized locale, OCR flag the language`() {
        assertEquals("tr-TR", ScreenshotsFlags.entryFor(ScreenshotsFlags.SUPPORTED_LOCALES, "tr-TR"))
        assertEquals("tr", ScreenshotsFlags.entryFor(ScreenshotsFlags.OCR_LANGUAGES, "tr-TR"))
        assertEquals("zh", ScreenshotsFlags.entryFor(ScreenshotsFlags.OCR_LANGUAGES, "zh-Hans-CN"))
        assertNull(ScreenshotsFlags.entryFor("45651835", "tr-TR"))
        assertNull(ScreenshotsFlags.entryFor(ScreenshotsFlags.OCR_LANGUAGES, ""))
    }

}
