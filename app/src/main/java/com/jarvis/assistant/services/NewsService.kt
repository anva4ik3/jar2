package com.jarvis.assistant.services

import com.jarvis.assistant.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NewsService {

    data class NewsArticle(
        val title: String,
        val description: String,
        val source: String,
        val url: String
    )

    suspend fun getNews(category: String = "general", callback: (List<NewsArticle>) -> Unit) {
        try {
            val articles = listOf(
                NewsArticle(
                    title = "AI Technology Advances",
                    description = "Latest developments in artificial intelligence",
                    source = "Tech News",
                    url = "https://example.com/news1"
                ),
                NewsArticle(
                    title = "Smart Home Innovations",
                    description = "New smart home devices and automation",
                    source = "Home Tech",
                    url = "https://example.com/news2"
                )
            )
            withContext(Dispatchers.Main) {
                callback(articles)
            }
            Logger.d("News service: Retrieved $category news")
        } catch (e: Exception) {
            Logger.e("News service error: ${e.message}")
        }
    }

    fun getLatestNews(callback: (List<NewsArticle>) -> Unit) {
        val articles = listOf(
            NewsArticle(
                title = "Breaking News",
                description = "Latest news update",
                source = "News",
                url = "https://example.com"
            )
        )
        callback(articles)
    }
}
