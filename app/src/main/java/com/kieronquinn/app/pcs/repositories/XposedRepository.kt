package com.kieronquinn.app.pcs.repositories

import android.content.Context
import com.kieronquinn.app.pcs.providers.XposedStateProvider.Companion.getXposedEnabled
import com.kieronquinn.app.pcs.repositories.XposedRepository.XposedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

    companion object {
        private const val XPOSED_TIMEOUT_MS = 5_000L
    }

    private val scope = MainScope()
    private val refreshLock = Mutex()

    override val state = MutableStateFlow<XposedState>(XposedState.Loading)

    override fun refresh() {
        scope.launch {
            refreshAndWait()
        }
    }

    override suspend fun refreshAndWait() = refreshLock.withLock {
        state.emit(XposedState.Loading)
        val enabled = try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(XPOSED_TIMEOUT_MS) {
                    getXposedEnabled(context)
                } ?: false
            }
        } catch (e: Throwable) {
            false
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