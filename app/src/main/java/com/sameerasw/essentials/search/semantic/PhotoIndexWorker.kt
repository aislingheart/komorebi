package com.sameerasw.essentials.search.semantic

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

class PhotoIndexWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            PhotoSemanticEngine.indexBatch(applicationContext, maxToIndex = 180)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "semantic_photo_indexer"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<PhotoIndexWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
