package com.kieronquinn.app.pcs.model.phone

import com.kieronquinn.app.pcs.repositories.SettingsRepository.BeeslyRegion
import com.kieronquinn.app.pcs.repositories.SettingsRepository.CallScreenMode
import com.kieronquinn.app.pcs.repositories.SettingsRepository.DobbyRegion
import com.kieronquinn.app.pcs.repositories.SettingsRepository.PatrickPhase
import com.kieronquinn.app.pcs.repositories.SettingsRepository.SharpieCountryMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneSettingsTest {

    private val settings = PhoneSettings(
        sharpieEnabled = true,
        dobbyEnabled = true,
        dobbyUrl = null,
        dobbyDuplexFiles = null,
        dobbyRegion = DobbyRegion.US,
        atlasEnabled = false,
        atlasModels = null,
        beeslyEnabled = false,
        beesly = null,
        beeslyRegion = BeeslyRegion.US,
        nautilusEnabled = false,
        sonicEnabled = false,
        xatuEnabled = false,
        xatuModels = null,
        callerTagsEnabled = false,
        fermatEnabled = false,
        expressoEnabled = false,
        patrickPhase = PatrickPhase.DISABLED,
        callRecordingEnabled = false
    )

    @Test
    fun defaultsKeepClassicCallScreenAndAllowUnsupportedCountries() {
        assertEquals(CallScreenMode.DUPLEX, settings.callScreenMode)
        assertEquals(true, settings.getSharpieCountryOverride("CN"))
        assertEquals(true, settings.getSharpieCountryOverride("US"))
    }

    @Test
    fun disabledScamDetectionLeavesCountryChecksAloneInEveryMode() {
        SharpieCountryMode.entries.forEach { mode ->
            val disabled = settings.copy(sharpieEnabled = false, sharpieCountryMode = mode)
            assertNull(disabled.getSharpieCountryOverride("US"))
            assertNull(disabled.getSharpieCountryOverride("CN"))
        }
    }

    @Test
    fun dialerDefaultLeavesBothAllowedAndUnsupportedCountriesAlone() {
        val defaults = settings.copy(sharpieCountryMode = SharpieCountryMode.DIALER_DEFAULT)
        assertNull(defaults.getSharpieCountryOverride("US"))
        assertNull(defaults.getSharpieCountryOverride("CN"))
    }

    @Test
    fun selectedCountryMatchesTheRequestedCountryCaseInsensitively() {
        val selected = settings.copy(
            sharpieCountryMode = SharpieCountryMode.FORCE_COUNTRY,
            sharpieCountry = "GB"
        )
        assertEquals(true, selected.getSharpieCountryOverride("GB"))
        assertEquals(true, selected.getSharpieCountryOverride("gb"))
        assertEquals(false, selected.getSharpieCountryOverride("US"))
        assertEquals(false, selected.getSharpieCountryOverride("CN"))
        assertEquals(false, selected.getSharpieCountryOverride(""))
    }
}
