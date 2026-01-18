package com.kieronquinn.app.pcs.ui.screens.error

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository
import com.kieronquinn.app.pcs.repositories.XposedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

abstract class ErrorScreenViewModel: ViewModel() {

    abstract val isLoading: StateFlow<Boolean>

    abstract fun onRetryClicked()

}

class ErrorScreenViewModelImpl(
    private val phenotypeRepository: PhenotypeRepository,
    private val xposedRepository: XposedRepository
): ErrorScreenViewModel() {

    override val isLoading = MutableStateFlow(false)

    override fun onRetryClicked() {
        viewModelScope.launch {
            if (isLoading.value) return@launch
            isLoading.emit(true)
            phenotypeRepository.refreshAndWait()
            xposedRepository.refreshAndWait()
            isLoading.emit(false)
        }
    }

}