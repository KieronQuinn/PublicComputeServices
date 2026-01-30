package com.kieronquinn.app.pcs.repositories

import android.content.Context
import com.google.android.`as`.oss.pd.api.proto.BlobConstraints
import com.google.android.`as`.oss.pd.manifest.api.proto.GetManifestConfigRequest
import com.google.android.`as`.oss.pd.manifest.api.proto.ManifestConfigConstraints
import com.google.crypto.tink.KeysetHandle
import com.kieronquinn.app.pcs.model.Manifests
import com.kieronquinn.app.pcs.model.PcsClient
import com.kieronquinn.app.pcs.repositories.ManifestRepository.ManifestState
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository.PhenotypeState
import com.kieronquinn.app.pcs.service.ManifestService
import com.kieronquinn.app.pcs.utils.extensions.buildId
import com.kieronquinn.app.pcs.utils.extensions.client
import com.kieronquinn.app.pcs.utils.extensions.decryptManifest
import com.kieronquinn.app.pcs.utils.extensions.deviceTier
import com.kieronquinn.app.pcs.utils.extensions.getManifestKey
import com.kieronquinn.app.pcs.utils.extensions.toKeysetHandle
import com.kieronquinn.app.pcs.utils.extensions.variant
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.Retrofit

interface ManifestRepository {

    val state: StateFlow<ManifestState?>

    fun refresh()
    suspend fun refreshAndWait(): Boolean
    suspend fun checkRepositoryUrl(url: String): Boolean
    suspend fun getManifest(url: String, request: GetManifestConfigRequest): ByteArray?
    suspend fun getStaticManifest(url: String, clientId: String): ByteArray?

    sealed class ManifestState {
        data object Loading: ManifestState()
        data object NotConfigured: ManifestState()
        data class Loaded(
            val versions: Map<PcsClient, Long>
        ): ManifestState()
        data object Error: ManifestState()
    }

}

class ManifestRepositoryImpl(
    private val context: Context,
    phenotypeRepository: PhenotypeRepository,
    retrofit: Retrofit
): ManifestRepository {

    private val scope = MainScope()
    private val manifestService = ManifestService.createService(retrofit, context)

    private val repositoryUrl = phenotypeRepository.state.filter {
        it is PhenotypeState.Loaded || it is PhenotypeState.Unavailable
    }.map {
        (it as? PhenotypeState.Loaded)?.repository
    }

    private val manifestKey by lazy {
        context.getManifestKey()
    }

    override val state = MutableStateFlow<ManifestState?>(null)

    override fun refresh() {
        scope.launch {
            refreshAndWait()
        }
    }

    override suspend fun refreshAndWait(): Boolean {
        if(state.value is ManifestState.Loading) return false
        val repositoryUrl = this@ManifestRepositoryImpl.repositoryUrl.first() ?: run {
            state.emit(ManifestState.NotConfigured)
            return false
        }
        state.emit(ManifestState.Loading)
        val manifestKey = context.getManifestKey()
        val manifests = try {
            manifestService.getManifest(repositoryUrl)
                ?.decryptManifest(context, manifestKey)
                ?.let { Manifests.parseFrom(it) }
        } catch (e: Exception) {
            state.emit(ManifestState.Error)
            return true
        }
        if(manifests == null) {
            state.emit(ManifestState.Error)
            return true
        }
        val clients = PcsClient.entries.mapNotNull {
            val version = manifests.manifestList?.firstOrNull { manifest ->
                manifest.constraints.client == it.client
            }?.constraints?.buildId ?: return@mapNotNull null
            it to version
        }.toMap()
        state.emit(ManifestState.Loaded(clients))
        return false
    }

    private suspend fun getManifests(url: String): Manifests? {
        return manifestService.getManifest(url)
            ?.decryptManifest(context, manifestKey)
            ?.let {
                try {
                    Manifests.parseFrom(it)
                } catch (e: Exception) {
                    null
                }
            }
    }

    private suspend fun getManifest(url: String, name: String, key: KeysetHandle): ByteArray? {
        return manifestService.getManifest(url, name)
            ?.decryptManifest(null, key)
    }

    override suspend fun checkRepositoryUrl(url: String): Boolean {
        return try {
            getManifests(url) != null
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getManifest(url: String, request: GetManifestConfigRequest): ByteArray? {
        val mainManifest = getManifests(url) ?: return null
        val manifest = mainManifest.manifestList.firstOrNull {
            request.constraints.matches(it.constraints)
        } ?: return null
        return getManifest(url, manifest.name, manifest.encryptionKey.toByteArray().toKeysetHandle())
    }

    override suspend fun getStaticManifest(url: String, clientId: String): ByteArray? {
        val mainManifest = getManifests(url) ?: return null
        val manifest = mainManifest.staticManifestList.firstOrNull {
            clientId == it.clientId
        } ?: return null
        return getManifest(url, manifest.name, manifest.encryptionKey.toByteArray().toKeysetHandle())
    }

    /**
     *  Automatically refresh when the base URL changes (includes when the app is first opened)
     */
    private fun setupUrlRefresh() = scope.launch {
        repositoryUrl.drop(1).distinctUntilChanged().collect {
            refresh()
        }
    }

    init {
        setupUrlRefresh()
    }

    /**
     *  Checks fields we actually care about
     */
    private fun ManifestConfigConstraints.matches(other: BlobConstraints): Boolean {
        if (client?.client != other.client) return false
        if (variant != other.variant) return false
        if (deviceTier != other.deviceTier)  return false
        // Only check the build ID if it's actually specified
        return buildId == 0L || buildId == other.buildId
    }

}