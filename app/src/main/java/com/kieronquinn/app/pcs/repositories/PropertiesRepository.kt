package com.kieronquinn.app.pcs.repositories

import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_PSI
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.DEBUG_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.PSI_ENABLE_APPS_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.PSI_FORCE_ACCOUNT_PRESENCE_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.PSI_FORCE_ACCOUNT_TYPE_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.PSI_FORCE_ADMIN_ALLOWANCE_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.PropertiesRepository.State
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

    data class State(
        val debug: Boolean = false,
        val psiApps: Boolean = false,
        val psiForceAccountPresence: Boolean = false,
        val psiForceAccountType: Boolean = false,
        val psiForceAdminAllowance: Boolean = false
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

    private fun getState(): State {
        return State(
            SystemProperties_getBoolean(DEBUG_PROPERTY_NAME, false),
            SystemProperties_getBoolean(PSI_ENABLE_APPS_PROPERTY_NAME, false),
            SystemProperties_getBoolean(PSI_FORCE_ACCOUNT_PRESENCE_PROPERTY_NAME, false),
            SystemProperties_getBoolean(PSI_FORCE_ACCOUNT_TYPE_PROPERTY_NAME, false),
            SystemProperties_getBoolean(PSI_FORCE_ADMIN_ALLOWANCE_PROPERTY_NAME, false)
        )
    }

}