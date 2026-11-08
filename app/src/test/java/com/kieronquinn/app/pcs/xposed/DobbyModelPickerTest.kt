package com.kieronquinn.app.pcs.xposed

import org.junit.Assert.*
import org.junit.Test

class DobbyModelPickerTest {
    interface Provider { fun get(): Any }
    class LocaleProvider

    // Constructor layouts from Dialer 238 and 242, including the removed manual flag.
    @Suppress("UNUSED_PARAMETER")
    class Dialer238(locale: LocaleProvider, path: Provider, agentic: Provider, manual: Provider,
                    us: Provider, uk: Provider, jp: Provider, ca: Provider, ie: Provider,
                    au: Provider, india: Provider)
    @Suppress("UNUSED_PARAMETER")
    class Dialer242(locale: LocaleProvider, path: Provider, agentic: Provider,
                    us: Provider, uk: Provider, jp: Provider, ca: Provider, ie: Provider,
                    au: Provider, india: Provider)
    @Suppress("UNUSED_PARAMETER")
    class Unsupported(locale: LocaleProvider, path: String, agentic: Provider,
                      us: Provider, uk: Provider, jp: Provider, ca: Provider, ie: Provider,
                      au: Provider, india: Provider)

    @Test fun `legacy manual flag and all seven regions retain their meaning`() {
        verifyRegions(Dialer238::class.java, regionStart = 4)
    }

    @Test fun `removed manual flag must not overwrite the US region flag`() {
        verifyRegions(Dialer242::class.java, regionStart = 3)
    }

    @Test fun `unknown signature fails before any arguments are modified`() {
        assertThrows(IllegalStateException::class.java) { DobbyModelPicker.resolve(Unsupported::class.java) }
    }

    private fun verifyRegions(type: Class<*>, regionStart: Int) {
        val picker = DobbyModelPicker.resolve(type)
        assertEquals(LocaleProvider::class.java, picker.localeProvider)
        assertEquals(Provider::class.java, picker.flagType)
        val locale = LocaleProvider()
        val path = object : Provider { override fun get() = "https://example.test/models" }
        val enabled = object : Provider { override fun get() = true }
        val disabled = object : Provider { override fun get() = false }
        for (agentic in listOf(enabled, disabled)) {
            for (region in 0..6) {
                val args = arrayOfNulls<Any>(picker.constructor.parameterCount)
                args[0] = locale
                args[1] = path
                picker.overrideProviders(args, agentic, region, enabled, disabled)
                assertSame(locale, args[0])
                assertSame(path, args[1])
                assertSame(agentic, args[2])
                if (regionStart == 4) assertSame(agentic, args[3])
                for (offset in 0..6) {
                    assertEquals(offset == region, (args[regionStart + offset] as Provider).get())
                }
                // All dependencies remain assignable to the actual constructor types.
                picker.constructor.newInstance(*args)
            }
        }
    }
}
