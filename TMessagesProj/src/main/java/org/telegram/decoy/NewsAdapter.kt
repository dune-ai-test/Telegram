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

class NewsAdapter : ListAdapter<NewsListItem, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val VIEW_TYPE_ARTICLE = 0
        private const val VIEW_TYPE_HEADER = 1

        private val DIFF = object : DiffUtil.ItemCallback<NewsListItem>() {
            override fun areItemsTheSame(a: NewsListItem, b: NewsListItem): Boolean {
                return when {
                    a is NewsListItem.Article && b is NewsListItem.Article -> a.data.link == b.data.link
                    a is NewsListItem.Header && b is NewsListItem.Header -> a.source == b.source
                    else -> false
                }
            }

            override fun areContentsTheSame(a: NewsListItem, b: NewsListItem): Boolean = a == b
        }
    }

    private var onItemClick: ((NewsArticle) -> Unit)? = null
    private var onHeaderClick: ((String) -> Unit)? = null

    fun setOnItemClickListener(listener: (NewsArticle) -> Unit) {
        onItemClick = listener
    }

    fun setOnHeaderClickListener(listener: ((String) -> Unit)?) {
        onHeaderClick = listener
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is NewsListItem.Article -> VIEW_TYPE_ARTICLE
            is NewsListItem.Header -> VIEW_TYPE_HEADER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ARTICLE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_news_article, parent, false)
                ArticleViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_news_group, parent, false)
                HeaderViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is NewsListItem.Article -> (holder as ArticleViewHolder).bind(item.data)
            is NewsListItem.Header -> (holder as HeaderViewHolder).bind(item)
        }
    }

    inner class ArticleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val number: TextView = itemView.findViewById(R.id.news_number)
        val title: TextView = itemView.findViewById(R.id.news_title)
        val description: TextView = itemView.findViewById(R.id.news_description)
        val source: TextView = itemView.findViewById(R.id.news_source)
        val time: TextView = itemView.findViewById(R.id.news_time)
        val image: ImageView = itemView.findViewById(R.id.news_image)

        fun bind(article: NewsArticle) {
            number.text = "${layoutPosition + 1}"
            title.text = article.title
            description.text = article.description
            source.text = article.source
            time.text = formatTime(itemView.context, article)

            if (!article.imageUrl.isNullOrBlank()) {
                image.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(article.imageUrl)
                    .centerCrop()
                    .into(image)
            } else {
                image.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onItemClick?.invoke(article)
            }
        }
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val sourceText: TextView = itemView.findViewById(R.id.group_source)
        val countText: TextView = itemView.findViewById(R.id.group_count)

        fun bind(header: NewsListItem.Header) {
            sourceText.text = header.source
            countText.text = "${header.count} articles"
            itemView.setOnClickListener {
                onHeaderClick?.invoke(header.source)
            }
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
}
