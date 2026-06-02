package org.telegram.decoy

import org.telegram.messenger.R
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NewsAdapter : ListAdapter<NewsArticle, NewsAdapter.ViewHolder>(DIFF) {

    private var onItemClick: ((NewsArticle) -> Unit)? = null

    fun setOnItemClickListener(listener: (NewsArticle) -> Unit) {
        onItemClick = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_news_article, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val article = getItem(position)
        holder.title.text = article.title
        holder.description.text = article.description
        holder.source.text = article.source
        holder.time.text = formatTime(holder.itemView.context, article)

        if (!article.imageUrl.isNullOrBlank()) {
            holder.image.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(article.imageUrl)
                .centerCrop()
                .into(holder.image)
        } else {
            holder.image.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(article)
        }
    }

    private fun formatTime(ctx: android.content.Context, article: NewsArticle): String {
        val parsed = parsePubDateMillis(article.pubDate)
        if (parsed == null) return ""
        val now = System.currentTimeMillis()
        val delta = now - parsed
        return when {
            delta < DateUtils.MINUTE_IN_MILLIS -> ctx.getString(R.string.time_just_now)
            delta < DateUtils.HOUR_IN_MILLIS -> ctx.getString(
                R.string.time_minutes_ago,
                (delta / DateUtils.MINUTE_IN_MILLIS).toInt()
            )
            delta < DateUtils.DAY_IN_MILLIS -> ctx.getString(
                R.string.time_hours_ago,
                (delta / DateUtils.HOUR_IN_MILLIS).toInt()
            )
            delta < 7 * DateUtils.DAY_IN_MILLIS -> ctx.getString(
                R.string.time_days_ago,
                (delta / DateUtils.DAY_IN_MILLIS).toInt()
            )
            else -> SimpleDateFormat("MMM d", Locale.US).format(Date(parsed))
        }
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

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.news_title)
        val description: TextView = itemView.findViewById(R.id.news_description)
        val source: TextView = itemView.findViewById(R.id.news_source)
        val time: TextView = itemView.findViewById(R.id.news_time)
        val image: ImageView = itemView.findViewById(R.id.news_image)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<NewsArticle>() {
            override fun areItemsTheSame(a: NewsArticle, b: NewsArticle) = a.link == b.link
            override fun areContentsTheSame(a: NewsArticle, b: NewsArticle) = a == b
        }
    }
}
