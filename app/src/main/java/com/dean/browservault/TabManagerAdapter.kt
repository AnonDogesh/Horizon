package com.dean.browservault

import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class TabManagerAdapter(
    private val onTabClick: (String) -> Unit,
    private val onCloseTab: (String) -> Unit,
    private val onNewTabClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val urls = mutableListOf<String>()

    fun submitTabs(values: List<String>) {
        urls.clear()
        urls.addAll(values)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < urls.size) VIEW_TAB else VIEW_NEW_TAB
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TAB) {
            val view = inflater.inflate(R.layout.item_tab_preview, parent, false)
            TabViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_tab_new, parent, false)
            NewTabViewHolder(view)
        }
    }

    override fun getItemCount(): Int = urls.size + 1

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is TabViewHolder) {
            holder.bind(urls[position], onTabClick, onCloseTab)
        } else if (holder is NewTabViewHolder) {
            holder.itemView.setOnClickListener { onNewTabClick() }
        }
    }

    private class NewTabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val previewTint: LinearLayout = itemView.findViewById(R.id.previewTint)
        private val textInitial: TextView = itemView.findViewById(R.id.textInitial)
        private val textTitle: TextView = itemView.findViewById(R.id.textTitle)
        private val textSubtitle: TextView = itemView.findViewById(R.id.textSubtitle)
        private val closeButton: ImageButton = itemView.findViewById(R.id.buttonCloseTab)

        fun bind(url: String, onClick: (String) -> Unit, onClose: (String) -> Unit) {
            val host = Uri.parse(url).host.orEmpty().ifBlank { url }
            val title = host.substringBefore('.').replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
            }

            textInitial.text = title.take(1).ifBlank { "?" }
            textTitle.text = title
            textSubtitle.text = host
            previewTint.setBackgroundColor(colorFromHost(host))

            itemView.setOnClickListener { onClick(url) }
            closeButton.setOnClickListener { onClose(url) }
        }

        private fun colorFromHost(host: String): Int {
            val palette = intArrayOf(
                Color.parseColor("#4E5554"),
                Color.parseColor("#2B3337"),
                Color.parseColor("#564034"),
                Color.parseColor("#37463E"),
                Color.parseColor("#344F56")
            )
            return palette[kotlin.math.abs(host.hashCode()) % palette.size]
        }
    }

    companion object {
        private const val VIEW_TAB = 1
        private const val VIEW_NEW_TAB = 2
    }
}
