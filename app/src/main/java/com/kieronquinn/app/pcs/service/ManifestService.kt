package com.kieronquinn.app.pcs.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url

interface ManifestService {

    companion object {
        fun createService(retrofit: Retrofit, context: Context): ManifestService {
            return retrofit.newBuilder()
                .withCache(context)
                .build()
                .create(ManifestService::class.java)
        }

        private const val MANIFEST_PATH = "/manifest"
        private const val MANIFESTS_PATH = "/manifests/"
        private const val CACHE_SIZE = 100 * 1024 * 1024 // 100 MB

        fun Retrofit.Builder.withCache(context: Context): Retrofit.Builder {
            val cache = Cache(context.cacheDir, CACHE_SIZE.toLong())
            return client(OkHttpClient.Builder().cache(cache).build())
        }
    }

    @GET
    fun get(@Url url: String): Call<ResponseBody>

    suspend fun getManifest(
        url: String,
        name: String? = null
    ): ByteArray? = withContext(Dispatchers.IO) {
        val url = if(name != null) {
            "$url$MANIFESTS_PATH$name"
        } else {
            "$url$MANIFEST_PATH"
        }
        get(url).execute().body()?.bytes()
    }

}