package net.scpru.foundationportablereader.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.scpru.foundationportablereader.MainActivity
import net.scpru.foundationportablereader.R
import net.scpru.foundationportablereader.bundles.cache.RegistryRepository

class SettingsFragment : Fragment() {
    private lateinit var changeHomeFolderButton: Button
    private lateinit var addRegistryButton: Button
    private lateinit var recyclerView: RecyclerView

    private lateinit var repository: RegistryRepository
    private lateinit var adapter: RegistryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = RegistryRepository(requireContext())

        changeHomeFolderButton = view.findViewById(R.id.change_home_folder_button)
        addRegistryButton = view.findViewById(R.id.add_registry_button)
        recyclerView = view.findViewById(R.id.registries_recycler_view)

        setupFragment()
        setupRecyclerView()
    }

    private fun setupFragment() {
        changeHomeFolderButton.setOnClickListener {
            (requireActivity() as MainActivity).rebindStorage()
        }

        addRegistryButton.setOnClickListener {
            showAddRegistryDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = RegistryAdapter(repository.getRegistryUrls()) { urlToDelete ->
            repository.removeUrl(urlToDelete)
            adapter.updateData(repository.getRegistryUrls())
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    private fun showAddRegistryDialog() {
        val input = EditText(context)
        input.hint = "https://..."

        AlertDialog.Builder(context)
            .setTitle("Добавить реестр")
            .setView(input)
            .setPositiveButton("Добавить") { _, _ ->
                val url = input.text.toString()
                if (url.isNotBlank() && url.startsWith("http")) {
                    repository.addUrl(url)
                    adapter.updateData(repository.getRegistryUrls())
                } else {
                    Toast.makeText(context, "Некорректная ссылка", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    class RegistryAdapter(
        private var urls: MutableList<String>,
        private val onDelete: (String) -> Unit
    ) : RecyclerView.Adapter<RegistryAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val urlText: TextView = view.findViewById(R.id.registry_url_text)
            val deleteBtn: ImageButton = view.findViewById(R.id.delete_registry_button)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_registry, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val url = urls[position]
            holder.urlText.text = url
            holder.deleteBtn.setOnClickListener { onDelete(url) }
        }

        override fun getItemCount() = urls.size

        fun updateData(newUrls: MutableList<String>) {
            urls = newUrls
            notifyDataSetChanged()
        }
    }
}