package org.telegram.decoy

import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArticleReaderActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var sourceLabel: TextView
    private lateinit var heroImage: ImageView
    private lateinit var articleTitle: TextView
    private lateinit var articleSource: TextView
    private lateinit var articleTime: TextView
    private lateinit var articleBody: TextView
    private lateinit var articleLinkLabel: TextView
    private lateinit var loadingBar: ProgressBar

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article_reader)

        backButton = findViewById(R.id.back_button)
        sourceLabel = findViewById(R.id.source_label)
        heroImage = findViewById(R.id.hero_image)
        articleTitle = findViewById(R.id.article_title)
        articleSource = findViewById(R.id.article_source)
        articleTime = findViewById(R.id.article_time)
        articleBody = findViewById(R.id.article_body)
        articleLinkLabel = findViewById(R.id.article_link_label)
        loadingBar = findViewById(R.id.loading_bar)

        backButton.setOnClickListener { finish() }

        val link = intent.getStringExtra(EXTRA_LINK) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: ""
        val pubDate = intent.getStringExtra(EXTRA_PUB_DATE) ?: ""
        val imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL)
        val initialContent = intent.getStringExtra(EXTRA_CONTENT)
        val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: ""

        sourceLabel.text = source
        articleTitle.text = title
        articleSource.text = source
        articleTime.text = formatTime(pubDate)
        articleLinkLabel.text = if (link.isNotBlank()) link else ""

        if (!imageUrl.isNullOrBlank()) {
            heroImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(imageUrl)
                .centerCrop()
                .into(heroImage)
        } else {
            heroImage.visibility = View.GONE
        }

        // Try full content first, then description, then fetch from URL
        when {
            !initialContent.isNullOrBlank() -> {
                renderHtml(initialContent)
            }
            description.isNotBlank() -> {
                renderHtml(description)
            }
            link.isNotBlank() -> {
                fetchAndRender(link)
            }
            else -> {
                articleBody.text = getString(R.string.no_content)
            }
        }
    }

    private fun renderHtml(html: String) {
        val cleaned = RssFetcher.stripHtml(html)
        articleBody.text = HtmlCompat.fromHtml(cleaned, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private fun fetchAndRender(url: String) {
        loadingBar.visibility = View.VISIBLE
        articleBody.text = ""
        scope.launch(Dispatchers.IO) {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                conn.instanceFollowRedirects = true
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                val extracted = extractArticleBody(html)
                withContext(Dispatchers.Main) {
                    loadingBar.visibility = View.GONE
                    if (extracted.isNotBlank()) {
                        articleBody.text = HtmlCompat.fromHtml(
                            RssFetcher.stripHtml(extracted),
                            HtmlCompat.FROM_HTML_MODE_LEGACY
                        )
                    } else {
                        articleBody.text = getString(R.string.no_content)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingBar.visibility = View.GONE
                    articleBody.text = getString(R.string.no_content)
                }
            }
        }
    }

    /**
     * Best-effort extraction of article body HTML from a typical news page.
     * Looks for common article containers: <article>, role="article", <main>.
     */
    private fun extractArticleBody(html: String): String {
        val patterns = listOf(
            Regex("(?is)<article[^>]*>(.*?)</article>"),
            Regex("(?is)<main[^>]*>(.*?)</main>"),
            Regex("(?is)<div[^>]+role=[\"']article[\"'][^>]*>(.*?)</div>"),
            Regex("(?is)<div[^>]+class=[\"'][^\"']*(article|story|content|post-body)[^\"']*[\"'][^>]*>(.*?)</div>")
        )
        for (p in patterns) {
            val match = p.find(html)
            if (match != null) {
                val body = match.groupValues[1].ifBlank { match.groupValues[2] }
                if (body.length > 200) return body
            }
        }
        return ""
    }

    private fun formatTime(pubDate: String): String {
        if (pubDate.isBlank()) return ""
        val parsed = parsePubDateMillis(pubDate) ?: return ""
        val now = System.currentTimeMillis()
        val delta = now - parsed
        return when {
            delta < DateUtils.MINUTE_IN_MILLIS -> getString(R.string.time_just_now)
            delta < DateUtils.HOUR_IN_MILLIS -> getString(
                R.string.time_minutes_ago,
                (delta / DateUtils.MINUTE_IN_MILLIS).toInt()
            )
            delta < DateUtils.DAY_IN_MILLIS -> getString(
                R.string.time_hours_ago,
                (delta / DateUtils.HOUR_IN_MILLIS).toInt()
            )
            delta < 7 * DateUtils.DAY_IN_MILLIS -> getString(
                R.string.time_days_ago,
                (delta / DateUtils.DAY_IN_MILLIS).toInt()
            )
            else -> SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(parsed))
        }
    }

    private fun parsePubDateMillis(pubDate: String): Long? {
        if (pubDate.isBlank()) return null
        if (pubDate.all { it.isDigit() }) return pubDate.toLongOrNull()
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in formats) {
            try {
                return SimpleDateFormat(pattern, Locale.US).parse(pubDate)?.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_LINK = "extra_link"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SOURCE = "extra_source"
        const val EXTRA_PUB_DATE = "extra_pub_date"
        const val EXTRA_IMAGE_URL = "extra_image_url"
        const val EXTRA_CONTENT = "extra_content"
        const val EXTRA_DESCRIPTION = "extra_description"
    }
}
