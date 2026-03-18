package com.dean.browservault

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SavedSitesActivity : AppCompatActivity() {

    private lateinit var titleView: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var selectionInfo: TextView
    private lateinit var deleteSelectedButton: ImageButton
    private lateinit var adapter: SavedSitesAdapter

    private val selectedUrls = mutableSetOf<String>()

    private val siteType: String by lazy {
        intent.getStringExtra(EXTRA_SITE_TYPE) ?: SavedSiteStore.TYPE_BOOKMARKS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_sites)

        titleView = findViewById(R.id.textTitle)
        recyclerView = findViewById(R.id.savedSitesRecyclerView)
        emptyText = findViewById(R.id.textEmpty)
        selectionInfo = findViewById(R.id.textSelectionInfo)
        deleteSelectedButton = findViewById(R.id.buttonDeleteSelected)

        adapter = SavedSitesAdapter(
            onSiteClicked = ::handleSiteClick,
            onSelectionChanged = ::toggleSelection,
            onDeleteClicked = ::confirmDeleteSingle
        )

        titleView.text = getString(titleRes())
        emptyText.setText(emptyMessageRes())

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<ImageButton>(R.id.buttonBack).setOnClickListener { finish() }
        deleteSelectedButton.setOnClickListener { confirmDeleteSelected() }

        loadItems()
    }

    override fun onResume() {
        super.onResume()
        loadItems()
    }

    private fun loadItems() {
        val urls = SavedSiteStore.list(this, siteType)
        selectedUrls.retainAll(urls.toSet())
        adapter.submitList(urls)
        adapter.setSelections(selectedUrls)
        emptyText.visibility = if (urls.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (urls.isEmpty()) View.GONE else View.VISIBLE
        updateSelectionUi()
    }

    private fun handleSiteClick(url: String) {
        if (selectedUrls.isNotEmpty()) {
            toggleSelection(url)
            return
        }

        setResult(RESULT_OK, Intent().putExtra(EXTRA_SELECTED_URL, url))
        finish()
    }

    private fun toggleSelection(url: String) {
        if (!selectedUrls.add(url)) {
            selectedUrls.remove(url)
        }
        adapter.setSelections(selectedUrls)
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        val inSelectionMode = selectedUrls.isNotEmpty()
        deleteSelectedButton.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
        selectionInfo.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
        if (inSelectionMode) {
            selectionInfo.text = resources.getQuantityString(
                R.plurals.saved_sites_selected_count,
                selectedUrls.size,
                selectedUrls.size
            )
        }
    }

    private fun confirmDeleteSingle(url: String) {
        confirmDelete(listOf(url))
    }

    private fun confirmDeleteSelected() {
        if (selectedUrls.isNotEmpty()) {
            confirmDelete(selectedUrls.toList())
        }
    }

    private fun confirmDelete(urls: List<String>) {
        AlertDialog.Builder(this)
            .setTitle(R.string.action_delete)
            .setMessage(resources.getQuantityString(R.plurals.msg_delete_saved_sites_confirm, urls.size, urls.size))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                SavedSiteStore.removeAll(this, siteType, urls)
                selectedUrls.removeAll(urls.toSet())
                loadItems()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun titleRes() = when (siteType) {
        SavedSiteStore.TYPE_HISTORY -> R.string.title_history
        else -> R.string.title_bookmarks
    }

    private fun emptyMessageRes() = when (siteType) {
        SavedSiteStore.TYPE_HISTORY -> R.string.msg_no_history
        else -> R.string.msg_no_bookmarks
    }

    companion object {
        private const val EXTRA_SITE_TYPE = "extra_site_type"
        const val EXTRA_SELECTED_URL = "extra_selected_url"

        fun createIntent(context: Context, siteType: String) =
            Intent(context, SavedSitesActivity::class.java).putExtra(EXTRA_SITE_TYPE, siteType)
    }
}
