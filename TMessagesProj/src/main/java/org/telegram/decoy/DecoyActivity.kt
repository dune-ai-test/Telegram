package org.telegram.decoy

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DecoyActivity : AppCompatActivity() {

    private lateinit var newsList: RecyclerView
    private lateinit var adapter: NewsAdapter
    private lateinit var searchBar: EditText
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var timestampText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var contentArea: FrameLayout
    private lateinit var emptyState: View
    private lateinit var refreshButton: View

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isFetching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_decoy)

        newsList = findViewById(R.id.news_list)
        searchBar = findViewById(R.id.search_bar)
        bottomNav = findViewById(R.id.bottom_nav)
        timestampText = findViewById(R.id.timestamp_text)
        progressBar = findViewById(R.id.progress_bar)
        contentArea = findViewById(R.id.content_area)
        emptyState = findViewById(R.id.empty_state)
        refreshButton = findViewById(R.id.refresh_button)

        adapter = NewsAdapter()
        newsList.layoutManager = LinearLayoutManager(this)
        newsList.adapter = adapter

        setupSearch()
        setupBottomNav()
        setupTitleTap()
        setupRefreshButton()
        setupArticleClicks()

        // Load whatever is in the DB
        loadNews()

        // Schedule periodic background RSS sync
        RssSyncWorker.scheduleSync(this)
    }

    private fun setupTitleTap() {
        val titleView = findViewById<TextView>(R.id.app_title)
        titleView.setOnClickListener {
            val activated = UnlockValidator.handleTap()
            searchBar.hint = if (activated) {
                getString(R.string.search_hint_unlock)
            } else {
                getString(R.string.search_hint_normal)
            }
        }
    }

    private fun setupSearch() {
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!UnlockValidator.unlockModeActive) return
                val input = s?.toString() ?: return
                if (input.length < 3) return

                if (UnlockValidator.validate(this@DecoyActivity, input)) {
                    unlockTelegram()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupBottomNav() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_news -> {
                    showNewsList()
                    true
                }
                R.id.nav_settings -> {
                    showSettings()
                    true
                }
                else -> false
            }
        }
    }

    private fun showNewsList() {
        contentArea.removeAllViews()
        contentArea.addView(newsList)
        contentArea.addView(emptyState)
        contentArea.addView(progressBar)
        updateTimestamp()
    }

    private fun setupRefreshButton() {
        refreshButton.setOnClickListener {
            triggerRefresh()
        }
    }

    private fun setupArticleClicks() {
        adapter.setOnItemClickListener { article ->
            openArticle(article)
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

        // Reload from DB after a short delay so the user sees the new articles
        scope.launch {
            delay(3500)
            loadNews(silent = true)
            isFetching = false
        }
    }

    private fun showSettings() {
        contentArea.removeAllViews()
        val view = layoutInflater.inflate(R.layout.fragment_settings, contentArea, false)
        contentArea.addView(view)

        val rssList = view.findViewById<LinearLayout>(R.id.rss_list)
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
                row.findViewById<TextView>(R.id.feed_url_text).text = feed
                row.findViewById<View>(R.id.feed_remove_btn).setOnClickListener {
                    RssSyncWorker.removeFeedUrl(this, feed)
                    showSettings()
                }
                rssList.addView(row)
            }
        }

        val addBtn = view.findViewById<Button>(R.id.add_feed_btn)
        addBtn.setOnClickListener {
            val url = view.findViewById<EditText>(R.id.new_feed_url).text.toString().trim()
            if (url.isNotBlank()) {
                RssSyncWorker.addFeedUrl(this, url)
                showSettings()
            }
        }
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
                    adapter.submitList(articles)
                    progressBar.visibility = View.GONE
                    if (articles.isEmpty()) {
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

    private fun updateTimestamp() {
        val ts = RssSyncWorker.getLastFetchedTimestamp(this)
        if (ts > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
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
