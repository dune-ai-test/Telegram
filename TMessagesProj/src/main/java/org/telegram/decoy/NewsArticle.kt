package org.telegram.decoy

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news_articles")
data class NewsArticle(
    @PrimaryKey val link: String,
    val title: String,
    val description: String,
    val source: String,
    val pubDate: String,
    val imageUrl: String?,
    val fetchedAt: Long = System.currentTimeMillis()
)
