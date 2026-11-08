package com.kieronquinn.app.pcs.ui.screens.experiments

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_AGENT
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_JOURNAL
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_WEATHER
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_AIC
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_AS
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_PHONE
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_PSI
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_TTS
import com.kieronquinn.app.pcs.phonestrings.PhoneStrings
import com.kieronquinn.app.pcs.translation.PhoneLanguageTranslator
import com.kieronquinn.app.pcs.translation.TranslationModelWorker
import com.kieronquinn.app.pcs.model.ClientGroupOverride
import com.kieronquinn.app.pcs.directmycall.MenuParser
import com.kieronquinn.app.pcs.voicepack.HoldForMePrompts
import com.kieronquinn.app.pcs.voicepack.VoicePack
import com.kieronquinn.app.pcs.voicepack.VoicePackTranslations
import com.kieronquinn.app.pcs.model.phone.PhoneSettings
import com.kieronquinn.app.pcs.repositories.VoicePackRepository
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository
import com.kieronquinn.app.pcs.repositories.PropertiesRepository
import com.kieronquinn.app.pcs.repositories.SettingsRepository
import com.kieronquinn.app.pcs.repositories.SettingsRepository.BeeslyRegion
import com.kieronquinn.app.pcs.repositories.SettingsRepository.CallScreenMode
import com.kieronquinn.app.pcs.repositories.SettingsRepository.DobbyRegion
import com.kieronquinn.app.pcs.repositories.SettingsRepository.PatrickPhase
import com.kieronquinn.app.pcs.repositories.SettingsRepository.SharpieCountryMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.io.FileNotFoundException
import kotlinx.coroutines.launch

abstract class ExperimentsViewModel: ViewModel() {

    abstract val state: StateFlow<State>
    abstract val events: Flow<Event>

    abstract fun onPhoneDebugLoggingChanged(enabled: Boolean)
    abstract fun onPhoneCallScreenModeChanged(mode: CallScreenMode)
    abstract fun onPhoneSharpieCountryModeChanged(mode: SharpieCountryMode)
    abstract fun onPhoneSharpieCountryChanged(country: String)
    abstract fun onPhoneSharpieEnabledChanged(enabled: Boolean)
    abstract fun onPhoneSharpiePhoneLanguageChanged(enabled: Boolean)
    abstract fun onPhoneDobbyEnabledChanged(enabled: Boolean)
    abstract fun onPhoneDobbyRegionChanged(region: DobbyRegion)
    abstract fun onCallScreenPackClicked()
    abstract fun onResume()
    abstract fun onPhoneAtlasEnabledChanged(enabled: Boolean)
    abstract fun onPhoneAtlasPhoneLanguageChanged(enabled: Boolean)
    abstract fun onPhoneBeeslyEnabledChanged(enabled: Boolean)
    abstract fun onPhoneBeeslyRegionChanged(region: BeeslyRegion)
    abstract fun onPhoneBeeslyPhoneLanguageChanged(enabled: Boolean)
    abstract fun onTakeAMessagePackClicked()
    abstract fun onHoldForMePackClicked()
    abstract fun onPhoneNautilusEnabledChanged(enabled: Boolean)
    abstract fun onPhoneSonicEnabledChanged(enabled: Boolean)
    abstract fun onPhoneXatuAllowMeteredChanged(enabled: Boolean)
    abstract fun onPhoneXatuEnabledChanged(enabled: Boolean)
    abstract fun onPhoneXatuPhoneLanguageChanged(enabled: Boolean)
    abstract fun onPhoneAtlasEveryNumberChanged(enabled: Boolean)
    abstract fun onPhoneXatuEveryNumberChanged(enabled: Boolean)
    abstract fun onPhoneStringsPhoneLanguageChanged(enabled: Boolean)
    abstract fun onPhoneFermatPhoneLanguageChanged(enabled: Boolean)
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
    abstract fun onAsForceGSAChanged(enabled: Boolean)
    abstract fun onAiCoreUnloadInferenceChanged(enabled: Boolean)
    abstract fun onAsNotificationOrganizerChanged(enabled: Boolean)
    abstract fun onAsGeminiDailyBriefChanged(enabled: Boolean)

