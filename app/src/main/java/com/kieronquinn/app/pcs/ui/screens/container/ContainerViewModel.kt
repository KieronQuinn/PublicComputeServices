package com.kieronquinn.app.pcs.ui.screens.container

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.pcs.repositories.NavigationRepository
import com.kieronquinn.app.pcs.repositories.NavigationRepository.Destination
import com.kieronquinn.app.pcs.repositories.NavigationRepository.Destination.Error.Type
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository.PhenotypeState
import com.kieronquinn.app.pcs.repositories.XposedRepository
import com.kieronquinn.app.pcs.repositories.XposedRepository.XposedState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

abstract class ContainerViewModel: ViewModel() {

    abstract val backStack: SnapshotStateList<Destination>

    abstract fun onResume()

}

class ContainerViewModelImpl(
    private val navigationRepository: NavigationRepository,
    private val xposedRepository: XposedRepository,
    private val phenotypeRepository: PhenotypeRepository
): ContainerViewModel() {

    private val route = combine(
        xposedRepository.state,
        phenotypeRepository.state
    ) { xposedState, phenotype ->
        when {
            xposedState is XposedState.Unavailable -> Destination.Error(Type.NO_XPOSED)
            phenotype is PhenotypeState.Unavailable -> Destination.Error(Type.NO_ROOT)
            xposedState is XposedState.Loading || phenotype !is PhenotypeState.Loaded -> null
            phenotype.repository == null -> Destination.EnterBaseURL
            phenotype.labels == null -> Destination.SelectBuildLabel
            else -> Destination.Settings
        }
    }.filterNotNull().distinctUntilChanged()

    override val backStack = mutableStateListOf<Destination>(Destination.Loading)

    override fun onResume() {
        xposedRepository.refresh()
        phenotypeRepository.refresh()
    }

    private fun setupRoute() = viewModelScope.launch {
        route.collect {
            navigationRepository.navigateTo(it, true)
        }
    }

    init {
        setupRoute()
    }

}