package com.kieronquinn.app.pcs.service

import android.content.Context
import com.kieronquinn.app.pcs.model.GitHubRelease
import com.kieronquinn.app.pcs.service.ManifestService.Companion.withCache
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET

interface GitHubService {

    companion object {
        private const val BASE_URL = "https://api.github.com/repos/KieronQuinn/PublicComputeServices/"

        fun createService(retrofit: Retrofit, context: Context): GitHubService {
            return retrofit.newBuilder()
                .baseUrl(BASE_URL)
                .withCache(context)
                .build()
                .create(GitHubService::class.java)
        }
    }

    @GET("releases")
    fun getReleases(): Call<Array<GitHubRelease>>

}