    abstract fun onPhoneEnabledChanged(enabled: Boolean)
    abstract fun onTtsEnabledChanged(enabled: Boolean)
    abstract fun onAgentEnabledChanged(enabled: Boolean)
    abstract fun onAgentScreenshotsLanguageChanged(enabled: Boolean)
    abstract fun onWeatherSummaryLanguageChanged(enabled: Boolean)
    abstract fun onJournalLanguageChanged(enabled: Boolean)
    abstract fun onClearMddClicked()
    abstract fun onClearOverridesClicked()
    abstract fun onClientGroupOverrideChanged(override: ClientGroupOverride)

    sealed class State {
        data object Loading: State()
        data class Loaded(
            val magicCueAvailable: Boolean,
            val nowPlayingAvailable: Boolean,
            val aicoreAvailable: Boolean,
            val phoneAvailable: Boolean,
            val agentAvailable: Boolean,
            val weatherAvailable: Boolean,
            val journalAvailable: Boolean,
            val phoneSettings: PhoneSettings,
            val propertiesState: PropertiesRepository.State,
            /** Regions offered for Call Screen; [DobbyRegion.PHONE] only with a translation. */
            val dobbyRegions: List<DobbyRegion>,
            val callScreenPack: VoicePackRepository.State,
            /** Whether PCS ships a Take a Message translation for the phone's language. */
            val takeAMessageTranslationAvailable: Boolean,
            val takeAMessagePack: VoicePackRepository.State,
            /** Whether PCS ships Hold for Me's sentences for the phone's language. */
            val holdForMeTranslationAvailable: Boolean,
            val holdForMePack: VoicePackRepository.State,
            /** Whether PCS can read phone menus in the phone's language. */
            val directMyCallMenuParserAvailable: Boolean,
            /** Whether PCS ships Phone's English-only text in the phone's language. */
            val phoneStringsTranslationAvailable: Boolean,
            /** Whether on-device translation into the phone's language is available. */
            val callNotesTranslationAvailable: Boolean
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
    private val callScreenPackRepository: VoicePackRepository,
    private val takeAMessagePackRepository: VoicePackRepository,
    private val holdForMePackRepository: VoicePackRepository,
    settingsRepository: SettingsRepository,
    context: Context
): ExperimentsViewModel() {

    private val appContext: Context = context.applicationContext
    private val phoneManifestHandlingEnabled = settingsRepository.phoneManifestHandlingEnabled
    private val phoneDebugLogging = settingsRepository.phoneDebugLogging

    private val phoneCallScreenMode = settingsRepository.phoneCallScreenMode
    private val phoneSharpieCountryMode = settingsRepository.phoneSharpieCountryMode
    private val phoneSharpieCountry = settingsRepository.phoneSharpieCountry
    private val phoneSharpieEnabled = settingsRepository.phoneSharpieEnabled
    private val phoneSharpiePhoneLanguage = settingsRepository.phoneSharpiePhoneLanguage
    private val phoneDobbyEnabled = settingsRepository.phoneDobbyEnabled
    private val phoneDobbyRegion = settingsRepository.phoneDobbyRegion
    private val phoneDobbyUrl = settingsRepository.phoneDobbyUrl
    private val phoneDobbyDuplexFiles = settingsRepository.phoneDobbyDuplexFiles
    private val phoneAtlasEnabled = settingsRepository.phoneAtlasEnabled
    private val phoneAtlasModels = settingsRepository.phoneAtlasModels
    private val phoneAtlasPhoneLanguage = settingsRepository.phoneAtlasPhoneLanguage
    private val phoneBeeslyEnabled = settingsRepository.phoneBeeslyEnabled
    private val phoneBeeslyRegion = settingsRepository.phoneBeeslyRegion
    private val phoneBeesly = settingsRepository.phoneBeesly
    private val phoneBeeslyPhoneLanguage = settingsRepository.phoneBeeslyPhoneLanguage
    private val phoneNautilusEnabled = settingsRepository.phoneNautilusEnabled
    private val phoneSonicEnabled = settingsRepository.phoneSonicEnabled
    private val phoneXatuAllowMetered = settingsRepository.phoneXatuAllowMetered
    private val phoneXatuPhoneLanguage = settingsRepository.phoneXatuPhoneLanguage
    private val phoneAtlasEveryNumber = settingsRepository.phoneAtlasEveryNumber
    private val phoneXatuEveryNumber = settingsRepository.phoneXatuEveryNumber
    private val phoneStringsPhoneLanguage = settingsRepository.phoneStringsPhoneLanguage
    private val phoneFermatPhoneLanguage = settingsRepository.phoneFermatPhoneLanguage
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

    private val isWeatherAvailable = flow {
        try {
            context.packageManager.getPackageInfo(PACKAGE_NAME_WEATHER, 0)
            emit(true)
        } catch (e: PackageManager.NameNotFoundException) {
            emit(false)
        }
    }

    private val isJournalAvailable = flow {
        try {
            context.packageManager.getPackageInfo(PACKAGE_NAME_JOURNAL, 0)
            emit(true)
        } catch (e: PackageManager.NameNotFoundException) {
            emit(false)
        }
    }

    private val isAiCoreAvailable = flow {
        try {
            context.packageManager.getPackageInfo(PACKAGE_NAME_AIC, 0)
            emit(true)
        } catch (e: PackageManager.NameNotFoundException) {
            emit(false)
        }
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
        phoneCallRecordingEnabled.asFlow(),
        phoneXatuAllowMetered.asFlow(),
        phoneManifestHandlingEnabled.asFlow(),
        phoneDebugLogging.asFlow(),
        phoneSharpiePhoneLanguage.asFlow(),
        phoneAtlasPhoneLanguage.asFlow(),
        phoneBeeslyPhoneLanguage.asFlow(),
        phoneXatuPhoneLanguage.asFlow(),
        phoneAtlasEveryNumber.asFlow(),
        phoneXatuEveryNumber.asFlow(),
        phoneStringsPhoneLanguage.asFlow(),
        phoneFermatPhoneLanguage.asFlow()
    ) {
        it
    }

    private val phoneEnumSettings = combine(
        phoneDobbyRegion.asFlow(),
        phoneBeeslyRegion.asFlow(),
        phonePatrickPhase.asFlow(),
        phoneCallScreenMode.asFlow(),
        phoneSharpieCountryMode.asFlow()
    ) {
        it
    }

    private val phoneStringSettings = combine(
        phoneDobbyUrl.asFlow(),
        phoneBeesly.asFlow(),
        phoneDobbyDuplexFiles.asFlow(),
        phoneAtlasModels.asFlow(),
        phoneXatuModels.asFlow(),
        phoneSharpieCountry.asFlow()
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
            booleans[10],
            callScreenMode = enums[3] as CallScreenMode,
            sharpieCountryMode = enums[4] as SharpieCountryMode,
            sharpieCountry = strings[5],
            xatuAllowMetered = booleans[11],
            manifestHandlingEnabled = booleans[12],
            debugLogging = booleans[13],
            sharpiePhoneLanguage = booleans[14],
            atlasPhoneLanguage = booleans[15],
            beeslyPhoneLanguage = booleans[16],
            xatuPhoneLanguage = booleans[17],
            atlasEveryNumber = booleans[18],
            xatuEveryNumber = booleans[19],
            stringsPhoneLanguage = booleans[20],
            fermatPhoneLanguage = booleans[21]
        )
    }

