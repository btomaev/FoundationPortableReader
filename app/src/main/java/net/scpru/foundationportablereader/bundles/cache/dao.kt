package net.scpru.foundationportablereader.bundles.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import java.util.Date

@Dao
interface ArticleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<ArticleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Query("DELETE FROM article_tags WHERE articleId IN (:articleIds)")
    suspend fun deleteTagsForArticles(articleIds: List<Int>)

    @Transaction
    suspend fun updateCache(articles: List<ArticleEntity>, tags: List<TagEntity>) {
        val ids = articles.map { it.uid }
        deleteTagsForArticles(ids)
        insertArticles(articles)
        insertTags(tags)
    }

    @Transaction
    @Query("""
        SELECT DISTINCT a.* FROM articles a
        LEFT JOIN article_tags t ON a.uid = t.articleId
        WHERE 
            (:searchQuery IS NULL OR a.title LIKE '%' || :searchQuery || '%') 
            AND (:minRating IS NULL OR a.ratingValue >= :minRating)
            AND (:fromDate IS NULL OR a.createdAt >= :fromDate)
            AND (:toDate IS NULL OR a.createdAt <= :toDate)
            AND (:authorName IS NULL OR a.authorsJson LIKE '%' || :authorName || '%')
            AND (:tagCategory IS NULL OR t.category = :tagCategory)
            AND (:tagName IS NULL OR t.name = :tagName)
        ORDER BY a.createdAt DESC
    """)
    suspend fun getFilteredArticles(
        searchQuery: String? = null,
        minRating: Double? = null,
        fromDate: Date? = null,
        toDate: Date? = null,
        authorName: String? = null,
        tagCategory: String? = null,
        tagName: String? = null
    ): List<ArticleEntity>
}