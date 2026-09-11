package com.csyncvibe.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            performSync()
        } else {
            binding.txtStatus.text = getString(R.string.permission_required)
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
        }
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
        binding.btnSync.setOnClickListener { checkPermissionAndSync() }

        // Schedule periodic background sync (every 12 hours)
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

    private fun checkPermissionAndSync() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                performSync()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun performSync() {
        val token = securePrefs.githubToken
        val owner = securePrefs.repoOwner
        val repo = securePrefs.repoName
        val path = securePrefs.filePath

        if (token.isEmpty() || owner.isEmpty() || repo.isEmpty()) {
            Toast.makeText(this, "Please save settings first", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSync.isEnabled = false
        binding.txtStatus.text = getString(R.string.status_syncing)

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val contacts = contactRepository.getAllContacts()
                    val github = GitHubSyncService(token)
                    github.syncContacts(owner, repo, path, contacts)
                    contacts.size
                }

                securePrefs.lastSyncTime = System.currentTimeMillis()
                binding.txtStatus.text = getString(R.string.status_success)
                binding.txtContactCount.text = "Synced $result contacts"
                updateLastSyncDisplay()
            } catch (e: Exception) {
                binding.txtStatus.text = getString(R.string.status_error, e.message ?: "Unknown error")
            } finally {
                binding.btnSync.isEnabled = true
            }
        }
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
