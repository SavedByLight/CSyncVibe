package com.csyncvibe.app

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

class GitHubSyncService(private val token: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun syncContacts(
        owner: String,
        repo: String,
        path: String,
        contacts: List<Contact>
    ) {
        val content = gson.toJson(
            mapOf(
                "synced_at" to java.time.Instant.now().toString(),
                "contact_count" to contacts.size,
                "contacts" to contacts
            )
        )

        val encodedContent = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))

        // Try to get existing file SHA (needed for update)
        val existingSha = getFileSha(owner, repo, path)

        val bodyMap = mutableMapOf<String, Any>(
            "message" to "CSyncVibe: sync ${contacts.size} contacts",
            "content" to encodedContent
        )
        if (existingSha != null) {
            bodyMap["sha"] = existingSha
        }

        val requestBody = gson.toJson(bodyMap).toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/contents/$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .put(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No details"
                throw Exception("GitHub API ${response.code}: $errorBody")
            }
        }
    }

    private fun getFileSha(owner: String, repo: String, path: String): String? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/contents/$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val fileInfo = gson.fromJson(body, GitHubFileResponse::class.java)
                    fileInfo.sha
                } else {
                    null // File does not exist yet
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private data class GitHubFileResponse(
        @SerializedName("sha") val sha: String?
    )
}
