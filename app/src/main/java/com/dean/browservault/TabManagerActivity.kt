package com.dean.browservault

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TabManagerActivity : AppCompatActivity() {

    private lateinit var tabCountText: TextView
    private lateinit var tabsRecyclerView: RecyclerView
    private lateinit var tabAdapter: TabManagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tab_manager)

        tabCountText = findViewById(R.id.textTabCount)
        tabsRecyclerView = findViewById(R.id.tabsRecyclerView)
        tabAdapter = TabManagerAdapter(
            onTabClick = { url ->
                startActivity(
                    Intent(this, BrowserTabActivity::class.java)
                        .putExtra(BrowserTabActivity.EXTRA_URL, url)
                )
            },
            onCloseTab = { url ->
                removeTab(url)
                loadTabs()
            },
            onNewTabClick = {
                startActivity(Intent(this, MainActivity::class.java))
            }
        )

        tabsRecyclerView.layoutManager = GridLayoutManager(this, 2)
        tabsRecyclerView.adapter = tabAdapter

        findViewById<ImageButton>(R.id.buttonTabSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        loadTabs()
    }

    override fun onResume() {
        super.onResume()
        loadTabs()
    }

    private fun loadTabs() {
        val urls = TabSessionStore.list(this)

        tabAdapter.submitTabs(urls)
        tabCountText.text = resources.getQuantityString(R.plurals.tab_manager_tabs_open, urls.size, urls.size)
    }

    private fun removeTab(url: String) {
        TabSessionStore.remove(this, url)
    }

}
