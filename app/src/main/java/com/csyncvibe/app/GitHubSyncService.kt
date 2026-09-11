package com.csyncvibe.app

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
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

    /**
     * Download contacts JSON from the repo. Returns empty list if file does not exist.
     */
    fun downloadContacts(owner: String, repo: String, path: String): List<Contact> {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/contents/$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) {
                return emptyList()
            }
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No details"
                throw Exception("GitHub API ${response.code}: $errorBody")
            }

            val body = response.body?.string() ?: throw Exception("Empty response from GitHub")
            val fileInfo = gson.fromJson(body, GitHubFileResponse::class.java)
            val encoded = fileInfo.content?.replace("\n", "") ?: return emptyList()
            val decoded = String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)

            val payload = gson.fromJson(decoded, ContactsPayload::class.java)
            return payload.contacts ?: emptyList()
        }
    }

    /**
     * Upload (overwrite) the contacts file on GitHub.
     */
    fun uploadContacts(
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
        val existingSha = getFileSha(owner, repo, path)

        val bodyMap = mutableMapOf<String, Any>(
            "message" to "CSyncVibe: upload ${contacts.size} contacts",
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
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private data class GitHubFileResponse(
        @SerializedName("sha") val sha: String?,
        @SerializedName("content") val content: String?
    )

    private data class ContactsPayload(
        @SerializedName("contacts") val contacts: List<Contact>?
    )
}
