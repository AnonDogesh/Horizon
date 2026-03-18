package com.dean.browservault

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class DownloadsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var selectionInfo: TextView
    private lateinit var moveSelected: ImageButton
    private lateinit var deleteSelected: ImageButton
    private lateinit var adapter: DownloadsAdapter

    private val selectedPaths = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_downloads)

        recyclerView = findViewById(R.id.downloadsRecyclerView)
        emptyText = findViewById(R.id.textEmpty)
        selectionInfo = findViewById(R.id.textSelectionInfo)
        moveSelected = findViewById(R.id.buttonMoveSelected)
        deleteSelected = findViewById(R.id.buttonDeleteSelected)

        adapter = DownloadsAdapter(
            onFileClicked = ::handleFileClick,
            onFileLongClicked = ::toggleSelection,
            onMoreClicked = ::showRowMenu
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<ImageButton>(R.id.buttonBack).setOnClickListener { finish() }
        moveSelected.setOnClickListener { confirmMoveSelected() }
        deleteSelected.setOnClickListener { confirmDeleteSelected() }

        loadFiles()
    }

    override fun onResume() {
        super.onResume()
        loadFiles()
    }

    private fun downloadsDirectory(): File {
        return FileUtils.ensureDownloadsDirectory(this)
    }

    private fun loadFiles() {
        val files = downloadsDirectory().listFiles()
            .orEmpty()
            .filter { it.isFile }
            .sortedByDescending { it.lastModified() }

        selectedPaths.retainAll(files.map { it.absolutePath }.toSet())
        adapter.submitList(files)
        adapter.setSelections(selectedPaths)
        emptyText.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        updateSelectionUi()
    }

    private fun handleFileClick(file: File) {
        if (selectedPaths.isNotEmpty()) {
            toggleSelection(file)
            return
        }
        openDownloadedFile(file)
    }

    private fun openDownloadedFile(file: File) {
        val uri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, file)
        when {
            FileUtils.isVideoFile(file) -> {
                startActivity(
                    Intent(this, VideoPlayerActivity::class.java)
                        .putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, uri.toString())
                )
            }

            FileUtils.isImageFile(file) -> {
                startActivity(
                    Intent(this, ImageViewerActivity::class.java)
                        .putExtra(ImageViewerActivity.EXTRA_IMAGE_URI, uri.toString())
                )
            }

            else -> showRowMenu(recyclerView, file)
        }
    }

    private fun toggleSelection(file: File) {
        if (!selectedPaths.add(file.absolutePath)) {
            selectedPaths.remove(file.absolutePath)
        }
        adapter.setSelections(selectedPaths)
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        val inSelectionMode = selectedPaths.isNotEmpty()
        moveSelected.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
        deleteSelected.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
        selectionInfo.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
        if (inSelectionMode) {
            selectionInfo.text = resources.getQuantityString(
                R.plurals.downloads_selected_count,
                selectedPaths.size,
                selectedPaths.size
            )
        }
    }

    private fun showRowMenu(anchor: View, file: File) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, MENU_MOVE, 0, getString(R.string.action_move_to_vault))
        popup.menu.add(0, MENU_DELETE, 1, getString(R.string.action_delete))
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                MENU_MOVE -> {
                    confirmMove(listOf(file))
                    true
                }

                MENU_DELETE -> {
                    confirmDelete(listOf(file))
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun confirmMoveSelected() {
        val files = selectedPaths.map(::File)
        if (files.isNotEmpty()) confirmMove(files)
    }

    private fun confirmDeleteSelected() {
        val files = selectedPaths.map(::File)
        if (files.isNotEmpty()) confirmDelete(files)
    }

    private fun confirmMove(files: List<File>) {
        AlertDialog.Builder(this)
            .setTitle(R.string.action_move_to_vault)
            .setMessage(resources.getQuantityString(R.plurals.msg_move_files_confirm, files.size, files.size))
            .setPositiveButton(R.string.action_move_to_vault) { _, _ ->
                var moved = 0
                files.forEach { file ->
                    if (moveFileToVault(file)) moved++
                }
                toast(resources.getQuantityString(R.plurals.msg_files_moved, moved, moved))
                selectedPaths.clear()
                loadFiles()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(files: List<File>) {
        AlertDialog.Builder(this)
            .setTitle(R.string.action_delete)
            .setMessage(resources.getQuantityString(R.plurals.msg_delete_files_confirm, files.size, files.size))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                files.forEach { it.delete() }
                selectedPaths.clear()
                loadFiles()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun moveFileToVault(file: File): Boolean {
        return runCatching {
            val vaultDir = FileUtils.ensureVaultDirectory(this)
            val target = File(vaultDir, file.name)
            val destination = if (target.exists()) File(vaultDir, "${System.currentTimeMillis()}_${file.name}") else target

            FileInputStream(file).use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
            file.delete()
        }.isSuccess
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val FILE_PROVIDER_AUTHORITY = "com.dean.browservault.fileprovider"
        private const val MENU_MOVE = 1
        private const val MENU_DELETE = 2
    }
}
