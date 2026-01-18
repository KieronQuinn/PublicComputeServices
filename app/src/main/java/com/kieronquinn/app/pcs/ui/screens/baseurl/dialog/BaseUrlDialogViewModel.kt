package com.kieronquinn.app.pcs.ui.screens.baseurl.dialog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.pcs.repositories.ManifestRepository
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository
import kotlinx.coroutines.launch

abstract class BaseUrlDialogViewModel: ViewModel() {

    abstract suspend fun checkManifestUrl(url: String): Boolean
    abstract fun setUrl(url: String)

}

class BaseUrlDialogViewModelImpl(
    private val manifestRepository: ManifestRepository,
    private val phenotypeRepository: PhenotypeRepository
): BaseUrlDialogViewModel() {

    override suspend fun checkManifestUrl(url: String) = manifestRepository.checkRepositoryUrl(url)

    override fun setUrl(url: String) {
        viewModelScope.launch {
            phenotypeRepository.setRepository(url)
        }
    }

}