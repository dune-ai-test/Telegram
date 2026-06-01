package org.telegram.decoy

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL

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

        allArticles.sortByDescending { it.pubDate }
        return FetchResult(allArticles.take(10), now)
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
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var title = ""
        var description = ""
        var link = ""
        var pubDate = ""
        var imageUrl: String? = null
        var inItem = false
        var currentTag = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tag.equals("item", true) || tag.equals("entry", true)) {
                        inItem = true
                        title = ""
                        description = ""
                        link = ""
                        pubDate = ""
                        imageUrl = null
                    }
                    if (inItem) {
                        currentTag = tag
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inItem) {
                        val text = parser.text?.trim() ?: ""
                        when {
                            currentTag.equals("title", true) && title.isEmpty() -> title = text
                            currentTag.equals("description", true) && description.isEmpty() -> {
                                description = text.replace(Regex("<[^>]*>"), "").trim()
                                if (description.length > 200) description = description.take(200) + "..."
                            }
                            currentTag.equals("link", true) && link.isEmpty() -> link = text
                            currentTag.equals("pubDate", true) && pubDate.isEmpty() -> pubDate = text
                            currentTag.equals("updated", true) && pubDate.isEmpty() -> pubDate = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tag.equals("item", true) || tag.equals("entry", true)) {
                        if (title.isNotBlank()) {
                            articles.add(NewsArticle(
                                link = link.ifBlank { title.hashCode().toString() },
                                title = title,
                                description = description,
                                source = extractSourceName(sourceUrl),
                                pubDate = pubDate.ifBlank { System.currentTimeMillis().toString() },
                                imageUrl = imageUrl
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
