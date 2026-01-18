package com.kieronquinn.app.pcs.ui.screens.baseurl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository.PhenotypeState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

abstract class BaseUrlViewModel: ViewModel() {

    abstract val state: StateFlow<State>

    sealed class State {
        data object Loading: State()
        data class Loaded(val url: String?): State()
    }

}

class BaseUrlViewModelImpl(
    phenotypeRepository: PhenotypeRepository
): BaseUrlViewModel() {

    override val state = phenotypeRepository.state.filterNotNull().map {
        when (it) {
            is PhenotypeState.Loading, is PhenotypeState.Applying -> State.Loading
            is PhenotypeState.Loaded -> State.Loaded(it.repository)
            is PhenotypeState.Unavailable -> State.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State.Loading)

}