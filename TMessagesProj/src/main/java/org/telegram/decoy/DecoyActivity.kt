package org.telegram.decoy

import org.telegram.messenger.R
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DecoyActivity : AppCompatActivity() {

    private lateinit var newsList: RecyclerView
    private lateinit var adapter: NewsAdapter
    private lateinit var searchBar: EditText
    private lateinit var timestampText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var contentArea: FrameLayout
    private lateinit var emptyState: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var settingsButton: View

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isFetching = false
    private var allArticles: List<NewsArticle> = emptyList()
    private var currentSource: String? = null
    private var groupBySource: Boolean = false
    private var showingSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_decoy)

        newsList = findViewById(R.id.news_list)
        searchBar = findViewById(R.id.search_bar)
        timestampText = findViewById(R.id.timestamp_text)
        progressBar = findViewById(R.id.progress_bar)
        contentArea = findViewById(R.id.content_area)
        emptyState = findViewById(R.id.empty_state)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        settingsButton = findViewById(R.id.settings_button)

        adapter = NewsAdapter()
        newsList.layoutManager = LinearLayoutManager(this)
        newsList.adapter = adapter

        setupSearch()
        setupTitleTap()
        setupArticleClicks()
        setupSwipeRefresh()
        setupSettingsButton()

        loadNews()

        RssSyncWorker.scheduleSync(this)
    }

    private fun setupTitleTap() {
        val titleView = findViewById<TextView>(R.id.app_title)
        titleView.setOnClickListener {
            val activated = UnlockValidator.handleTap()
            searchBar.hint = if (UnlockValidator.isLocked(this)) {
                getString(R.string.search_hint_locked)
            } else if (activated) {
                getString(R.string.search_hint_unlock)
            } else {
                getString(R.string.search_hint_normal)
            }
        }
    }

    private fun setupSearch() {
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString() ?: ""

                if (UnlockValidator.unlockModeActive) {
                    if (validateUnlock(input)) {
                        unlockTelegram()
                    }
                } else {
                    filterArticles(input)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun validateUnlock(input: String): Boolean {
        val first = allArticles.firstOrNull()
        val title = first?.title ?: ""
        val source = first?.source ?: ""
        val expected = UnlockValidator.generateExpectedCode(this, title, source)
        if (expected.isEmpty() || input.length < expected.length) return false
        return UnlockValidator.validate(this, input, title, source)
    }

    private fun filterArticles(query: String) {
        val filtered = if (query.isBlank()) {
            allArticles
        } else {
            val q = query.lowercase()
            allArticles.filter {
                it.title.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.source.lowercase().contains(q)
            }
        }
        adapter.submitList(filtered.map { NewsListItem.Article(it) })
        emptyState.visibility = if (filtered.isEmpty() && allArticles.isNotEmpty()) {
            View.VISIBLE
        } else if (filtered.isEmpty() && allArticles.isEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            triggerRefresh()
        }
        swipeRefresh.setColorSchemeResources(R.color.newsreader_colorPrimary)
    }

    private fun setupSettingsButton() {
        settingsButton.setOnClickListener {
            if (showingSettings) {
                showNewsList()
            } else {
                showSettings()
            }
        }
    }

    private fun showSettings() {
        showingSettings = true
        contentArea.removeAllViews()
        val view = layoutInflater.inflate(R.layout.fragment_settings, contentArea, false)
        contentArea.addView(view)
        swipeRefresh.visibility = View.GONE

        val rssList = view.findViewById(R.id.rss_list) as LinearLayout
        rssList.removeAllViews()

        val feeds = RssSyncWorker.getFeedUrls(this)
        if (feeds.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No feeds configured"
            tv.setTextColor(ContextCompat.getColor(this, R.color.newsreader_textTertiary))
            tv.textSize = 13f
            val pad = (12 * resources.displayMetrics.density).toInt()
            tv.setPadding(pad, pad, pad, pad)
            rssList.addView(tv)
        } else {
            for (feed in feeds) {
                val row = layoutInflater.inflate(R.layout.item_rss_url, rssList, false)
                (row.findViewById(R.id.feed_url_text) as TextView).text = feed
                row.findViewById<View>(R.id.feed_remove_btn).setOnClickListener {
                    RssSyncWorker.removeFeedUrl(this, feed)
                    showSettings()
                }
                rssList.addView(row)
            }
        }

        val groupToggle = view.findViewById<Switch>(R.id.group_toggle)
        groupToggle.isChecked = groupBySource
        groupToggle.setOnCheckedChangeListener { _, checked ->
            getPrefs().edit().putBoolean("group_by_source", checked).apply()
            groupBySource = checked
            currentSource = null
            if (checked) {
                showGroupList()
            } else {
                adapter.submitList(allArticles.map { NewsListItem.Article(it) })
                adapter.setOnHeaderClickListener(null)
                updateTimestamp()
            }
        }

        val addBtn = view.findViewById(R.id.add_feed_btn) as Button
        addBtn.setOnClickListener {
            val url = (view.findViewById(R.id.new_feed_url) as EditText).text.toString().trim()
            if (url.isNotBlank()) {
                RssSyncWorker.addFeedUrl(this, url)
                showSettings()
            }
        }
    }

    private fun setupArticleClicks() {
        adapter.setOnItemClickListener { article ->
            openArticle(article)
        }
        adapter.setOnHeaderClickListener { source ->
            showSourceArticles(source)
        }
    }

    private fun openArticle(article: NewsArticle) {
        val intent = Intent(this, ArticleReaderActivity::class.java).apply {
            putExtra(ArticleReaderActivity.EXTRA_LINK, article.link)
            putExtra(ArticleReaderActivity.EXTRA_TITLE, article.title)
            putExtra(ArticleReaderActivity.EXTRA_SOURCE, article.source)
            putExtra(ArticleReaderActivity.EXTRA_PUB_DATE, article.pubDate)
            putExtra(ArticleReaderActivity.EXTRA_IMAGE_URL, article.imageUrl)
            putExtra(ArticleReaderActivity.EXTRA_CONTENT, article.content)
            putExtra(ArticleReaderActivity.EXTRA_DESCRIPTION, article.description)
        }
        startActivity(intent)
    }

    private fun triggerRefresh() {
        if (isFetching) return
        isFetching = true
        progressBar.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        timestampText.text = getString(R.string.refreshing)
        timestampText.visibility = View.VISIBLE

        RssSyncWorker.runOnce(this)

        scope.launch {
            delay(3500)
            loadNews(silent = true)
            isFetching = false
            swipeRefresh.isRefreshing = false
        }
    }

    private fun showNewsList() {
        showingSettings = false
        contentArea.removeAllViews()
        contentArea.addView(swipeRefresh)
        contentArea.addView(emptyState)
        contentArea.addView(progressBar)
        swipeRefresh.visibility = View.VISIBLE
        if (currentSource != null) {
            currentSource = null
            searchBar.hint = getString(R.string.search_hint_normal)
            if (groupBySource) {
                showGroupList()
            } else {
                adapter.submitList(allArticles.map { NewsListItem.Article(it) })
                updateTimestamp()
            }
            return
        }
        if (groupBySource && allArticles.isNotEmpty()) {
            showGroupList()
        }
        updateTimestamp()
    }

    private fun loadNews(silent: Boolean = false) {
        if (!silent) {
            progressBar.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
        }
        scope.launch(Dispatchers.IO) {
            try {
                val articles = NewsDatabase.getInstance(this@DecoyActivity)
                    .newsDao().getAll()
                withContext(Dispatchers.Main) {
                    allArticles = articles
                    groupBySource = getPrefs().getBoolean("group_by_source", false)
                    if (groupBySource && currentSource == null) {
                        showGroupList()
                    } else if (groupBySource && currentSource != null) {
                        showSourceArticles(currentSource!!)
                    } else {
                        val query = searchBar.text.toString()
                        if (query.isBlank()) {
                            adapter.submitList(allArticles.map { NewsListItem.Article(it) })
                        } else {
                            filterArticles(query)
                        }
                    }
                    progressBar.visibility = View.GONE
                    if (allArticles.isEmpty()) {
                        emptyState.visibility = View.VISIBLE
                    } else {
                        emptyState.visibility = View.GONE
                    }
                    updateTimestamp()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun getPrefs() = getSharedPreferences("decoy_newsreader", MODE_PRIVATE)

    private fun showGroupList() {
        currentSource = null
        val groups = allArticles.groupBy { it.source }
            .map { (source, articles) -> NewsListItem.Header(source, articles.size) }
            .sortedBy { it.source }
        adapter.submitList(groups)
        adapter.setOnHeaderClickListener { source ->
            showSourceArticles(source)
        }
        timestampText.visibility = View.GONE
    }

    private fun showSourceArticles(source: String) {
        currentSource = source
        val filtered = allArticles.filter { it.source == source }
        adapter.submitList(filtered.map { NewsListItem.Article(it) })
        adapter.setOnHeaderClickListener(null)
        searchBar.setText("")
        searchBar.hint = "All ${source} articles"
    }

    private fun updateTimestamp() {
        val ts = RssSyncWorker.getLastFetchedTimestamp(this)
        if (ts > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            timestampText.text = "Updated ${sdf.format(Date(ts))}"
            timestampText.visibility = View.VISIBLE
        } else {
            timestampText.visibility = View.GONE
        }
    }

    private fun unlockTelegram() {
        UnlockValidator.deactivate()
        searchBar.setText("")
        searchBar.hint = getString(R.string.search_hint_normal)
        try {
            val intent = Intent()
            intent.setClassName(applicationContext.packageName, "org.telegram.ui.LaunchActivity")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
