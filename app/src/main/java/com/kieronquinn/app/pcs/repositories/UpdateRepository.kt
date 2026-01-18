package com.kieronquinn.app.pcs.repositories

import android.content.Context
import com.kieronquinn.app.pcs.BuildConfig
import com.kieronquinn.app.pcs.model.Release
import com.kieronquinn.app.pcs.repositories.UpdateRepository.Companion.CONTENT_TYPE_APK
import com.kieronquinn.app.pcs.service.GitHubService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit

interface UpdateRepository {

    companion object {
        const val CONTENT_TYPE_APK = "application/vnd.android.package-archive"
    }

    suspend fun getUpdate(currentTag: String = BuildConfig.TAG_NAME): Release?

}

class UpdateRepositoryImpl(context: Context, retrofit: Retrofit): UpdateRepository {

    private val gitHubService = GitHubService.createService(retrofit, context)

    override suspend fun getUpdate(currentTag: String): Release? = withContext(Dispatchers.IO) {
        val releasesResponse = try {
            gitHubService.getReleases().execute()
        }catch (e: Exception) {
            return@withContext null
        }
        if(!releasesResponse.isSuccessful) return@withContext null
        val newestRelease = releasesResponse.body()?.firstOrNull() ?: return@withContext null
        if(newestRelease.tag == null || newestRelease.tag == currentTag) return@withContext null
        // Found a new release
        val versionName = newestRelease.versionName ?: return@withContext null
        val asset = newestRelease.assets?.firstOrNull { it.contentType == CONTENT_TYPE_APK }
            ?: return@withContext null
        val downloadUrl = asset.downloadUrl ?: return@withContext null
        val fileName = asset.fileName ?: return@withContext null
        val gitHubUrl = newestRelease.gitHubUrl ?: return@withContext null
        val body = newestRelease.body ?: return@withContext null
        return@withContext Release(
            newestRelease.tag, versionName, downloadUrl, fileName, gitHubUrl, body
        )
    }

}