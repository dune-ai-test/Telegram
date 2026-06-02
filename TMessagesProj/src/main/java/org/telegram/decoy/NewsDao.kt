package org.telegram.decoy

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NewsDao {
    @Query("SELECT * FROM news_articles ORDER BY fetchedAt DESC")
    suspend fun getAll(): List<NewsArticle>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(articles: List<NewsArticle>)

    @Query("DELETE FROM news_articles WHERE link NOT IN (SELECT link FROM news_articles ORDER BY fetchedAt DESC LIMIT 30)")
    suspend fun trimToLast30()

    @Query("SELECT COUNT(*) FROM news_articles")
    suspend fun count(): Int

    @Query("DELETE FROM news_articles")
    suspend fun clearAll()
}
