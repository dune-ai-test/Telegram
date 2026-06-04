package org.telegram.decoy

sealed class NewsListItem {
    data class Article(val data: NewsArticle) : NewsListItem()
    data class Header(val source: String, val count: Int) : NewsListItem()
}
