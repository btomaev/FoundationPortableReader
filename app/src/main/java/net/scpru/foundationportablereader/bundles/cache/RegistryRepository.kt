package net.scpru.foundationportablereader.bundles.cache

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.content.edit

class RegistryRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("scp_registries", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val KEY_URLS = "registry_urls"

    private val defaultUrl = "https://files.scpfoundation.net/local--files/draft:fprd-test/bundles-registry-scheme.json"

    fun getRegistryUrls(): MutableList<String> {
        val json = prefs.getString(KEY_URLS, null) ?: return mutableListOf(defaultUrl)
        val type = object : TypeToken<MutableList<String>>() {}.type
        return gson.fromJson(json, type)
    }

    fun addUrl(url: String) {
        val list = getRegistryUrls()
        if (!list.contains(url)) {
            list.add(url)
            saveList(list)
        }
    }

    fun removeUrl(url: String) {
        val list = getRegistryUrls()
        list.remove(url)
        saveList(list)
    }

    private fun saveList(list: List<String>) {
        prefs.edit { putString(KEY_URLS, gson.toJson(list)) }
    }
}