    /** Whether PCS ships a translation of [pack] for the phone's language. */
    private fun isTranslationAvailable(pack: VoicePack) = isTranslationAvailable(pack.assetDirectory)

    private fun isTranslationAvailable(assetDirectory: String) = flow {
        emit(
            try {
                VoicePackTranslations.forPhone(appContext, assetDirectory) != null
            } catch (e: Exception) {
                false
            }
        )
    }.flowOn(Dispatchers.IO)

    private val isPhoneStringsTranslationAvailable = flow {
        emit(
            try {
                PhoneStrings.forLocale(VoicePackTranslations.phoneLocale()) { path ->
                    try {
                        appContext.assets.open(path)
                    } catch (e: FileNotFoundException) {
                        null
                    }
                } != null
            } catch (e: Exception) {
                false
            }
        )
    }.flowOn(Dispatchers.IO)

    private val isPhoneLanguageCallScreenAvailable = isTranslationAvailable(VoicePack.CALL_SCREEN)

    private val dobbyRegions = isPhoneLanguageCallScreenAvailable.combine(phoneDobbyRegion.asFlow()) {
            available, selected ->
        // Keep PHONE listed while it is selected, so the setting stays visible and can be changed.
        DobbyRegion.entries.filter { it != DobbyRegion.PHONE || available || selected == it }
    }

