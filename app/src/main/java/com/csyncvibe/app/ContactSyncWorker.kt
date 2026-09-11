package com.csyncvibe.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ContactSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val securePrefs = SecurePreferences(applicationContext)
        val token = securePrefs.githubToken
        val owner = securePrefs.repoOwner
        val repo = securePrefs.repoName
        val path = securePrefs.filePath

        if (token.isEmpty() || owner.isEmpty() || repo.isEmpty()) {
            return Result.failure()
        }

        return try {
            val contactRepository = ContactRepository(applicationContext)
            val contacts = contactRepository.getAllContacts()
            val github = GitHubSyncService(token)
            github.syncContacts(owner, repo, path, contacts)
            securePrefs.lastSyncTime = System.currentTimeMillis()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
