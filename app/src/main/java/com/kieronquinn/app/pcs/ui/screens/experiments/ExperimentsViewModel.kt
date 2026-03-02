package com.kieronquinn.app.pcs.ui.screens.experiments

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_PHONE
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_PSI
import com.kieronquinn.app.pcs.model.phone.PhoneSettings
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository
import com.kieronquinn.app.pcs.repositories.PropertiesRepository
import com.kieronquinn.app.pcs.repositories.SettingsRepository
import com.kieronquinn.app.pcs.repositories.SettingsRepository.BeeslyRegion
import com.kieronquinn.app.pcs.repositories.SettingsRepository.DobbyRegion
import com.kieronquinn.app.pcs.repositories.SettingsRepository.PatrickPhase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class ExperimentsViewModel: ViewModel() {

    abstract val state: StateFlow<State>

    abstract fun onPhoneSharpieEnabledChanged(enabled: Boolean)
    abstract fun onPhoneDobbyEnabledChanged(enabled: Boolean)
    abstract fun onPhoneDobbyRegionChanged(region: DobbyRegion)
    abstract fun onPhoneAtlasEnabledChanged(enabled: Boolean)
    abstract fun onPhoneBeeslyEnabledChanged(enabled: Boolean)
    abstract fun onPhoneBeeslyRegionChanged(region: BeeslyRegion)
    abstract fun onPhoneNautilusEnabledChanged(enabled: Boolean)
    abstract fun onPhoneSonicEnabledChanged(enabled: Boolean)
    abstract fun onPhoneXatuEnabledChanged(enabled: Boolean)
    abstract fun onPhoneCallerTagsEnabledChanged(enabled: Boolean)
    abstract fun onPhoneFermatEnabledChanged(enabled: Boolean)
    abstract fun onPhoneExpressoEnabledChanged(enabled: Boolean)
    abstract fun onPhonePatrickChanged(patrickPhase: PatrickPhase)
    abstract fun onPhoneCallRecordingEnabledChanged(enabled: Boolean)

    abstract fun onPsiAppsChanged(enabled: Boolean)
    abstract fun onPsiForceAccountPresenceChanged(enabled: Boolean)
    abstract fun onPsiForceAccountTypeChanged(enabled: Boolean)
    abstract fun onPsiForceAdminAllowanceChanged(enabled: Boolean)

    sealed class State {
        data object Loading: State()
        data class Loaded(
            val magicCueAvailable: Boolean,
            val phoneSettings: PhoneSettings,
            val propertiesState: PropertiesRepository.State
        ): State()
    }

}

