package com.dean.browservault

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.util.Xml
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import android.view.View
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import org.xmlpull.v1.XmlPullParser
import java.net.URL
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var inputSearch: EditText
    private lateinit var searchEngineSpinner: Spinner
    private lateinit var feedPrefs: SharedPreferences
    private val feedCards = mutableListOf<MaterialCardView>()
    private val feedTitles = mutableListOf<TextView>()
    @Volatile
    private var topNews: List<NewsItem> = emptyList()

    private val savedSitesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val url = result.data?.getStringExtra(SavedSitesActivity.EXTRA_SELECTED_URL) ?: return@registerForActivityResult
        openUrl(url)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputSearch = findViewById(R.id.inputSearch)
        searchEngineSpinner = findViewById(R.id.spinnerSearchEngineHome)
        feedPrefs = getSharedPreferences(FEED_PREFS, MODE_PRIVATE)

        setupSearchEngineSpinner()
        setupTopActions()
        setupShortcutActions()
        setupFeedActions()
        setupBottomActions()
        loadRealNewsFeed()
    }

    private fun setupSearchEngineSpinner() {
        val engineNames = SearchEngineManager.engines.values.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, engineNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        searchEngineSpinner.adapter = adapter

        val selectedEngine = SearchEngineManager.selectedEngine(this)
        val selectedIndex = SearchEngineManager.engines.keys.indexOf(selectedEngine).coerceAtLeast(0)
        searchEngineSpinner.setSelection(selectedIndex, false)

        searchEngineSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val key = SearchEngineManager.engines.keys.elementAt(position)
                SearchEngineManager.saveSelectedEngine(this@MainActivity, key)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun setupTopActions() {
        findViewById<MaterialButton>(R.id.buttonSearch).setOnClickListener {
            searchFromInput()
        }
        inputSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                searchFromInput()
                true
            } else {
                false
            }
        }

        findViewById<ImageButton>(R.id.buttonSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<TextView>(R.id.textCustomize).setOnClickListener {
            showCustomizeFeedDialog()
        }
    }

    private fun setupShortcutActions() {
        findViewById<ImageButton>(R.id.shortcutChatGpt).setOnClickListener {
            openUrl("https://chatgpt.com")
        }
        findViewById<ImageButton>(R.id.shortcutYoutube).setOnClickListener {
            openUrl("https://youtube.com")
        }
        findViewById<ImageButton>(R.id.shortcutX).setOnClickListener {
            openUrl("https://x.com")
        }
        findViewById<ImageButton>(R.id.shortcutInstagram).setOnClickListener {
            openUrl("https://instagram.com")
        }
    }

    private fun setupFeedActions() {
        feedCards.clear()
        feedCards.add(findViewById<MaterialCardView>(R.id.cardPrimary))
        feedCards.add(findViewById<MaterialCardView>(R.id.cardSecondary))
        feedCards.add(findViewById<MaterialCardView>(R.id.cardTertiary))
        feedCards.add(findViewById<MaterialCardView>(R.id.cardQuaternary))
        feedCards.add(findViewById<MaterialCardView>(R.id.cardQuinary))

        feedTitles.clear()
        feedTitles.add(findViewById<TextView>(R.id.textCardPrimary))
        feedTitles.add(findViewById<TextView>(R.id.textCardSecondary))
        feedTitles.add(findViewById<TextView>(R.id.textCardTertiary))
        feedTitles.add(findViewById<TextView>(R.id.textCardQuaternary))
        feedTitles.add(findViewById<TextView>(R.id.textCardQuinary))

        feedCards.forEachIndexed { index, card ->
            card.setOnClickListener {
                topNews.getOrNull(index)?.let { item -> openUrl(item.link) }
            }
        }
    }

    private fun loadRealNewsFeed() {
        Thread {
            val fetchedNews = runCatching { fetchTopNews(buildNewsFeedUrl()) }.getOrDefault(emptyList())
            runOnUiThread {
                if (fetchedNews.isNotEmpty()) {
                    topNews = fetchedNews
                    bindNewsToCards(fetchedNews)
                } else {
                    toast(getString(R.string.msg_news_load_failed))
                }
            }
        }.start()
    }

    private fun bindNewsToCards(news: List<NewsItem>) {
        feedTitles.forEachIndexed { index, titleView ->
            val item = news.getOrNull(index)
            titleView.text = item?.title ?: getString(R.string.news_not_available)
            feedCards[index].isEnabled = item != null
            feedCards[index].alpha = if (item != null) 1f else 0.55f
        }
    }

    private fun fetchTopNews(url: String): List<NewsItem> {
        val parser = Xml.newPullParser()
        URL(url).openStream().use { input ->
            parser.setInput(input, null)
            var eventType = parser.eventType
            val items = mutableListOf<NewsItem>()
            var inItem = false
            var title: String? = null
            var link: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT && items.size < MAX_NEWS_ITEMS) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "item" -> {
                                inItem = true
                                title = null
                                link = null
                            }
                            "title" -> if (inItem) title = parser.nextText().orEmpty().trim()
                            "link" -> if (inItem) link = parser.nextText().orEmpty().trim()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "item" && inItem) {
                            val articleTitle = title?.takeIf { it.isNotBlank() }
                            val articleLink = link?.takeIf { it.startsWith("http") }
                            if (articleTitle != null && articleLink != null) {
                                items += NewsItem(articleTitle, articleLink)
                            }
                            inItem = false
                        }
                    }
                }
                eventType = parser.next()
            }
            return items
        }
    }

    private fun showCustomizeFeedDialog() {
        val countries = listOf("US", "GB", "IN", "CA", "AU")
        val categories = listOf("Top", "World", "Business", "Technology", "Sports", "Health")

        val countrySpinner = Spinner(this)
        countrySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, countries).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        countrySpinner.setSelection(countries.indexOf(selectedCountry()).coerceAtLeast(0))

        val categorySpinner = Spinner(this)
        categorySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        categorySpinner.setSelection(categories.indexOf(selectedCategory()).coerceAtLeast(0))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 8)
            addView(TextView(this@MainActivity).apply { text = getString(R.string.feed_country_label) })
            addView(countrySpinner)
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.feed_category_label)
                setPadding(0, 20, 0, 0)
            })
            addView(categorySpinner)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.title_customize_feed)
            .setView(content)
            .setPositiveButton(R.string.action_save) { _, _ ->
                feedPrefs.edit()
                    .putString(KEY_FEED_COUNTRY, countries[countrySpinner.selectedItemPosition])
                    .putString(KEY_FEED_CATEGORY, categories[categorySpinner.selectedItemPosition])
                    .apply()
                loadRealNewsFeed()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun selectedCountry(): String = feedPrefs.getString(KEY_FEED_COUNTRY, "US").orEmpty()

    private fun selectedCategory(): String = feedPrefs.getString(KEY_FEED_CATEGORY, "Top").orEmpty()

    private fun buildNewsFeedUrl(): String {
        val country = selectedCountry()
        val category = selectedCategory()
        val topic = CATEGORY_TO_TOPIC[category]
        val language = "en-$country"
        return if (topic == null) {
            "https://news.google.com/rss?hl=$language&gl=$country&ceid=$country:en"
        } else {
            "https://news.google.com/rss/headlines/section/topic/$topic?hl=$language&gl=$country&ceid=$country:en"
        }
    }

    private fun setupBottomActions() {
        findViewById<MaterialButton>(R.id.navHome).setOnClickListener {
            toast(getString(R.string.msg_home_active))
        }
        findViewById<MaterialButton>(R.id.navHistory).setOnClickListener {
            openSavedSites(SavedSiteStore.TYPE_HISTORY)
        }
        findViewById<MaterialButton>(R.id.navTabs).setOnClickListener {
            startActivity(Intent(this, TabManagerActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.navMenu).setOnClickListener {
            showBottomMenuSheet()
        }
    }

    private fun searchFromInput() {
        val raw = inputSearch.text.toString().trim()
        if (raw.isEmpty()) {
            toast(getString(R.string.msg_enter_search))
            return
        }
        val resolvedUrl = SearchEngineManager.resolveInputToUrl(this, raw)
        openUrl(resolvedUrl)
    }

    private fun openUrl(url: String) {
        SavedSiteStore.add(this, SavedSiteStore.TYPE_HISTORY, url)

        val lower = url.lowercase(Locale.US)
        if (lower.endsWith(".mp4") || lower.endsWith(".m3u8") || lower.endsWith(".webm")) {
            startActivity(
                Intent(this, VideoPlayerActivity::class.java)
                    .putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, url)
            )
            return
        }

        startActivity(
            Intent(this, BrowserTabActivity::class.java)
                .putExtra(BrowserTabActivity.EXTRA_URL, url)
        )
    }

    private fun showBottomMenuSheet() {
        val dialog = BottomSheetDialog(this)
        val content = layoutInflater.inflate(R.layout.bottom_sheet_tab_menu, null)
        dialog.setContentView(content)

        content.findViewById<MaterialButton>(R.id.menuPrivateVault).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, VaultActivity::class.java))
        }
        content.findViewById<android.view.View>(R.id.rowNewTab).setOnClickListener {
            dialog.dismiss()
        }
        content.findViewById<android.view.View>(R.id.rowAddBookmark).visibility = android.view.View.GONE

        content.findViewById<android.view.View>(R.id.rowBookmarks).setOnClickListener {
            dialog.dismiss()
            openSavedSites(SavedSiteStore.TYPE_BOOKMARKS)
        }
        content.findViewById<android.view.View>(R.id.rowHistory).setOnClickListener {
            dialog.dismiss()
            openSavedSites(SavedSiteStore.TYPE_HISTORY)
        }
        content.findViewById<android.view.View>(R.id.rowDownloads).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, DownloadsActivity::class.java))
        }

        content.findViewById<SwitchMaterial>(R.id.switchDesktopSite).apply {
            isChecked = false
            (parent as? View)?.visibility = View.GONE
        }

        dialog.show()
    }

    private fun openSavedSites(type: String) {
        val entries = SavedSiteStore.list(this, type)
        if (entries.isEmpty()) {
            toast(getString(if (type == SavedSiteStore.TYPE_HISTORY) R.string.msg_no_history else R.string.msg_no_bookmarks))
            return
        }
        savedSitesLauncher.launch(SavedSitesActivity.createIntent(this, type))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private data class NewsItem(
        val title: String,
        val link: String
    )

    companion object {
        private const val MAX_NEWS_ITEMS = 5
        private const val FEED_PREFS = "feed_preferences"
        private const val KEY_FEED_COUNTRY = "feed_country"
        private const val KEY_FEED_CATEGORY = "feed_category"
        private val CATEGORY_TO_TOPIC = mapOf(
            "Top" to null,
            "World" to "WORLD",
            "Business" to "BUSINESS",
            "Technology" to "TECHNOLOGY",
            "Sports" to "SPORTS",
            "Health" to "HEALTH"
        )
    }
}
