package net.scpru.foundationportablereader.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import net.scpru.foundationportablereader.R
import net.scpru.foundationportablereader.bundles.cache.BundleItem

data class BundleDownloadState(
    val isDownloading: Boolean = false,
    val progress: Int = 0,
    val statusText: String = "",
    val isDownloaded: Boolean = false
)

class BundlesAdapter(
    private val bundles: List<BundleItem>,
    private val onActionClick: (BundleItem, Int) -> Unit
) : RecyclerView.Adapter<BundlesAdapter.BundleViewHolder>() {

    private val downloadStates = mutableMapOf<String, BundleDownloadState>()

    fun updateProgress(bundleName: String, progress: Int, status: String) {
        val currentState = downloadStates[bundleName] ?: BundleDownloadState()
        downloadStates[bundleName] = currentState.copy(
            isDownloading = true,
            progress = progress,
            statusText = status
        )
        val index = bundles.indexOfFirst { it.name == bundleName }
        if (index != -1) {
            notifyItemChanged(index, "PROGRESS_UPDATE")
        }
    }

    fun setDownloadComplete(bundleName: String) {
        downloadStates[bundleName] = BundleDownloadState(isDownloading = false, isDownloaded = true)
        val index = bundles.indexOfFirst { it.name == bundleName }
        if (index != -1) notifyItemChanged(index)
    }

    class BundleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.bundle_icon)
        val title: TextView = view.findViewById(R.id.bundle_title)
        val type: TextView = view.findViewById(R.id.bundle_type)
        val description: TextView = view.findViewById(R.id.bundle_description)
        val actionButton: ImageButton = view.findViewById(R.id.bundle_action_button)
        val progressBar: ProgressBar = view.findViewById(R.id.bundle_progress_bar)
        val statusText: TextView = view.findViewById(R.id.bundle_status_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BundleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bundle, parent, false)
        return BundleViewHolder(view)
    }

    override fun onBindViewHolder(holder: BundleViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads[0] == "PROGRESS_UPDATE") {
            val bundle = bundles[position]
            val state = downloadStates[bundle.name] ?: BundleDownloadState()
            bindProgress(holder, state)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: BundleViewHolder, position: Int) {
        val bundle = bundles[position]
        val state = downloadStates[bundle.name] ?: BundleDownloadState()

        holder.title.text = bundle.name
        holder.description.text = bundle.description ?: holder.itemView.context.getString(R.string.bundle_no_description)
        holder.type.text = bundle.mode.name

        // if (bundle.icon != null) holder.icon.load(bundle.icon)

        bindProgress(holder, state)

        holder.actionButton.setOnClickListener {
            if (!state.isDownloading) {
                onActionClick(bundle, position)
            } else {
                // TODO: Handle download cancellation
            }
        }
    }

    private fun bindProgress(holder: BundleViewHolder, state: BundleDownloadState) {
        if (state.isDownloading) {
            holder.progressBar.visibility = View.VISIBLE
            holder.statusText.visibility = View.VISIBLE
            holder.progressBar.progress = state.progress
            holder.statusText.text = state.statusText
            holder.actionButton.isEnabled = false
            holder.actionButton.alpha = 0.5f
        } else {
            holder.progressBar.visibility = View.GONE
            holder.statusText.visibility = View.GONE
            holder.actionButton.isEnabled = true
            holder.actionButton.alpha = 1.0f

            if (state.isDownloaded) {
                holder.actionButton.setImageResource(android.R.drawable.ic_menu_delete)
            } else {
                holder.actionButton.setImageResource(android.R.drawable.stat_sys_download)
            }
        }
    }

    override fun getItemCount() = bundles.size
}