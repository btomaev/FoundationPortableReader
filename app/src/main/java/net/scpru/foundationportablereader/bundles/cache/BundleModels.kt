package net.scpru.foundationportablereader.bundles.cache

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class RegistryConfig(
    val version: String,
    val articleRegistries: List<ArticleRegistry>,
    val bundles: List<BundleItem>
)

data class ArticleRegistry(
    val name: String,
    val url: String,
    val internalDomains: List<String>
)

data class BundleItem(
    val name: String,
    val description: String? = null,
    val icon: String? = null,
    val lastUpdate: Long,
    val mode: BundleMode,

    val filters: Map<String, List<String>>? = null,
    val content: JsonElement? = null
)

enum class BundleMode {
    @SerializedName("dynamic") DYNAMIC,
    @SerializedName("latest") LATEST,
    @SerializedName("static") STATIC
}

data class StaticBundleContent(
    val name: String,
    val revision: Int
)