class ExperimentsViewModelImpl(
    private val propertiesRepository: PropertiesRepository,
    private val deviceConfigPropertiesRepository: DeviceConfigPropertiesRepository,
    settingsRepository: SettingsRepository,
    context: Context
): ExperimentsViewModel() {

    private val phoneSharpieEnabled = settingsRepository.phoneSharpieEnabled
    private val phoneDobbyEnabled = settingsRepository.phoneDobbyEnabled
    private val phoneDobbyRegion = settingsRepository.phoneDobbyRegion
    private val phoneDobbyUrl = settingsRepository.phoneDobbyUrl
    private val phoneDobbyDuplexFiles = settingsRepository.phoneDobbyDuplexFiles
    private val phoneAtlasEnabled = settingsRepository.phoneAtlasEnabled
    private val phoneAtlasModels = settingsRepository.phoneAtlasModels
    private val phoneBeeslyEnabled = settingsRepository.phoneBeeslyEnabled
    private val phoneBeeslyRegion = settingsRepository.phoneBeeslyRegion
    private val phoneBeesly = settingsRepository.phoneBeesly
    private val phoneNautilusEnabled = settingsRepository.phoneNautilusEnabled
    private val phoneSonicEnabled = settingsRepository.phoneSonicEnabled
    private val phoneXatuEnabled = settingsRepository.phoneXatuEnabled
    private val phoneXatuModels = settingsRepository.phoneXatuModels
    private val phoneCallerTagsEnabled = settingsRepository.phoneCallerTagsEnabled
    private val phoneFermatEnabled = settingsRepository.phoneFermatEnabled
    private val phoneExpressoEnabled = settingsRepository.phoneExpressoEnabled
    private val phonePatrickPhase = settingsRepository.phonePatrickPhase
    private val phoneCallRecordingEnabled = settingsRepository.phoneCallRecordingEnabled

    private val isMagicCueAvailable = flow {
        val versionName = try {
            context.packageManager.getPackageInfo(PACKAGE_NAME_PSI, 0)
                ?.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        emit(versionName != null && !versionName.contains("stub"))
    }

    private val phoneBooleanSettings = combine(
        phoneSharpieEnabled.asFlow(),
        phoneDobbyEnabled.asFlow(),
        phoneAtlasEnabled.asFlow(),
        phoneBeeslyEnabled.asFlow(),
        phoneNautilusEnabled.asFlow(),
        phoneSonicEnabled.asFlow(),
        phoneXatuEnabled.asFlow(),
        phoneCallerTagsEnabled.asFlow(),
        phoneFermatEnabled.asFlow(),
        phoneExpressoEnabled.asFlow(),
        phoneCallRecordingEnabled.asFlow()
    ) {
        it
    }

    private val phoneEnumSettings = combine(
        phoneDobbyRegion.asFlow(),
        phoneBeeslyRegion.asFlow(),
        phonePatrickPhase.asFlow()
    ) {
        it
    }

    private val phoneStringSettings = combine(
        phoneDobbyUrl.asFlow(),
        phoneBeesly.asFlow(),
        phoneDobbyDuplexFiles.asFlow(),
        phoneAtlasModels.asFlow(),
        phoneXatuModels.asFlow()
    ) {
        it
    }

    private val phoneSettings = combine(
        phoneBooleanSettings,
        phoneEnumSettings,
        phoneStringSettings
    ) { booleans, enums, strings ->
        PhoneSettings(
            booleans[0],
            booleans[1],
            strings[0],
            strings[2],
            enums[0] as DobbyRegion,
            booleans[2],
            strings[3],
            booleans[3],
            strings[1],
            enums[1] as BeeslyRegion,
            booleans[4],
            booleans[5],
            booleans[6],
            strings[4],
            booleans[7],
            booleans[8],
            booleans[9],
            enums[2] as PatrickPhase,
            booleans[10]
        )
    }

    override val state = combine(
        isMagicCueAvailable,
        propertiesRepository.state,
        phoneSettings
    ) { magicCueAvailable, propertiesState, phoneSettings ->
        State.Loaded(
            magicCueAvailable = magicCueAvailable,
            phoneSettings = phoneSettings,
            propertiesState = propertiesState
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State.Loading)

    override fun onPhoneSharpieEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneSharpieEnabled.set(enabled)
            forceStopPhone()
        }
    }

    override fun onPhoneDobbyEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneDobbyEnabled.set(enabled)
            forceStopPhone()
        }
    }

    override fun onPhoneDobbyRegionChanged(region: DobbyRegion) {
        viewModelScope.launch {
            phoneDobbyRegion.set(region)
            forceStopPhone()
        }
    }

    override fun onPhoneAtlasEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneAtlasEnabled.set(enabled)
            forceStopPhone()
        }
    }

    override fun onPhoneBeeslyEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneBeeslyEnabled.set(enabled)
            forceStopPhone()
        }
    }

    override fun onPhoneBeeslyRegionChanged(region: BeeslyRegion) {
        viewModelScope.launch {
            phoneBeeslyRegion.set(region)
            forceStopPhone()
        }
    }

    override fun onPhoneNautilusEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneNautilusEnabled.set(enabled)
            forceStopPhone()
        }
    }

    override fun onPhoneSonicEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneSonicEnabled.set(enabled)
            forceStopPhone()
        }
    }

    override fun onPhoneXatuEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneXatuEnabled.set(enabled)
            forceStopPhone()
        }
    }

    override fun onPhoneCallerTagsEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneCallerTagsEnabled.set(enabled)
            forceStopPhone()
        }
    }

    override fun onPhoneFermatEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneFermatEnabled.set(enabled)
            forceStopPhone()
        }
    }

    override fun onPhoneExpressoEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneExpressoEnabled.set(enabled)
            forceStopPhone()
        }
    }

    override fun onPhonePatrickChanged(patrickPhase: PatrickPhase) {
        viewModelScope.launch {
            phonePatrickPhase.set(patrickPhase)
            forceStopPhone()
        }
    }

    override fun onPhoneCallRecordingEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneCallRecordingEnabled.set(enabled)
            forceStopPhone()
        }
    }

    override fun onPsiAppsChanged(enabled: Boolean) {
        viewModelScope.launch {
            propertiesRepository.setPsiApps(enabled)
        }
    }

    override fun onPsiForceAccountPresenceChanged(enabled: Boolean) {
        viewModelScope.launch {
            propertiesRepository.setPsiForceAccountPresence(enabled)
        }
    }

    override fun onPsiForceAccountTypeChanged(enabled: Boolean) {
        viewModelScope.launch {
            propertiesRepository.setPsiForceAccountType(enabled)
        }
    }

    override fun onPsiForceAdminAllowanceChanged(enabled: Boolean) {
        viewModelScope.launch {
            propertiesRepository.setPsiForceAdminAllowance(enabled)
        }
    }

    private suspend fun forceStopPhone() {
        deviceConfigPropertiesRepository.forceStopPackage(PACKAGE_NAME_PHONE)
    }

}