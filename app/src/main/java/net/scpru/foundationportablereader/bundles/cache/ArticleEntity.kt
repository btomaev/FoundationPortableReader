package net.scpru.foundationportablereader.bundles.cache

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey
    val uid: Int,
    val pageId: String,
    val title: String,
    val canonicalUrl: String,

    val createdAt: Date,
    val updatedAt: Date,

    val ratingValue: Double,
    val ratingVotes: Int,

    val authorsJson: String
)