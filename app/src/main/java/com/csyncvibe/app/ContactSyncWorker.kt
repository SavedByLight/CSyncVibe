package com.csyncvibe.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Background worker – currently does nothing automatic.
 * Kept so the scheduled work does not crash. User must explicitly
 * press "Upload / Overwrite" to push contacts.
 */
class ContactSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Safe no-op: never auto-overwrite the GitHub file.
        return Result.success()
    }
}
