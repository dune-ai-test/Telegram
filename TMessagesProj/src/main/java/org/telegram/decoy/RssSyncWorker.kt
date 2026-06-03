package org.telegram.decoy

import android.content.Context
import android.content.SharedPreferences
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class RssSyncWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val prefs = getPrefs(applicationContext)
        val feeds = prefs.getStringSet("rss_feeds", null)?.toList()
            ?: RssFetcher.DEFAULT_FEEDS

        return try {
            val result = RssFetcher.fetch(feeds)
            val db = NewsDatabase.getInstance(applicationContext)
            runBlocking {
                db.newsDao().clearAll()
                db.newsDao().insertAll(result.articles)
            }

            prefs.edit()
                .putLong("last_fetched_timestamp", result.timestamp)
                .apply()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "rss_sync"

        fun getPrefs(ctx: Context): SharedPreferences {
            return ctx.getSharedPreferences("decoy_newsreader", Context.MODE_PRIVATE)
        }

        fun getLastFetchedTimestamp(ctx: Context): Long {
            return getPrefs(ctx).getLong("last_fetched_timestamp", 0L)
        }

        fun getFeedUrls(ctx: Context): List<String> {
            val prefs = getPrefs(ctx)
            return prefs.getStringSet("rss_feeds", null)?.toList()
                ?: RssFetcher.DEFAULT_FEEDS
        }

        fun addFeedUrl(ctx: Context, url: String) {
            val prefs = getPrefs(ctx)
            val feeds = prefs.getStringSet("rss_feeds", null)?.toMutableSet()
                ?: RssFetcher.DEFAULT_FEEDS.toMutableSet()
            feeds.add(url)
            prefs.edit().putStringSet("rss_feeds", feeds).apply()
        }

        fun removeFeedUrl(ctx: Context, url: String) {
            val prefs = getPrefs(ctx)
            val feeds = prefs.getStringSet("rss_feeds", null)?.toMutableSet()
                ?: RssFetcher.DEFAULT_FEEDS.toMutableSet()
            feeds.remove(url)
            prefs.edit().putStringSet("rss_feeds", feeds).apply()
        }

        fun scheduleSync(ctx: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<RssSyncWorker>(
                30, TimeUnit.MINUTES
            ).setConstraints(constraints).build()

            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun runOnce(ctx: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = androidx.work.OneTimeWorkRequestBuilder<RssSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(ctx).enqueue(request)
        }
    }
}
