package com.kairos.app.data.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * Completes a task in the background for the alert's "Complete" action. Runs
 * through WorkManager so it survives process death, waits for a network, and
 * retries on transient failure — more reliable than a bare receiver coroutine.
 * Idempotent: completing an already-complete task is a no-op server-side.
 */
class CompleteTaskWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.success()
        val container = (applicationContext as? com.kairos.app.KairosApp)?.container
            ?: return Result.retry()
        val ok = runCatching {
            container.sessionRepository.completeTaskFromNotification(taskId)
        }.getOrDefault(false)
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        const val KEY_TASK_ID = "taskId"

        fun enqueue(context: Context, taskId: String) {
            val data = Data.Builder().putString(KEY_TASK_ID, taskId).build()
            val req = OneTimeWorkRequestBuilder<CompleteTaskWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}