    private val packageStates = combine(
        isAgentAvailable,
        isPhoneAvailable,
        isMagicCueAvailable,
        isNowPlayingAvailable,
        isAiCoreAvailable,
        isWeatherAvailable,
        isJournalAvailable
    ) {
        it
    }

    private data class Packs(
        val callScreen: VoicePackRepository.State,
        val takeAMessageTranslationAvailable: Boolean,
        val takeAMessage: VoicePackRepository.State,
        val holdForMeTranslationAvailable: Boolean,
        val holdForMe: VoicePackRepository.State
    )

    private val packs = combine(
        callScreenPackRepository.state,
        isTranslationAvailable(VoicePack.TAKE_A_MESSAGE),
        takeAMessagePackRepository.state,
        isTranslationAvailable(HoldForMePrompts.ASSET_DIRECTORY),
        holdForMePackRepository.state,
        ::Packs
    )

    override val state = combine(
        packageStates,
        propertiesRepository.state,
        phoneSettings,
        dobbyRegions.combine(isPhoneStringsTranslationAvailable, ::Pair),
        packs
    ) { packageStates, propertiesState, phoneSettings, (dobbyRegions, phoneStringsAvailable), packs ->
        val (isAgentAvailable, isPhoneAvailable, magicCueAvailable, nowPlayingAvailable,
            isAiCoreAvailable) = packageStates
        val isWeatherAvailable = packageStates[5]
        val isJournalAvailable = packageStates[6]
        State.Loaded(
            magicCueAvailable = magicCueAvailable,
            nowPlayingAvailable = nowPlayingAvailable,
            aicoreAvailable = isAiCoreAvailable,
            phoneAvailable = isPhoneAvailable,
            agentAvailable = isAgentAvailable,
            weatherAvailable = isWeatherAvailable,
            journalAvailable = isJournalAvailable,
            phoneSettings = phoneSettings,
            propertiesState = propertiesState,
            dobbyRegions = dobbyRegions,
            callScreenPack = packs.callScreen,
            takeAMessageTranslationAvailable = packs.takeAMessageTranslationAvailable,
            takeAMessagePack = packs.takeAMessage,
            holdForMeTranslationAvailable = packs.holdForMeTranslationAvailable,
            holdForMePack = packs.holdForMe,
            directMyCallMenuParserAvailable = MenuParser.forLocale(VoicePackTranslations.phoneLocale()) != null,
            phoneStringsTranslationAvailable = phoneStringsAvailable,
            callNotesTranslationAvailable = PhoneLanguageTranslator.targetLanguage() != null
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State.Loading)

    override val events = MutableSharedFlow<Event>()

    override fun onPhoneDebugLoggingChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneDebugLogging.set(enabled)
            forceStopPhone()
        }
    }

    override fun onPhoneCallScreenModeChanged(mode: CallScreenMode) {
        viewModelScope.launch {
            phoneCallScreenMode.set(mode)
            forceStopPhone()
        }
    }

    override fun onPhoneSharpieCountryModeChanged(mode: SharpieCountryMode) {
        viewModelScope.launch {
            phoneSharpieCountryMode.set(mode)
            forceStopPhone()
        }
    }

    override fun onPhoneSharpieCountryChanged(country: String) {
        viewModelScope.launch {
            phoneSharpieCountry.set(country)
            forceStopPhone()
        }
    }

