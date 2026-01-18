package com.kieronquinn.app.pcs.repositories

import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.DEBUG_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.PHONE_FLAGS_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.PropertiesRepository.State
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_getBoolean
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

interface PropertiesRepository {

    val state: StateFlow<State>

    suspend fun setDebug(enabled: Boolean)
    suspend fun setPhoneFlags(enabled: Boolean)

    data class State(
        val debug: Boolean,
        val phoneFlags: Boolean
    )

}

class PropertiesRepositoryImpl(
    private val deviceConfigPropertiesRepository: DeviceConfigPropertiesRepository
): PropertiesRepository {

    private val refreshBus = MutableStateFlow(System.currentTimeMillis())
    private val scope = MainScope()

    override val state = refreshBus.mapLatest {
        getState()
    }.stateIn(scope, SharingStarted.Eagerly, getState())

    override suspend fun setDebug(enabled: Boolean) {
        deviceConfigPropertiesRepository.setProperty(DEBUG_PROPERTY_NAME, enabled.toString())
        refreshBus.emit(System.currentTimeMillis())
    }

    override suspend fun setPhoneFlags(enabled: Boolean) {
        deviceConfigPropertiesRepository.setProperty(PHONE_FLAGS_PROPERTY_NAME, enabled.toString())
        refreshBus.emit(System.currentTimeMillis())
    }

    private fun getState(): State {
        return State(
            SystemProperties_getBoolean(DEBUG_PROPERTY_NAME, false),
            SystemProperties_getBoolean(PHONE_FLAGS_PROPERTY_NAME, false)
        )
    }

}