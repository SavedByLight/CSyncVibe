package com.csyncvibe.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var githubToken: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var repoOwner: String
        get() = prefs.getString(KEY_OWNER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OWNER, value).apply()

    var repoName: String
        get() = prefs.getString(KEY_REPO, "") ?: ""
        set(value) = prefs.edit().putString(KEY_REPO, value).apply()

    var filePath: String
        get() = prefs.getString(KEY_PATH, "contacts/contacts.json") ?: "contacts/contacts.json"
        set(value) = prefs.edit().putString(KEY_PATH, value).apply()

    var lastSyncTime: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    companion object {
        private const val KEY_TOKEN = "github_token"
        private const val KEY_OWNER = "repo_owner"
        private const val KEY_REPO = "repo_name"
        private const val KEY_PATH = "file_path"
        private const val KEY_LAST_SYNC = "last_sync_time"
    }
}
