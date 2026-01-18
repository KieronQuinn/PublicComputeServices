package com.kieronquinn.app.pcs.repositories

import android.content.Context
import com.kieronquinn.app.pcs.providers.XposedStateProvider.Companion.getXposedEnabled
import com.kieronquinn.app.pcs.repositories.XposedRepository.XposedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface XposedRepository {

    val state: StateFlow<XposedState>

    fun refresh()
    suspend fun refreshAndWait()

    sealed class XposedState {
        data object Loading: XposedState()
        data object Available: XposedState()
        data object Unavailable: XposedState()
    }

}

class XposedRepositoryImpl(private val context: Context): XposedRepository {

    private val scope = MainScope()

    override val state = MutableStateFlow<XposedState>(XposedState.Loading)

    override fun refresh() {
        scope.launch {
            refreshAndWait()
        }
    }

    override suspend fun refreshAndWait() {
        state.emit(XposedState.Loading)
        val enabled = withContext(Dispatchers.IO) {
            getXposedEnabled(context)
        }
        if (enabled) {
            state.emit(XposedState.Available)
        } else {
            state.emit(XposedState.Unavailable)
        }
    }

    init {
        refresh()
    }

}