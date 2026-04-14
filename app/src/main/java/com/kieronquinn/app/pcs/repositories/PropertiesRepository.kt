package com.kieronquinn.app.pcs.repositories

import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_AGENT
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_PHONE
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_PSI
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_TTS
import com.kieronquinn.app.pcs.model.ClientGroupOverride
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.AGENT_ENABLED
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.AS_SHOW_NOW_PLAYING_NOTIFICATION
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.DEBUG_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.PHONE_ENABLED
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.PSI_CLIENT_GROUP_OVERRIDE_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.PSI_ENABLE_APPS_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.PSI_FORCE_ACCOUNT_PRESENCE_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.PSI_FORCE_ACCOUNT_TYPE_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.PSI_FORCE_ADMIN_ALLOWANCE_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.TTS_ENABLED
import com.kieronquinn.app.pcs.repositories.PropertiesRepository.State
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_get
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_getBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

interface PropertiesRepository {

    val state: StateFlow<State>

    suspend fun setDebug(enabled: Boolean)
    suspend fun setPsiApps(enabled: Boolean)
    suspend fun setPsiForceAccountPresence(enabled: Boolean)
    suspend fun setPsiForceAccountType(enabled: Boolean)
    suspend fun setPsiForceAdminAllowance(enabled: Boolean)
    suspend fun setAsNowPlayingNotificationEnabled(enabled: Boolean)
    suspend fun setClientGroupOverride(override: ClientGroupOverride)
    suspend fun setPhoneEnabled(enabled: Boolean)
    suspend fun setTtsEnabled(enabled: Boolean)
    suspend fun setAgentEnabled(enabled: Boolean)

    data class State(
        val debug: Boolean = false,
        val psiApps: Boolean = false,
        val psiForceAccountPresence: Boolean = false,
        val psiForceAccountType: Boolean = false,
        val psiForceAdminAllowance: Boolean = false,
        val asNowPlayingNotificationEnabled: Boolean = false,
        val phoneEnabled: Boolean = false,
        val ttsEnabled: Boolean = false,
        val agentEnabled: Boolean = false,
        val clientGroupOverride: ClientGroupOverride = ClientGroupOverride.DISABLED
    )

}

class PropertiesRepositoryImpl(
    private val deviceConfigPropertiesRepository: DeviceConfigPropertiesRepository
): PropertiesRepository {

    private val refreshBus = MutableStateFlow(System.currentTimeMillis())
    private val scope = MainScope()

    override val state = refreshBus.mapLatest {
        getState()
    }.flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, getState())

    override suspend fun setDebug(enabled: Boolean) {
        deviceConfigPropertiesRepository.setProperty(DEBUG_PROPERTY_NAME, enabled.toString())
        refreshBus.emit(System.currentTimeMillis())
    }

    override suspend fun setPsiApps(enabled: Boolean) {
        deviceConfigPropertiesRepository.setProperty(PSI_ENABLE_APPS_PROPERTY_NAME, enabled.toString())
        deviceConfigPropertiesRepository.forceStopPackage(PACKAGE_NAME_PSI)
        refreshBus.emit(System.currentTimeMillis())
    }

    override suspend fun setPsiForceAccountPresence(enabled: Boolean) {
        deviceConfigPropertiesRepository.setProperty(PSI_FORCE_ACCOUNT_PRESENCE_PROPERTY_NAME, enabled.toString())
        refreshBus.emit(System.currentTimeMillis())
    }

    override suspend fun setPsiForceAccountType(enabled: Boolean) {
        deviceConfigPropertiesRepository.setProperty(PSI_FORCE_ACCOUNT_TYPE_PROPERTY_NAME, enabled.toString())
        refreshBus.emit(System.currentTimeMillis())
    }

    override suspend fun setPsiForceAdminAllowance(enabled: Boolean) {
        deviceConfigPropertiesRepository.setProperty(PSI_FORCE_ADMIN_ALLOWANCE_PROPERTY_NAME, enabled.toString())
        refreshBus.emit(System.currentTimeMillis())
    }

    override suspend fun setAsNowPlayingNotificationEnabled(enabled: Boolean) {
        deviceConfigPropertiesRepository.setProperty(AS_SHOW_NOW_PLAYING_NOTIFICATION, enabled.toString())
        refreshBus.emit(System.currentTimeMillis())
    }

    override suspend fun setClientGroupOverride(override: ClientGroupOverride) {
        deviceConfigPropertiesRepository.setProperty(PSI_CLIENT_GROUP_OVERRIDE_PROPERTY_NAME, override.name)
        refreshBus.emit(System.currentTimeMillis())
    }

    override suspend fun setPhoneEnabled(enabled: Boolean) {
        deviceConfigPropertiesRepository.setProperty(PHONE_ENABLED, enabled.toString())
        deviceConfigPropertiesRepository.forceStopPackage(PACKAGE_NAME_PHONE)
        refreshBus.emit(System.currentTimeMillis())
    }

    override suspend fun setTtsEnabled(enabled: Boolean) {
        deviceConfigPropertiesRepository.setProperty(TTS_ENABLED, enabled.toString())
        deviceConfigPropertiesRepository.forceStopPackage(PACKAGE_NAME_TTS)
        refreshBus.emit(System.currentTimeMillis())
    }

    override suspend fun setAgentEnabled(enabled: Boolean) {
        deviceConfigPropertiesRepository.setProperty(AGENT_ENABLED, enabled.toString())
        deviceConfigPropertiesRepository.forceStopPackage(PACKAGE_NAME_AGENT)
        refreshBus.emit(System.currentTimeMillis())
    }

    private fun getState(): State {
        return State(
            SystemProperties_getBoolean(DEBUG_PROPERTY_NAME, false),
            SystemProperties_getBoolean(PSI_ENABLE_APPS_PROPERTY_NAME, false),
            SystemProperties_getBoolean(PSI_FORCE_ACCOUNT_PRESENCE_PROPERTY_NAME, false),
            SystemProperties_getBoolean(PSI_FORCE_ACCOUNT_TYPE_PROPERTY_NAME, false),
            SystemProperties_getBoolean(PSI_FORCE_ADMIN_ALLOWANCE_PROPERTY_NAME, false),
            SystemProperties_getBoolean(AS_SHOW_NOW_PLAYING_NOTIFICATION, false),
            SystemProperties_getBoolean(PHONE_ENABLED, false),
            SystemProperties_getBoolean(TTS_ENABLED, false),
            SystemProperties_getBoolean(AGENT_ENABLED, false),
            ClientGroupOverride.from(SystemProperties_get(PSI_CLIENT_GROUP_OVERRIDE_PROPERTY_NAME))
        )
    }

}