package com.dean.browservault

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class VaultAdapter(
    private val onFileClick: (File) -> Unit,
    private val onFileLongClick: (File) -> Unit
) : RecyclerView.Adapter<VaultAdapter.VaultViewHolder>() {

    private val files = mutableListOf<File>()

    fun submitList(items: List<File>) {
        files.clear()
        files.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VaultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vault_file, parent, false)
        return VaultViewHolder(view)
    }

    override fun getItemCount(): Int = files.size

    override fun onBindViewHolder(holder: VaultViewHolder, position: Int) {
        holder.bind(files[position], onFileClick, onFileLongClick)
    }

    class VaultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileName: TextView = itemView.findViewById(R.id.fileName)
        private val fileTypeIcon: ImageView = itemView.findViewById(R.id.fileTypeIcon)

        fun bind(file: File, onClick: (File) -> Unit, onLongClick: (File) -> Unit) {
            fileName.text = file.name
            fileTypeIcon.setImageResource(
                when {
                    FileUtils.isImageFile(file) -> R.drawable.ic_vault_image
                    FileUtils.isVideoFile(file) -> R.drawable.ic_vault_video
                    else -> R.drawable.ic_vault_file
                }
            )

            itemView.setOnClickListener { onClick(file) }
            itemView.setOnLongClickListener {
                onLongClick(file)
                true
            }
        }
    }
}
