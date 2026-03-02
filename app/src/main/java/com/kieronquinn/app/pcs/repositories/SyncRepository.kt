package com.kieronquinn.app.pcs.repositories

import android.content.Context
import android.util.Base64
import com.kieronquinn.app.pcs.model.PcsClient
import com.kieronquinn.app.pcs.model.phone.PhoneManifest
import com.kieronquinn.app.pcs.providers.ConfigProvider
import com.kieronquinn.app.pcs.repositories.ManifestRepository.ManifestState
import com.kieronquinn.app.pcs.repositories.ManifestRepository.PhoneManifestData
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository.PhenotypeState
import com.kieronquinn.app.pcs.repositories.SyncRepository.SyncRequirements
import com.kieronquinn.app.pcs.utils.extensions.sha256AsHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SyncRepository {

    /**
     *  Returns a map of out-of-sync clients to their updated version. If up to date, the map will
     *  be empty. If the manifest refresh fails, null will be returned.
     */
    suspend fun getSyncRequired(): SyncRequirements?

    /**
     *  Updates the versions in the local Device Config with the given map
     */
    suspend fun performSync(requirements: SyncRequirements, waitForRefresh: Boolean)

    data class SyncRequirements(
        val phenotype: Map<PcsClient, Long> = emptyMap(),
        val phoneManifests: Map<PhoneManifestData, String> = emptyMap()
    )

}

class SyncRepositoryImpl(
    private val manifestRepository: ManifestRepository,
    private val phenotypeRepository: PhenotypeRepository,
    private val settingsRepository: SettingsRepository,
    private val context: Context
): SyncRepository {

    override suspend fun getSyncRequired(): SyncRequirements? {
        if (manifestRepository.refreshAndWait()) {
            return null
        }
        val manifestState = manifestRepository.state.value
        val phenotypeState = phenotypeRepository.state.value
        if(manifestState !is ManifestState.Loaded) return SyncRequirements()
        if(phenotypeState !is PhenotypeState.Loaded) return SyncRequirements()
        val gitHubVersions = manifestState.versions
        val phenotypeVersions = phenotypeState.versions
        val phenotype = gitHubVersions.filter { (client, version) ->
            phenotypeVersions[client] != version
        }
        val phoneManifests = getLocalPhoneManifests().mapNotNull { local ->
            val remote = manifestState.phoneManifests.entries.firstOrNull { m ->
                m.key.manifest == local.manifest
            } ?: return@mapNotNull null
            remote.takeUnless { remote.key.hash == local.hash }?.toPair()
        }.toMap()
        return SyncRequirements(phenotype, phoneManifests)
    }

    override suspend fun performSync(requirements: SyncRequirements, waitForRefresh: Boolean) {
        if (requirements.phenotype.isNotEmpty()) {
            phenotypeRepository.setVersions(requirements.phenotype, waitForRefresh)
        }
        if (requirements.phoneManifests.isEmpty()) return
        val url = withContext(Dispatchers.IO) {
            ConfigProvider.getRepositoryUrl(context)
        } ?: return
        requirements.phoneManifests.forEach {
            val manifest = manifestRepository.getPhoneManifest(url, it.key.getId()).let { m ->
                Base64.encodeToString(m, Base64.DEFAULT)
            } ?: return@forEach
            it.key.manifest.setting(settingsRepository).set(manifest)
        }
    }

    private suspend fun getLocalPhoneManifests(): List<PhoneManifestData> {
        return PhoneManifest.entries.map {
            val rawSetting = it.setting(settingsRepository).get().takeIf { s -> s.isNotBlank() }
            val data = rawSetting?.let { s -> Base64.decode(s, Base64.DEFAULT) }
            PhoneManifestData(it, data?.sha256AsHex())
        }
    }

}