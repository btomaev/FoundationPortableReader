package net.scpru.foundationportablereader.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.scpru.foundationportablereader.MainActivity
import net.scpru.foundationportablereader.PythonInterface
import net.scpru.foundationportablereader.R
import net.scpru.foundationportablereader.adapters.BundlesAdapter
import net.scpru.foundationportablereader.bundles.cache.AppDatabase
import net.scpru.foundationportablereader.bundles.cache.ArticleEntity
import net.scpru.foundationportablereader.bundles.cache.BundleItem
import net.scpru.foundationportablereader.bundles.cache.RegistryConfig
import net.scpru.foundationportablereader.bundles.cache.RegistryRepository
import net.scpru.foundationportablereader.bundles.cache.StaticBundleContent
import net.scpru.foundationportablereader.bundles.cache.TagEntity
import net.scpru.foundationportablereader.bundles.cache.ArticleRegistry
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class BundlesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: BundlesAdapter

    private val gson = Gson()
    private val client = OkHttpClient()
    private lateinit var repository: RegistryRepository

    private val db by lazy { AppDatabase.getDatabase(requireContext()) }

    private val pythonInterface: PythonInterface by lazy {
        (requireActivity() as MainActivity).pythonInterface
    }

    @Volatile
    private var isStopRequested = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_bundles, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = RegistryRepository(requireContext())

        recyclerView = view.findViewById(R.id.bundles_recycler_view)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        progressBar = view.findViewById(R.id.loading_progress)

        recyclerView.layoutManager = LinearLayoutManager(context)

        swipeRefreshLayout.setOnRefreshListener {
            loadBundles()
        }

        loadBundles(isFirstLoad = true)
    }

    private fun loadBundles(isFirstLoad: Boolean = false) {
        if (isFirstLoad) progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val urls = repository.getRegistryUrls()
            val allBundles = mutableListOf<BundleItem>()
            val allArticleRegistries = mutableListOf<ArticleRegistry>()
            var errors = 0

            for (url in urls) {
                try {
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (jsonString != null) {
                            val config = gson.fromJson(jsonString, RegistryConfig::class.java)
                            allBundles.addAll(config.bundles)
                            allArticleRegistries.addAll(config.articleRegistries)
                        }
                    } else { errors++ }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errors++
                }
            }

            if (allArticleRegistries.isNotEmpty()) {
                syncArticlesCache(allArticleRegistries)
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                setupAdapter(allBundles)
                if (errors > 0 && urls.isNotEmpty()) {
                    Toast.makeText(context, "Ошибки при обновлении ($errors)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun syncArticlesCache(registries: List<ArticleRegistry>) {
        val allArticles = mutableListOf<ArticleEntity>()
        val allTags = mutableListOf<TagEntity>()

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        for (registry in registries) {
            try {
                val request = Request.Builder().url(registry.url).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val jsonString = response.body?.string() ?: continue

                    val type = object : TypeToken<List<ArticleApiDto>>() {}.type
                    val dtoList: List<ArticleApiDto> = gson.fromJson(jsonString, type)

                    dtoList.forEach { dto ->
                        val createdAtDate = try { isoFormat.parse(dto.createdAt.substring(0, 19)) ?: Date() } catch (e: Exception) { Date() }
                        val updatedAtDate = try { isoFormat.parse(dto.updatedAt.substring(0, 19)) ?: Date() } catch (e: Exception) { Date() }

                        val article = ArticleEntity(
                            uid = dto.uid,
                            pageId = dto.pageId,
                            title = dto.title,
                            canonicalUrl = dto.canonicalUrl,
                            createdAt = createdAtDate,
                            updatedAt = updatedAtDate,
                            ratingValue = dto.rating.value,
                            ratingVotes = dto.rating.votes,
                            authorsJson = gson.toJson(dto.authors ?: emptyList<AuthorApiDto>())
                        )
                        allArticles.add(article)

                        val tags = dto.tags.map { rawTag ->
                            val parts = rawTag.split(":", limit = 2)
                            TagEntity(
                                articleId = dto.uid,
                                fullTag = rawTag,
                                category = if (parts.size == 2) parts[0] else null,
                                name = if (parts.size == 2) parts[1] else parts[0]
                            )
                        }
                        allTags.addAll(tags)
                    }
                }
            } catch (e: Exception) {
                Log.e("BundlesFragment", "Ошибка загрузки кэша статей: ${e.message}")
            }
        }

        if (allArticles.isNotEmpty()) {
            db.articleDao().updateCache(allArticles, allTags)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Кэш статей обновлен", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupAdapter(bundles: List<BundleItem>) {
        adapter = BundlesAdapter(bundles) { clickedBundle, _ ->
            lifecycleScope.launch {
                handleBundleAction(clickedBundle)
            }
        }
        recyclerView.adapter = adapter
    }

    private suspend fun handleBundleAction(bundle: BundleItem) {
        val pagesToDownload = withContext(Dispatchers.IO) {
            parsePagesFromBundle(bundle)
        }

        if (pagesToDownload.isEmpty()) {
            val msg = if (bundle.mode.name == "DYNAMIC")
                "Не найдено статей по фильтрам"
            else
                "Бандл пуст"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            return
        }

        startDownload(bundle.name, pagesToDownload)
    }

    private suspend fun parsePagesFromBundle(bundle: BundleItem): List<String> {
        return try {
            when (bundle.mode.name) {
                "LATEST" -> {
                    val type = object : TypeToken<List<String>>() {}.type
                    gson.fromJson(bundle.content, type) ?: emptyList()
                }
                "STATIC" -> {
                    val type = object : TypeToken<List<StaticBundleContent>>() {}.type
                    val items: List<StaticBundleContent> = gson.fromJson(bundle.content, type) ?: emptyList()
                    items.map { it.name }
                }
                "DYNAMIC" -> {
                    val filters = bundle.filters ?: return emptyList()

                    var tagCategory: String? = null
                    var tagName: String? = null
                    var authorName: String? = null
                    var minRating: Double? = null

                    filters["tags"]?.firstOrNull()?.let { fullTag ->
                        if (fullTag.contains(":")) {
                            val parts = fullTag.split(":", limit = 2)
                            tagCategory = parts[0]
                            tagName = parts[1]
                        } else {
                            tagName = fullTag
                        }
                    }

                    filters["author"]?.firstOrNull()?.let {
                        authorName = it
                    }

                    filters["rating"]?.firstOrNull()?.let { ratingStr ->
                        minRating = ratingStr.replace(">", "").toDoubleOrNull()
                    }

                    val articles = db.articleDao().getFilteredArticles(
                        tagCategory = tagCategory,
                        tagName = tagName,
                        authorName = authorName,
                        minRating = minRating
                    )

                    articles.map { it.pageId }
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun startDownload(bundleName: String, pages: List<String>) {
        isStopRequested = false

        lifecycleScope.launch(Dispatchers.IO) {

            withContext(Dispatchers.Main) {
                adapter.updateProgress(bundleName, 0, "Подготовка...")
            }

            try {
                pythonInterface.importPages(pages) { current, fetched, imported, failed, total ->
                    importProgressCallback(bundleName, current, fetched, imported, failed, total)
                }

                withContext(Dispatchers.Main) {
                    adapter.setDownloadComplete(bundleName)
                    Toast.makeText(context, "Загрузка $bundleName завершена", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    adapter.setDownloadComplete(bundleName)
                }
            }
        }
    }

    fun importProgressCallback(bundleName: String, current: String?, fetched: Int, imported: Int, failed: Int, total: Int): Boolean {
        if (isStopRequested) return true

        lifecycleScope.launch(Dispatchers.Main) {
            val progress = if (total > 0) ((fetched+imported) * 100) / (total*2) else 0
            val statusText = "${if (fetched < total) "Загрузка" else "Импорт"}: ${current?.take(30) ?: "..."} (${(fetched+imported)%total}/$total)"

            adapter.updateProgress(bundleName, progress, statusText)
        }

        return false
    }

    private data class ArticleApiDto(
        val uid: Int,
        val pageId: String,
        val title: String,
        val canonicalUrl: String,
        val createdAt: String,
        val updatedAt: String,
        val tags: List<String>,
        val rating: RatingApiDto,
        val authors: List<AuthorApiDto>?
    )

    private data class RatingApiDto(val value: Double, val votes: Int)
    private data class AuthorApiDto(val name: String, val username: String)
}