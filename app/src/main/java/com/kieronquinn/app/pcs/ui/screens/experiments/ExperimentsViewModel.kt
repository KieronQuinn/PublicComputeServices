package com.kieronquinn.app.pcs.ui.screens.experiments

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_AGENT
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_AIC
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_AS
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_PHONE
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_PSI
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_TTS
import com.kieronquinn.app.pcs.model.ClientGroupOverride
import com.kieronquinn.app.pcs.model.phone.PhoneSettings
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository
import com.kieronquinn.app.pcs.repositories.PropertiesRepository
import com.kieronquinn.app.pcs.repositories.SettingsRepository
import com.kieronquinn.app.pcs.repositories.SettingsRepository.BeeslyRegion
import com.kieronquinn.app.pcs.repositories.SettingsRepository.DobbyRegion
import com.kieronquinn.app.pcs.repositories.SettingsRepository.PatrickPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class ExperimentsViewModel: ViewModel() {

    abstract val state: StateFlow<State>
    abstract val events: Flow<Event>

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

    abstract fun onAsNowPlayingChanged(enabled: Boolean)

    abstract fun onPhoneEnabledChanged(enabled: Boolean)
    abstract fun onTtsEnabledChanged(enabled: Boolean)
    abstract fun onAgentEnabledChanged(enabled: Boolean)
    abstract fun onClearMddClicked()
    abstract fun onClearOverridesClicked()
    abstract fun onClientGroupOverrideChanged(override: ClientGroupOverride)

    sealed class State {
        data object Loading: State()
        data class Loaded(
            val magicCueAvailable: Boolean,
            val nowPlayingAvailable: Boolean,
            val phoneAvailable: Boolean,
            val agentAvailable: Boolean,
            val phoneSettings: PhoneSettings,
            val propertiesState: PropertiesRepository.State
        ): State()
    }

    enum class Event {
        MANIFESTS_PURGED, MANIFEST_OVERRIDES_CLEARED
    }

}

class ExperimentsViewModelImpl(
    private val propertiesRepository: PropertiesRepository,
    private val deviceConfigPropertiesRepository: DeviceConfigPropertiesRepository,
    private val phenotypeRepository: PhenotypeRepository,
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

    private val isPhoneAvailable = flow {
        val versionName = try {
            context.packageManager.getPackageInfo(PACKAGE_NAME_PHONE, 0)
                ?.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        emit(versionName != null && versionName.contains("pixel"))
    }

    private val isAgentAvailable = flow {
        val versionName = try {
            context.packageManager.getPackageInfo(PACKAGE_NAME_AGENT, 0)
                ?.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        emit(versionName != null && !versionName.contains("stub"))
    }

    private val isNowPlayingAvailable = flow {
        try {
            context.packageManager.getServiceInfo(
                ComponentName(
                    PACKAGE_NAME_AS,
                    "com.google.android.apps.miphone.aiai.nowplaying.api.NowPlayingService"
                ),
                0
            )
            emit(true)
        } catch (e: PackageManager.NameNotFoundException) {
            emit(false)
        }
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

    private val packageStates = combine(
        isAgentAvailable,
        isPhoneAvailable,
        isMagicCueAvailable,
        isNowPlayingAvailable
    ) {
        it
    }

    override val state = combine(
        packageStates,
        propertiesRepository.state,
        phoneSettings
    ) { packageStates, propertiesState, phoneSettings ->
        val (isAgentAvailable, isPhoneAvailable, magicCueAvailable, nowPlayingAvailable) = packageStates
        State.Loaded(
            magicCueAvailable = magicCueAvailable,
            nowPlayingAvailable = nowPlayingAvailable,
            phoneAvailable = isPhoneAvailable,
            agentAvailable = isAgentAvailable,
            phoneSettings = phoneSettings,
            propertiesState = propertiesState
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State.Loading)

    override val events = MutableSharedFlow<Event>()

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

    override fun onAsNowPlayingChanged(enabled: Boolean) {
        viewModelScope.launch {
            propertiesRepository.setAsNowPlayingNotificationEnabled(enabled)
        }
    }

    override fun onPhoneEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            propertiesRepository.setPhoneEnabled(enabled)
        }
    }

    override fun onTtsEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            propertiesRepository.setTtsEnabled(enabled)
        }
    }

    override fun onAgentEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            propertiesRepository.setAgentEnabled(enabled)
        }
    }

    override fun onClearMddClicked() {
        viewModelScope.launch {
            deviceConfigPropertiesRepository.clearMdd(PACKAGE_NAME_AIC)
            deviceConfigPropertiesRepository.clearMdd(PACKAGE_NAME_PSI)
            deviceConfigPropertiesRepository.clearMdd(PACKAGE_NAME_PHONE)
            deviceConfigPropertiesRepository.clearMdd(PACKAGE_NAME_AGENT)
            deviceConfigPropertiesRepository.clearMdd(PACKAGE_NAME_TTS)
            events.emit(Event.MANIFESTS_PURGED)
        }
    }

    override fun onClearOverridesClicked() {
        viewModelScope.launch {
            phenotypeRepository.resetVersions()
            events.emit(Event.MANIFEST_OVERRIDES_CLEARED)
        }
    }

    override fun onClientGroupOverrideChanged(override: ClientGroupOverride) {
        viewModelScope.launch {
            propertiesRepository.setClientGroupOverride(override)
        }
    }

    private suspend fun forceStopPhone() {
        deviceConfigPropertiesRepository.forceStopPackage(PACKAGE_NAME_PHONE)
    }

}