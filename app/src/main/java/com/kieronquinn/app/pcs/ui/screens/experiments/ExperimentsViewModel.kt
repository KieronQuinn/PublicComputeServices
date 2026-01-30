package com.kieronquinn.app.pcs.ui.screens.experiments

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_PSI
import com.kieronquinn.app.pcs.repositories.PropertiesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class ExperimentsViewModel: ViewModel() {

    abstract val state: StateFlow<State>

    abstract fun onPhoneFlagsChanged(enabled: Boolean)
    abstract fun onPsiAppsChanged(enabled: Boolean)
    abstract fun onPsiForceAccountPresenceChanged(enabled: Boolean)
    abstract fun onPsiForceAccountTypeChanged(enabled: Boolean)
    abstract fun onPsiForceAdminAllowanceChanged(enabled: Boolean)

    sealed class State {
        data object Loading: State()
        data class Loaded(
            val magicCueAvailable: Boolean,
            val propertiesState: PropertiesRepository.State
        ): State()
    }

}

class ExperimentsViewModelImpl(
    private val propertiesRepository: PropertiesRepository,
    context: Context
): ExperimentsViewModel() {

    private val isMagicCueAvailable = flow {
        val versionName = try {
            context.packageManager.getPackageInfo(PACKAGE_NAME_PSI, 0)
                ?.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        emit(versionName != null && !versionName.contains("stub"))
    }

    override val state = combine(
        isMagicCueAvailable,
        propertiesRepository.state
    ) { magicCueAvailable, propertiesState ->
        State.Loaded(
            magicCueAvailable = magicCueAvailable,
            propertiesState = propertiesState
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State.Loading)

    override fun onPhoneFlagsChanged(enabled: Boolean) {
        viewModelScope.launch {
            propertiesRepository.setPhoneFlags(enabled)
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

}