package com.dean.browservault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.io.File

class VaultActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: VaultAdapter

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            importToVault(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vault)

        recyclerView = findViewById(R.id.vaultRecyclerView)
        adapter = VaultAdapter(
            onFileClick = ::openVaultFile,
            onFileLongClick = ::confirmDelete
        )

        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = adapter

        findViewById<MaterialButton>(R.id.buttonAddFile).setOnClickListener {
            pickFileLauncher.launch(arrayOf("*/*"))
        }

        loadVaultFiles()
    }

    override fun onResume() {
        super.onResume()
        loadVaultFiles()
    }

    private fun importToVault(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Permission may be temporary-only on some providers.
        }

        runCatching {
            FileUtils.copyUriToVault(this, uri)
        }.onSuccess {
            toast(getString(R.string.msg_file_added_to_vault))
            loadVaultFiles()
        }.onFailure {
            toast(getString(R.string.msg_file_add_failed))
        }
    }

    private fun loadVaultFiles() {
        val files = FileUtils.listVaultFiles(this)
        adapter.submitList(files)
    }

    private fun openVaultFile(file: File) {
        if (FileUtils.isVideoFile(file)) {
            val uri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, file)
            startActivity(
                Intent(this, VideoPlayerActivity::class.java)
                    .putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, uri.toString())
            )
            return
        }

        if (FileUtils.isImageFile(file)) {
            val uri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
            return
        }

        val uri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, FileUtils.guessMimeType(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(this)
            .setTitle(R.string.title_delete_file)
            .setMessage(getString(R.string.msg_delete_file_confirm, file.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                if (file.delete()) {
                    toast(getString(R.string.msg_file_deleted))
                    loadVaultFiles()
                } else {
                    toast(getString(R.string.msg_file_delete_failed))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val FILE_PROVIDER_AUTHORITY = "com.dean.browservault.fileprovider"
    }
}
