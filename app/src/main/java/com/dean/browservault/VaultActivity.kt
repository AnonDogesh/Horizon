package com.dean.browservault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import java.io.File

class VaultActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var adapter: VaultAdapter
    private var selectedCategory: VaultCategory = VaultCategory.IMAGES

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            importToVault(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vault)

        recyclerView = findViewById(R.id.vaultRecyclerView)
        emptyState = findViewById(R.id.textEmptyState)
        adapter = VaultAdapter(
            onFileClick = ::openVaultFile,
            onFileLongClick = ::confirmDelete
        )

        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter

        setupHeaderActions()
        setupTabs()

        findViewById<FloatingActionButton>(R.id.buttonAddFile).setOnClickListener {
            pickFileLauncher.launch(arrayOf("image/*", "video/*"))
        }

        loadVaultFiles()
    }

    override fun onResume() {
        super.onResume()
        loadVaultFiles()
    }

    private fun setupHeaderActions() {
        findViewById<ImageButton>(R.id.buttonBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.buttonSearch).setOnClickListener {
            toast(getString(R.string.msg_vault_search_coming_soon))
        }
    }

    private fun setupTabs() {
        val tabLayout = findViewById<TabLayout>(R.id.vaultTabLayout)
        tabLayout.addTab(tabLayout.newTab().setText(R.string.vault_tab_images), true)
        tabLayout.addTab(tabLayout.newTab().setText(R.string.vault_tab_videos))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectedCategory = when (tab.position) {
                    1 -> VaultCategory.VIDEOS
                    else -> VaultCategory.IMAGES
                }
                loadVaultFiles()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
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
        val filtered = FileUtils.listVaultFiles(this)
            .sortedByDescending { it.lastModified() }
            .filter { file ->
                when (selectedCategory) {
                    VaultCategory.IMAGES -> FileUtils.isImageFile(file)
                    VaultCategory.VIDEOS -> FileUtils.isVideoFile(file)
                }
            }

        adapter.submitList(filtered)
        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
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

enum class VaultCategory {
    IMAGES,
    VIDEOS
}