    override fun onPhoneSharpieEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneSharpieEnabled.set(enabled)
            forceStopPhone()
        }
    }

    override fun onPhoneSharpiePhoneLanguageChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneSharpiePhoneLanguage.set(enabled)
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
        if (region == DobbyRegion.PHONE) callScreenPackRepository.refresh()
    }

    override fun onCallScreenPackClicked() = onPackClicked(callScreenPackRepository)

    override fun onTakeAMessagePackClicked() = onPackClicked(takeAMessagePackRepository)

    override fun onHoldForMePackClicked() = onPackClicked(holdForMePackRepository)

    private fun onPackClicked(repository: VoicePackRepository) {
        when (repository.state.value) {
            is VoicePackRepository.State.Checking,
            is VoicePackRepository.State.Building,
            is VoicePackRepository.State.Unsupported -> Unit
            is VoicePackRepository.State.NoRoot,
            is VoicePackRepository.State.NoVoice,
            is VoicePackRepository.State.NoSource -> repository.refresh()
            is VoicePackRepository.State.NotBuilt,
            is VoicePackRepository.State.Ready,
            is VoicePackRepository.State.Failed -> repository.build()
        }
    }

    override fun onResume() {
        // Only check the pack when it is in use: checking needs root, which would otherwise
        // prompt every user who opens this screen.
        viewModelScope.launch {
            if (phoneDobbyEnabled.get() && phoneDobbyRegion.get() == DobbyRegion.PHONE) {
                callScreenPackRepository.refresh()
            }
            if (phoneBeeslyEnabled.get() && phoneBeeslyPhoneLanguage.get()) {
                takeAMessagePackRepository.refresh()
            }
            if (phoneAtlasEnabled.get() && phoneAtlasPhoneLanguage.get()) {
                holdForMePackRepository.refresh()
            }
        }
    }

    override fun onPhoneBeeslyPhoneLanguageChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneBeeslyPhoneLanguage.set(enabled)
            forceStopPhone()
        }
        if (enabled) takeAMessagePackRepository.refresh()
    }

    override fun onPhoneAtlasEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneAtlasEnabled.set(enabled)
            forceStopPhone()
        }
    }

    override fun onPhoneAtlasPhoneLanguageChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneAtlasPhoneLanguage.set(enabled)
            forceStopPhone()
        }
        if (enabled) holdForMePackRepository.refresh()
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

    override fun onPhoneXatuPhoneLanguageChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneXatuPhoneLanguage.set(enabled)
            forceStopPhone()
        }
    }

    override fun onPhoneAtlasEveryNumberChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneAtlasEveryNumber.set(enabled)
            forceStopPhone()
        }
    }

    override fun onPhoneXatuEveryNumberChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneXatuEveryNumber.set(enabled)
            forceStopPhone()
        }
    }

    override fun onPhoneStringsPhoneLanguageChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneStringsPhoneLanguage.set(enabled)
            forceStopPhone()
        }
    }

    override fun onPhoneFermatPhoneLanguageChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneFermatPhoneLanguage.set(enabled)
            // The model is needed before the first call's tasks arrive.
            if (enabled) TranslationModelWorker.enqueue(appContext)
            forceStopPhone()
        }
    }

    override fun onPhoneXatuAllowMeteredChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneXatuAllowMetered.set(enabled)
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

    override fun onAsForceGSAChanged(enabled: Boolean) {
        viewModelScope.launch {
            propertiesRepository.setAsForceGSAEnabled(enabled)
        }
    }

    override fun onAsGeminiDailyBriefChanged(enabled: Boolean) {
        viewModelScope.launch {
            propertiesRepository.setAsGeminiDailyBrief(enabled)
        }
    }

    override fun onAsNotificationOrganizerChanged(enabled: Boolean) {
        viewModelScope.launch {
            propertiesRepository.setAsNotificationOrganizer(enabled)
            // Notifications are classified in English: the model is needed first.
            if (enabled) TranslationModelWorker.enqueue(appContext)
        }
    }

    override fun onAiCoreUnloadInferenceChanged(enabled: Boolean) {
        viewModelScope.launch {
            propertiesRepository.setAiCoreUnloadInference(enabled)
        }
    }

    override fun onPhoneEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            phoneManifestHandlingEnabled.set(enabled)
            forceStopPhone()
        }
    }

    override fun onTtsEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            propertiesRepository.setTtsEnabled(enabled)
        }
    }

    override fun onAgentScreenshotsLanguageChanged(enabled: Boolean) {
        viewModelScope.launch {
            propertiesRepository.setAgentScreenshotsLanguage(enabled)
        }
    }

    override fun onWeatherSummaryLanguageChanged(enabled: Boolean) {
        viewModelScope.launch {
            propertiesRepository.setWeatherSummaryLanguage(enabled)
        }
    }

    override fun onJournalLanguageChanged(enabled: Boolean) {
        viewModelScope.launch {
            propertiesRepository.setJournalLanguage(enabled)
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