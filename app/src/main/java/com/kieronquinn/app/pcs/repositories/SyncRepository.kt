package com.kieronquinn.app.pcs.repositories

import android.util.Log
import com.kieronquinn.app.pcs.model.PcsClient
import com.kieronquinn.app.pcs.repositories.ManifestRepository.ManifestState
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository.PhenotypeState

interface SyncRepository {

    /**
     *  Returns a map of out-of-sync clients to their updated version. If up to date, the map will
     *  be empty. If the manifest refresh fails, null will be returned.
     */
    suspend fun getSyncRequired(): Map<PcsClient, Long>?

    /**
     *  Updates the versions in the local Device Config with the given map
     */
    suspend fun performSync(versions: Map<PcsClient, Long>)

}

class SyncRepositoryImpl(
    private val manifestRepository: ManifestRepository,
    private val phenotypeRepository: PhenotypeRepository
): SyncRepository {

    override suspend fun getSyncRequired(): Map<PcsClient, Long>? {
        if (manifestRepository.refreshAndWait()) {
            Log.e("RefreshWorker", "Manifest refresh failed")
            return null
        }
        val manifestState = manifestRepository.state.value
        val phenotypeState = phenotypeRepository.state.value
        if(manifestState !is ManifestState.Loaded) return emptyMap()
        if(phenotypeState !is PhenotypeState.Loaded) return emptyMap()
        val gitHubVersions = manifestState.versions
        val phenotypeVersions = phenotypeState.versions
        return gitHubVersions.filter { (client, version) ->
            phenotypeVersions[client] != version
        }
    }

    override suspend fun performSync(versions: Map<PcsClient, Long>) {
        phenotypeRepository.setVersions(versions)
    }

}