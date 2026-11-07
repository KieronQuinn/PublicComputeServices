package com.kieronquinn.app.pcs.translation

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 *  Downloads the English to phone-language translation model in the background, when there is a
 *  network. Queued when a feature that translates is turned on, and again by
 *  [TranslationProvider] if a caller finds the model missing.
 */
class TranslationModelWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    companion object {
        private const val TAG = "TranslationModel"
        private const val WORK_NAME = "translation_model"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<TranslationModelWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val target = PhoneLanguageTranslator.targetLanguage() ?: return@withContext Result.success()
        try {
            PhoneLanguageTranslator.downloadModel(target)
            Log.i(TAG, "Translation model for $target ready")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Translation model for $target not downloaded", e)
            Result.retry()
        }
    }

}
