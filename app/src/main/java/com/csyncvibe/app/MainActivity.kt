package com.csyncvibe.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.csyncvibe.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var securePrefs: SecurePreferences
    private lateinit var contactRepository: ContactRepository

    private enum class PendingAction { DOWNLOAD, UPLOAD }

    private var pendingAction: PendingAction? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            when (pendingAction) {
                PendingAction.DOWNLOAD -> performDownload()
                PendingAction.UPLOAD -> confirmAndUpload()
                null -> {}
            }
        } else {
            binding.txtStatus.text = getString(R.string.permission_required)
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
        }
        pendingAction = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securePrefs = SecurePreferences(this)
        contactRepository = ContactRepository(this)

        loadSavedSettings()
        updateLastSyncDisplay()

        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnDownload.setOnClickListener { checkPermissionsAndRun(PendingAction.DOWNLOAD) }
        binding.btnUpload.setOnClickListener { checkPermissionsAndRun(PendingAction.UPLOAD) }

        // Background worker still does a safe upload only if you want it later
        schedulePeriodicSync()
    }

    private fun loadSavedSettings() {
        binding.editToken.setText(securePrefs.githubToken)
        binding.editOwner.setText(securePrefs.repoOwner)
        binding.editRepo.setText(securePrefs.repoName)
        binding.editFilePath.setText(securePrefs.filePath.ifEmpty { "contacts/contacts.json" })
    }

    private fun saveSettings() {
        val token = binding.editToken.text?.toString()?.trim().orEmpty()
        val owner = binding.editOwner.text?.toString()?.trim().orEmpty()
        val repo = binding.editRepo.text?.toString()?.trim().orEmpty()
        val path = binding.editFilePath.text?.toString()?.trim().orEmpty()

        if (token.isEmpty() || owner.isEmpty() || repo.isEmpty()) {
            Toast.makeText(this, "Please fill in Token, Owner and Repo", Toast.LENGTH_SHORT).show()
            return
        }

        securePrefs.githubToken = token
        securePrefs.repoOwner = owner
        securePrefs.repoName = repo
        securePrefs.filePath = path.ifEmpty { "contacts/contacts.json" }

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissionsAndRun(action: PendingAction) {
        val needed = mutableListOf(Manifest.permission.READ_CONTACTS)
        if (action == PendingAction.DOWNLOAD) {
            needed.add(Manifest.permission.WRITE_CONTACTS)
        }

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            when (action) {
                PendingAction.DOWNLOAD -> performDownload()
                PendingAction.UPLOAD -> confirmAndUpload()
            }
        } else {
            pendingAction = action
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun performDownload() {
        val token = securePrefs.githubToken
        val owner = securePrefs.repoOwner
        val repo = securePrefs.repoName
        val path = securePrefs.filePath

        if (token.isEmpty() || owner.isEmpty() || repo.isEmpty()) {
            Toast.makeText(this, "Please save settings first", Toast.LENGTH_SHORT).show()
            return
        }

        setButtonsEnabled(false)
        binding.txtStatus.text = getString(R.string.status_downloading)

        lifecycleScope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    val github = GitHubSyncService(token)
                    val remoteContacts = github.downloadContacts(owner, repo, path)
                    contactRepository.importContacts(remoteContacts)
                }

                securePrefs.lastSyncTime = System.currentTimeMillis()
                binding.txtStatus.text = getString(R.string.status_success_download, imported)
                binding.txtContactCount.text = if (imported == 0) {
                    "No new contacts to import (already up to date or empty on GitHub)"
                } else {
                    "Successfully imported $imported contacts"
                }
                updateLastSyncDisplay()
            } catch (e: Exception) {
                binding.txtStatus.text = getString(R.string.status_error, e.message ?: "Unknown error")
            } finally {
                setButtonsEnabled(true)
            }
        }
    }

    private fun confirmAndUpload() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_overwrite_title)
            .setMessage(R.string.confirm_overwrite_message)
            .setPositiveButton("Overwrite") { _, _ -> performUpload() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performUpload() {
        val token = securePrefs.githubToken
        val owner = securePrefs.repoOwner
        val repo = securePrefs.repoName
        val path = securePrefs.filePath

        if (token.isEmpty() || owner.isEmpty() || repo.isEmpty()) {
            Toast.makeText(this, "Please save settings first", Toast.LENGTH_SHORT).show()
            return
        }

        setButtonsEnabled(false)
        binding.txtStatus.text = getString(R.string.status_uploading)

        lifecycleScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val contacts = contactRepository.getAllContacts()
                    val github = GitHubSyncService(token)
                    github.uploadContacts(owner, repo, path, contacts)
                    contacts.size
                }

                securePrefs.lastSyncTime = System.currentTimeMillis()
                binding.txtStatus.text = getString(R.string.status_success_upload, count)
                binding.txtContactCount.text = "Uploaded $count contacts to GitHub"
                updateLastSyncDisplay()
            } catch (e: Exception) {
                binding.txtStatus.text = getString(R.string.status_error, e.message ?: "Unknown error")
            } finally {
                setButtonsEnabled(true)
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnDownload.isEnabled = enabled
        binding.btnUpload.isEnabled = enabled
        binding.btnSave.isEnabled = enabled
    }

    private fun updateLastSyncDisplay() {
        val ts = securePrefs.lastSyncTime
        if (ts > 0) {
            val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(ts))
            binding.txtLastSync.text = getString(R.string.last_sync, formatted)
        }
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<ContactSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "csyncvibe_periodic_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
