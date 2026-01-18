package com.kieronquinn.app.pcs.ui.screens.buildlabel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.pcs.model.BuildLabel
import com.kieronquinn.app.pcs.model.proto.Labels
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class BuildLabelViewModel: ViewModel() {

    abstract val state: StateFlow<State>
    abstract val events: Flow<Event>

    abstract fun setLabel(label: BuildLabel)
    abstract fun resetLabel()

    sealed class State {
        data object Loading: State()
        data class Loaded(val label: Labels?): State()
    }

    enum class Event {
        LABEL_SET,
        LABEL_RESET
    }

}

class BuildLabelViewModelImpl(
    private val repository: PhenotypeRepository
): BuildLabelViewModel() {

    override val state = repository.state.filterNotNull().map {
        when (it) {
            is PhenotypeRepository.PhenotypeState.Loaded -> State.Loaded(it.labels)
            else -> State.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State.Loading)

    override val events = MutableSharedFlow<Event>()

    override fun setLabel(label: BuildLabel) {
        viewModelScope.launch {
            val labels = Labels.newBuilder()
                .setDeviceTier(label.device)
                .setVariant(label.variant)
                .build()
            repository.setLabels(labels)
            events.emit(Event.LABEL_SET)
        }
    }

    override fun resetLabel() {
        viewModelScope.launch {
            repository.resetLabels()
            events.emit(Event.LABEL_RESET)
        }
    }

}