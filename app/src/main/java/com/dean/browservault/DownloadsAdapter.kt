package com.dean.browservault

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DownloadsAdapter(
    private val onFileClicked: (File) -> Unit,
    private val onFileLongClicked: (File) -> Unit,
    private val onMoreClicked: (View, File) -> Unit
) : RecyclerView.Adapter<DownloadsAdapter.DownloadViewHolder>() {

    private val files = mutableListOf<File>()
    private val selectedPaths = mutableSetOf<String>()

    fun submitList(items: List<File>) {
        files.clear()
        files.addAll(items)
        notifyDataSetChanged()
    }

    fun setSelections(selected: Set<String>) {
        selectedPaths.clear()
        selectedPaths.addAll(selected)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download_file, parent, false)
        return DownloadViewHolder(view)
    }

    override fun getItemCount(): Int = files.size

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(files[position], selectedPaths.contains(files[position].absolutePath))
    }

    inner class DownloadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rowRoot: LinearLayout = itemView.findViewById(R.id.rowRoot)
        private val fileName: TextView = itemView.findViewById(R.id.fileName)
        private val fileMeta: TextView = itemView.findViewById(R.id.fileMeta)
        private val fileIcon: ImageView = itemView.findViewById(R.id.fileIcon)
        private val buttonMore: ImageButton = itemView.findViewById(R.id.buttonMore)

        fun bind(file: File, selected: Boolean) {
            fileName.text = file.name
            val date = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(file.lastModified()))
            fileMeta.text = "${readableSize(file.length())} • $date"

            fileIcon.setImageResource(
                when {
                    FileUtils.isImageFile(file) -> R.drawable.ic_vault_image
                    FileUtils.isVideoFile(file) -> R.drawable.ic_vault_video
                    else -> R.drawable.ic_vault_file
                }
            )

            rowRoot.setBackgroundColor(if (selected) 0x223A4A2D else 0x00000000)
            itemView.setOnClickListener { onFileClicked(file) }
            itemView.setOnLongClickListener {
                onFileLongClicked(file)
                true
            }
            buttonMore.setOnClickListener { onMoreClicked(it, file) }
        }

        private fun readableSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
            val gb = mb / 1024.0
            return String.format(Locale.US, "%.1f GB", gb)
        }
    }
}
