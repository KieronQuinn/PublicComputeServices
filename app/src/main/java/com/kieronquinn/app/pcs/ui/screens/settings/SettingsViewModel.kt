package com.kieronquinn.app.pcs.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.kieronquinn.app.pcs.model.Release
import com.kieronquinn.app.pcs.repositories.ManifestRepository
import com.kieronquinn.app.pcs.repositories.ManifestRepository.ManifestState
import com.kieronquinn.app.pcs.repositories.NavigationRepository
import com.kieronquinn.app.pcs.repositories.NavigationRepository.Destination
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository.PhenotypeState
import com.kieronquinn.app.pcs.repositories.PropertiesRepository
import com.kieronquinn.app.pcs.repositories.SettingsRepository
import com.kieronquinn.app.pcs.repositories.SyncRepository
import com.kieronquinn.app.pcs.repositories.UpdateRepository
import com.kieronquinn.app.pcs.work.RefreshWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class SettingsViewModel: ViewModel() {

    abstract val state: StateFlow<State>

    abstract fun onSyncClicked()
    abstract fun onRefreshClicked()
    abstract fun onFaqClicked()
    abstract fun onBuildLabelClicked()
    abstract fun onDebugChanged(enabled: Boolean)
    abstract fun onExperimentsClicked()
    abstract fun onAutoSyncChanged(enabled: Boolean)
    abstract fun onDestinationSelected(destination: Destination)

    sealed class State {
        data object Loading: State()
        data class Loaded(
            val syncState: SyncState,
            val phenotypeState: PhenotypeState.Loaded,
            val propertiesState: PropertiesRepository.State,
            val updateState: Release?,
            val autoSync: Boolean
        ): State()
    }

    enum class SyncState {
        NOT_REQUIRED, REQUIRED, LOADING, SYNCING, ERROR
    }

}

class SettingsViewModelImpl(
    private val navigationRepository: NavigationRepository,
    private val propertiesRepository: PropertiesRepository,
    private val syncRepository: SyncRepository,
    private val settingsRepository: SettingsRepository,
    phenotypeRepository: PhenotypeRepository,
    manifestRepository: ManifestRepository,
    updateRepository: UpdateRepository,
    context: Context
): SettingsViewModel() {

    private val phenotypeState = phenotypeRepository.state.filterIsInstance<PhenotypeState.Loaded>()
    private val workManager = WorkManager.getInstance(context)
    private val autoSync = settingsRepository.autoSync.asFlow()
    private val refreshBus = MutableStateFlow(System.currentTimeMillis())

    private val syncRequired = refreshBus.mapLatest {
        // We use the raw state to show the error in the UI, so don't emit null
        syncRepository.getSyncRequired() ?: emptyMap()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val syncState = combine(
        manifestRepository.state,
        phenotypeRepository.state,
        syncRequired.filterNotNull()
    ) { manifest, phenotypeState, syncRequired ->
        val phenotype = phenotypeState?.unwrap()
        when {
            manifest is ManifestState.Loading -> SyncState.LOADING
            phenotype is PhenotypeState.Loading -> SyncState.LOADING
            phenotype is PhenotypeState.Applying -> SyncState.SYNCING
            manifest is ManifestState.Error -> SyncState.ERROR
            syncRequired.isNotEmpty() -> SyncState.REQUIRED
            else -> SyncState.NOT_REQUIRED
        }
    }

    private val updateState = flow {
        emit(updateRepository.getUpdate())
    }

    override val state = combine(
        phenotypeState,
        syncState,
        propertiesRepository.state,
        updateState,
        autoSync
    ) { phenotypeState, syncState, propertiesState, updateState, autoSync ->
        State.Loaded(
            syncState = syncState,
            phenotypeState = phenotypeState,
            propertiesState = propertiesState,
            updateState = updateState,
            autoSync = autoSync
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State.Loading)

    override fun onBuildLabelClicked() {
        viewModelScope.launch {
            navigationRepository.navigateTo(Destination.SelectBuildLabel)
        }
    }

    override fun onSyncClicked() {
        viewModelScope.launch {
            val versions = syncRequired.value ?: return@launch
            syncRepository.performSync(versions, true)
            refreshBus.emit(System.currentTimeMillis())
        }
    }

    override fun onRefreshClicked() {
        viewModelScope.launch {
            refreshBus.emit(System.currentTimeMillis())
        }
    }

    override fun onFaqClicked() {
        viewModelScope.launch {
            navigationRepository.navigateTo(Destination.FAQ)
        }
    }

    override fun onDebugChanged(enabled: Boolean) {
        viewModelScope.launch {
            propertiesRepository.setDebug(enabled)
        }
    }

    override fun onExperimentsClicked() {
        viewModelScope.launch {
            navigationRepository.navigateTo(Destination.Experiments)
        }
    }

    override fun onAutoSyncChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.autoSync.set(enabled)
            RefreshWorker.setEnabled(workManager, enabled)
        }
    }

    override fun onDestinationSelected(destination: Destination) {
        viewModelScope.launch {
            navigationRepository.navigateTo(destination)
        }
    }

    private fun syncWorker() = viewModelScope.launch {
        val enabled = autoSync.first()
        RefreshWorker.setEnabled(workManager, enabled)
    }

    init {
        syncWorker()
        manifestRepository.refresh()
    }

}