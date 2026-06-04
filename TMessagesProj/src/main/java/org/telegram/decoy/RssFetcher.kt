package org.telegram.decoy

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

object RssFetcher {

    val DEFAULT_FEEDS = listOf(
        "https://feeds.bbci.co.uk/news/rss.xml",
        "https://rss.nytimes.com/services/xml/rss/nyt/World.xml",
        "https://feeds.reuters.com/reuters/topNews",
        "https://rss.cnn.com/rss/edition.rss",
        "https://feeds.feedburner.com/TechCrunch/"
    )

    data class FetchResult(
        val articles: List<NewsArticle>,
        val timestamp: Long
    )

    fun fetch(feedUrls: List<String>): FetchResult {
        val allArticles = mutableListOf<NewsArticle>()
        val now = System.currentTimeMillis()

        for (url in feedUrls) {
            try {
                val xml = downloadXml(url)
                val articles = parseRss(xml, url)
                allArticles.addAll(articles)
            } catch (_: Exception) {
            }
        }

        allArticles.sortByDescending { parsePubDateMillis(it.pubDate) ?: 0L }
        return FetchResult(allArticles.take(30), now)
    }

    private fun downloadXml(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun parseRss(xml: String, sourceUrl: String): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var title = ""
        var description = ""
        var contentEncoded = ""
        var link = ""
        var pubDate = ""
        var imageUrl: String? = null
        var inItem = false
        var currentTag = ""
        var inContentEncoded = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tag.equals("item", true) || tag.equals("entry", true)) {
                        inItem = true
                        title = ""
                        description = ""
                        contentEncoded = ""
                        link = ""
                        pubDate = ""
                        imageUrl = null
                    }
                    if (inItem) {
                        currentTag = tag
                        if (tag.equals("content:encoded", true) ||
                            tag.equals("encoded", true)) {
                            inContentEncoded = true
                        }
                        if (tag.equals("media:content", true) ||
                            tag.equals("media:thumbnail", true) ||
                            tag.equals("enclosure", true)) {
                            val url = parser.getAttributeValue(null, "url")
                            if (!url.isNullOrBlank() && imageUrl == null) {
                                imageUrl = url
                            }
                        }
                        if (tag.equals("image", true) && imageUrl == null) {
                            // may be parsed in TEXT
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inItem) {
                        val text = parser.text?.trim() ?: ""
                        when {
                            inContentEncoded -> contentEncoded += text
                            currentTag.equals("title", true) && title.isEmpty() -> title = text
                            currentTag.equals("description", true) && description.isEmpty() -> {
                                description = text
                            }
                            currentTag.equals("link", true) && link.isEmpty() -> link = text
                            currentTag.equals("pubDate", true) && pubDate.isEmpty() -> pubDate = text
                            currentTag.equals("updated", true) && pubDate.isEmpty() -> pubDate = text
                            currentTag.equals("url", true) && currentTag == "image" && imageUrl == null -> {
                                if (text.isNotBlank()) imageUrl = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (inContentEncoded && (tag.equals("content:encoded", true) ||
                                tag.equals("encoded", true))) {
                        inContentEncoded = false
                    }
                    if (tag.equals("item", true) || tag.equals("entry", true)) {
                        if (title.isNotBlank()) {
                            val finalLink = if (link.isBlank()) {
                                title.hashCode().toString()
                            } else link
                            val cleanDescription = if (description.isNotBlank()) {
                                stripHtml(description).let {
                                    if (it.length > 400) it.take(400) + "..." else it
                                }
                            } else ""
                            val finalContent = if (contentEncoded.isNotBlank()) {
                                contentEncoded
                            } else if (description.isNotBlank()) {
                                description
                            } else null
                            articles.add(NewsArticle(
                                link = finalLink,
                                title = title,
                                description = cleanDescription,
                                source = extractSourceName(sourceUrl),
                                pubDate = pubDate.ifBlank { System.currentTimeMillis().toString() },
                                imageUrl = imageUrl,
                                content = finalContent
                            ))
                        }
                        inItem = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
        return articles
    }

    private fun parsePubDateMillis(pubDate: String): Long? {
        if (pubDate.isBlank()) return null
        if (pubDate.all { it.isDigit() }) {
            return pubDate.toLongOrNull()
        }
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

    fun stripHtml(html: String): String {
        if (html.isBlank()) return ""
        var s = html
        s = s.replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), " ")
        s = s.replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), " ")
        s = s.replace(Regex("<[^>]+>"), " ")
        s = s.replace(Regex("&nbsp;"), " ")
        s = s.replace(Regex("&amp;"), "&")
        s = s.replace(Regex("&lt;"), "<")
        s = s.replace(Regex("&gt;"), ">")
        s = s.replace(Regex("&quot;"), "\"")
        s = s.replace(Regex("&#39;"), "'")
        s = s.replace(Regex("&apos;"), "'")
        s = s.replace(Regex("\\s+"), " ")
        return s.trim()
    }

    private fun extractSourceName(url: String): String {
        return when {
            url.contains("bbc") -> "BBC News"
            url.contains("nytimes") -> "New York Times"
            url.contains("reuters") -> "Reuters"
            url.contains("cnn") -> "CNN"
            url.contains("techcrunch") -> "TechCrunch"
            url.contains("feedburner") -> "TechCrunch"
            else -> url.substringAfter("//").substringBefore("/")
        }
    }
}
