package com.kieronquinn.app.pcs.repositories

import android.content.Context
import com.google.android.`as`.oss.pd.api.proto.BlobConstraints
import com.google.android.`as`.oss.pd.api.proto.BlobConstraints.ClientGroup
import com.google.android.`as`.oss.pd.manifest.api.proto.GetManifestConfigRequest
import com.google.android.`as`.oss.pd.manifest.api.proto.ManifestConfigConstraints
import com.google.crypto.tink.KeysetHandle
import com.kieronquinn.app.pcs.model.Manifests
import com.kieronquinn.app.pcs.model.PcsClient
import com.kieronquinn.app.pcs.model.phone.PhoneManifest
import com.kieronquinn.app.pcs.repositories.ManifestRepository.ManifestState
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository.PhenotypeState
import com.kieronquinn.app.pcs.service.ManifestService
import com.kieronquinn.app.pcs.utils.extensions.buildId
import com.kieronquinn.app.pcs.utils.extensions.client
import com.kieronquinn.app.pcs.utils.extensions.clientGroup
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
    suspend fun getManifest(
        url: String,
        request: GetManifestConfigRequest,
        clientGroupOverride: ClientGroup? = null
    ): ByteArray?
    suspend fun getPhoneManifest(url: String, clientId: String): ByteArray?
    suspend fun getTtsManifest(url: String, id: String): ByteArray?
    suspend fun getAgentManifest(url: String, id: String, device: String?): ByteArray?

    sealed class ManifestState {
        data object Loading: ManifestState()
        data object NotConfigured: ManifestState()
        data class Loaded(
            val versions: Map<PcsClient, Long>,
            val phoneManifests: Map<PhoneManifestData, String>
        ): ManifestState()
        data object Error: ManifestState()
    }

    data class PhoneManifestData(
        val manifest: PhoneManifest,
        val hash: String?
    ) {
        fun getId() = "${manifest.id}:$hash"
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
        val phoneManifests = manifests.phoneManifestList.mapNotNull {
            if (!it.id.contains(":")) return@mapNotNull null
            val id = it.id.split(":")[0]
            val hash = it.id.split(":")[1]
            val manifest = PhoneManifest.entries.firstOrNull { m -> id == m.id }
                ?: return@mapNotNull null
            ManifestRepository.PhoneManifestData(manifest, hash) to it.name
        }.toMap()
        state.emit(ManifestState.Loaded(clients, phoneManifests))
        return false
    }

    private suspend fun getManifests(url: String): Manifests? {
        return try {
            manifestService.getManifest(url)
                ?.decryptManifest(context, manifestKey)
                ?.let {
                    Manifests.parseFrom(it)
                }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getManifest(url: String, name: String, key: KeysetHandle): ByteArray? {
        return try {
            manifestService.getManifest(url, name)
                ?.decryptManifest(null, key)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun checkRepositoryUrl(url: String): Boolean {
        return try {
            getManifests(url) != null
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getManifest(
        url: String,
        request: GetManifestConfigRequest,
        clientGroupOverride: ClientGroup?
    ): ByteArray? {
        val mainManifest = getManifests(url) ?: return null
        val manifest = mainManifest.manifestList.firstOrNull {
            // Ideal search: results matching either the search or the override
            request.constraints.matches(
                other = it.constraints,
                clientGroupOverride = clientGroupOverride
            )
        } ?: mainManifest.manifestList.firstOrNull {
            // Fallback 1: result with ALL client group (usually available)
            request.constraints.matches(
                other = it.constraints,
                clientGroupFallbackToAll = true
            )
        } ?: mainManifest.manifestList.firstOrNull {
            // Fallback 2: first result
            request.constraints.matches(
                other = it.constraints,
                clientGroupFallbackToFirst = true
            )
        } ?: return null
        return getManifest(url, manifest.name, manifest.encryptionKey.toByteArray().toKeysetHandle())
    }

    override suspend fun getPhoneManifest(url: String, clientId: String): ByteArray? {
        val mainManifest = getManifests(url) ?: return null
        val manifest = mainManifest.phoneManifestList.firstOrNull {
            clientId == it.id
        } ?: return null
        return getManifest(url, manifest.name, manifest.encryptionKey.toByteArray().toKeysetHandle())
    }

    override suspend fun getTtsManifest(url: String, id: String): ByteArray? {
        val mainManifest = getManifests(url) ?: return null
        val manifest = mainManifest.ttsManifestList.firstOrNull {
            id == it.id
        } ?: return null
        return getManifest(url, manifest.name, manifest.encryptionKey.toByteArray().toKeysetHandle())
    }

    override suspend fun getAgentManifest(url: String, id: String, device: String?): ByteArray? {
        val mainManifest = getManifests(url) ?: return null
        val requiredId = if (device != null) {
            "$id:$device"
        } else {
            id
        }
        val manifest = mainManifest.agentManifestList.firstOrNull {
            requiredId == it.id
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

    private fun ManifestConfigConstraints.matches(
        other: BlobConstraints,
        clientGroupFallbackToAll: Boolean = false,
        clientGroupFallbackToFirst: Boolean = false,
        clientGroupOverride: ClientGroup? = null
    ): Boolean {
        if (client?.client != other.client) return false
        if (variant != other.variant) return false
        if (deviceTier != other.deviceTier)  return false
        val clientGroupMatches = clientGroup.matches(
            other.clientGroup,
            clientGroupFallbackToAll,
            clientGroupFallbackToFirst,
            clientGroupOverride
        )
        if (!clientGroupMatches) return false
        // Only check the build ID if it's actually specified
        return buildId == 0L || buildId == other.buildId
    }

    private fun ClientGroup?.matches(
        other: ClientGroup,
        fallbackToAll: Boolean,
        fallbackToFirst: Boolean,
        override: ClientGroup?
    ): Boolean {
        return when {
            // If our group is null, we should also try to fall back to all if available
            (this == null || fallbackToAll) && other == ClientGroup.ALL -> true
            // If fallback to first is set, all other options have been exhausted, take first option
            fallbackToFirst -> true
            // If override is set, try to match it
            override != null -> other == override
            // Otherwise, match the request
            else -> other == this
        }
    }

}