package org.telegram.decoy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class NewsAdapter(
    private var articles: List<NewsArticle> = emptyList()
) : RecyclerView.Adapter<NewsAdapter.ViewHolder>() {

    private var onItemClick: ((NewsArticle) -> Unit)? = null

    fun setOnItemClickListener(listener: (NewsArticle) -> Unit) {
        onItemClick = listener
    }

    fun updateArticles(newArticles: List<NewsArticle>) {
        articles = newArticles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_news_article, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val article = articles[position]
        holder.title.text = article.title
        holder.description.text = article.description
        holder.source.text = article.source

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

    override fun getItemCount() = articles.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.news_title)
        val description: TextView = itemView.findViewById(R.id.news_description)
        val source: TextView = itemView.findViewById(R.id.news_source)
        val image: ImageView = itemView.findViewById(R.id.news_image)
    }
}
