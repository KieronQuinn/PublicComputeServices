package com.kieronquinn.app.pcs.repositories

import android.util.Base64
import com.kieronquinn.app.pcs.model.PcsClient
import com.kieronquinn.app.pcs.model.PcsClient.BuildId.Namespace
import com.kieronquinn.app.pcs.model.proto.Labels
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository.PhenotypeState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

interface PhenotypeRepository {

    val state: StateFlow<PhenotypeState?>
    val onVersionsReset: Flow<Unit>

    fun refresh()
    suspend fun refreshAndWait()
    suspend fun setVersions(versions: Map<PcsClient, Long>, waitForRefresh: Boolean)
    suspend fun resetVersions()
    suspend fun setLabels(labels: Labels)
    suspend fun resetLabels()
    suspend fun setRepository(url: String)

    sealed class PhenotypeState {
        data class Loading(val previous: PhenotypeState? = null): PhenotypeState()
        data object Unavailable: PhenotypeState()
        data object Applying: PhenotypeState()
        data class Loaded(
            val labels: Labels?,
            val repository: String?,
            val versions: Map<PcsClient, Long>
        ): PhenotypeState()

        fun unwrap() = when(this) {
            is Loading -> previous
            else -> this
        }
    }

}

object PhenotypeRepositoryStub: PhenotypeRepository {

    override val state = MutableStateFlow<PhenotypeState?>(PhenotypeState.Unavailable)
    override val onVersionsReset = emptyFlow<Unit>()

    override fun refresh() {
        // No-op
    }

    override suspend fun refreshAndWait() {
        // No-op
    }

    override suspend fun setVersions(versions: Map<PcsClient, Long>, wait: Boolean) {
        // No-op
    }

    override suspend fun resetVersions() {
        // No-op
    }

    override suspend fun setRepository(url: String) {
        // No-op
    }

    override suspend fun setLabels(labels: Labels) {
        // No-op
    }

    override suspend fun resetLabels() {
        // No-op
    }

}

class PhenotypeRepositoryImpl(
    private val deviceConfigPropertiesRepository: DeviceConfigPropertiesRepository
): PhenotypeRepository {

    companion object {
        private const val FLAG_LABELS = "AicDataRelease__build_labels"
        const val FLAG_REPOSITORY = "PcsManifest__repository"
    }

    private val scope = MainScope()

    override val state = MutableStateFlow<PhenotypeState?>(null)
    override val onVersionsReset = MutableSharedFlow<Unit>()

    override fun refresh() {
        scope.launch {
            refreshAndWait()
        }
    }

    override suspend fun refreshAndWait() {
        if(state.value is PhenotypeState.Loading) return
        val previous = state.value
        state.emit(PhenotypeState.Loading(previous))
        if(!deviceConfigPropertiesRepository.isAvailable()) {
            state.emit(PhenotypeState.Unavailable)
            return
        }
        val namespaces = Namespace.entries.associateWith {
            deviceConfigPropertiesRepository.getConfig(it.value)
        }
        val labels = namespaces[Namespace.AICORE]?.firstOrNull {
            it.flag == FLAG_LABELS
        }?.takeIf { it.value?.isNotBlank() == true }?.getBytes()?.parseLabels()
        val repository = namespaces[Namespace.DEVICE_PERSONALIZATION_SERVICES]?.firstOrNull {
            it.flag == FLAG_REPOSITORY
        }?.value
        val flags = PcsClient.entries.associateWith {
            namespaces[it.buildId.namespace]?.firstOrNull { flag ->
                flag.flag == it.buildId.flag
            }?.getLong() ?: 0L
        }
        state.emit(PhenotypeState.Loaded(labels, repository, flags))
    }

    override suspend fun setVersions(versions: Map<PcsClient, Long>, waitForRefresh: Boolean) {
        if (state.value is PhenotypeState.Applying) return
        state.emit(PhenotypeState.Applying)
        val entries = versions.map { (client, version) ->
            DeviceConfigPropertiesRepository.DeviceConfigEntry(
                namespace = client.buildId.namespace.value,
                flag = client.buildId.flag,
                value = version.toString()
            )
        }
        deviceConfigPropertiesRepository.overrideConfig(entries)
        if (waitForRefresh) {
            refreshAndWait()
        } else {
            refresh()
        }
    }

    override suspend fun resetVersions() {
        val entries = PcsClient.entries.map {
            DeviceConfigPropertiesRepository.DeviceConfigEntry(
                namespace = it.buildId.namespace.value,
                flag = it.buildId.flag,
                value = ""
            )
        }
        deviceConfigPropertiesRepository.clearConfigOverrides(entries)
        refreshAndWait()
        onVersionsReset.emit(Unit)
    }

    override suspend fun setLabels(labels: Labels) {
        val entries = listOf(
            DeviceConfigPropertiesRepository.DeviceConfigEntry(
                namespace = Namespace.AICORE.value,
                flag = FLAG_LABELS,
                value = Base64.encodeToString(labels.toByteArray(), Base64.NO_WRAP)
            )
        )
        deviceConfigPropertiesRepository.overrideConfig(entries)
        refresh()
    }

    override suspend fun resetLabels() {
        val entries = listOf(
            DeviceConfigPropertiesRepository.DeviceConfigEntry(
                namespace = Namespace.AICORE.value,
                flag = FLAG_LABELS,
                value = ""
            )
        )
        deviceConfigPropertiesRepository.clearConfigOverrides(entries)
        refreshAndWait()
    }

    override suspend fun setRepository(url: String) {
        val entry = DeviceConfigPropertiesRepository.DeviceConfigEntry(
            namespace = Namespace.DEVICE_PERSONALIZATION_SERVICES.value,
            flag = FLAG_REPOSITORY,
            value = url
        )
        deviceConfigPropertiesRepository.overrideConfig(listOf(entry))
        refresh()
    }

    /**
     *  Flag could have been overridden to be garbage, so if that happens just return null
     */
    private fun ByteArray.parseLabels(): Labels? {
        return try {
            Labels.parseFrom(this)
        }catch (e: Exception) {
            null
        }
    }

    init {
        scope.launch {
            refresh()
        }
    }

}