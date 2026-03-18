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
    private val onTabClick: (TabSession) -> Unit,
    private val onCloseTab: (TabSession) -> Unit,
    private val onNewTabClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val sessions = mutableListOf<TabSession>()

    fun submitTabs(values: List<TabSession>) {
        sessions.clear()
        sessions.addAll(values)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < sessions.size) VIEW_TAB else VIEW_NEW_TAB
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

    override fun getItemCount(): Int = sessions.size + 1

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is TabViewHolder) {
            holder.bind(sessions[position], onTabClick, onCloseTab)
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

        fun bind(session: TabSession, onClick: (TabSession) -> Unit, onClose: (TabSession) -> Unit) {
            val host = Uri.parse(session.url).host.orEmpty().ifBlank { session.url }
            val title = host.substringBefore('.').replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
            }

            textInitial.text = title.take(1).ifBlank { "?" }
            textTitle.text = title
            textSubtitle.text = if (session.desktopMode) "$host • ${itemView.context.getString(R.string.action_desktop_site)}" else host
            previewTint.setBackgroundColor(colorFromHost(host))

            itemView.setOnClickListener { onClick(session) }
            closeButton.setOnClickListener { onClose(session) }
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
