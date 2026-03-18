package com.dean.browservault

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class SavedSitesAdapter(
    private val onSiteClicked: (String) -> Unit,
    private val onSelectionChanged: (String) -> Unit,
    private val onDeleteClicked: (String) -> Unit
) : RecyclerView.Adapter<SavedSitesAdapter.SavedSiteViewHolder>() {

    private val urls = mutableListOf<String>()
    private val selectedUrls = mutableSetOf<String>()

    fun submitList(items: List<String>) {
        urls.clear()
        urls.addAll(items)
        notifyDataSetChanged()
    }

    fun setSelections(selected: Set<String>) {
        selectedUrls.clear()
        selectedUrls.addAll(selected)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedSiteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_site, parent, false)
        return SavedSiteViewHolder(view)
    }

    override fun getItemCount(): Int = urls.size

    override fun onBindViewHolder(holder: SavedSiteViewHolder, position: Int) {
        val url = urls[position]
        holder.bind(url, selectedUrls.contains(url))
    }

    inner class SavedSiteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rowRoot: LinearLayout = itemView.findViewById(R.id.rowRoot)
        private val textTitle: TextView = itemView.findViewById(R.id.textTitle)
        private val textUrl: TextView = itemView.findViewById(R.id.textUrl)
        private val checkSelected: CheckBox = itemView.findViewById(R.id.checkSelected)
        private val buttonDelete: ImageButton = itemView.findViewById(R.id.buttonDelete)

        fun bind(url: String, selected: Boolean) {
            val host = Uri.parse(url).host.orEmpty().ifBlank { url }
            val title = host.substringBefore('.').replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
            }.ifBlank { url }

            textTitle.text = title
            textUrl.text = url
            checkSelected.isChecked = selected
            rowRoot.setBackgroundColor(if (selected) 0x223A4A2D else 0x00000000)

            itemView.setOnClickListener { onSiteClicked(url) }
            checkSelected.setOnClickListener { onSelectionChanged(url) }
            rowRoot.setOnLongClickListener {
                onSelectionChanged(url)
                true
            }
            buttonDelete.setOnClickListener { onDeleteClicked(url) }
        }
    }
}
