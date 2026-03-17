package com.dean.browservault

import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
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
        private val filePreview: ImageView = itemView.findViewById(R.id.filePreview)
        private val videoBadge: ImageView = itemView.findViewById(R.id.videoBadge)
        private val documentOverlay: LinearLayout = itemView.findViewById(R.id.documentOverlay)

        fun bind(file: File, onClick: (File) -> Unit, onLongClick: (File) -> Unit) {
            fileName.text = file.name

            when {
                FileUtils.isImageFile(file) -> bindImage(file)
                FileUtils.isVideoFile(file) -> bindVideo(file)
                else -> bindDocument(file)
            }

            itemView.setOnClickListener { onClick(file) }
            itemView.setOnLongClickListener {
                onLongClick(file)
                true
            }
        }

        private fun bindImage(file: File) {
            documentOverlay.visibility = View.GONE
            videoBadge.visibility = View.GONE
            filePreview.scaleType = ImageView.ScaleType.CENTER_CROP
            filePreview.setBackgroundColor(0x00000000)
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                filePreview.setImageBitmap(bitmap)
            } else {
                filePreview.setImageResource(R.drawable.ic_vault_image)
                filePreview.setBackgroundColor(0xFF1F281B.toInt())
                filePreview.scaleType = ImageView.ScaleType.CENTER
            }
        }

        private fun bindVideo(file: File) {
            documentOverlay.visibility = View.GONE
            videoBadge.visibility = View.VISIBLE
            filePreview.scaleType = ImageView.ScaleType.CENTER_CROP
            filePreview.setBackgroundColor(0x00000000)
            val thumbnail = ThumbnailUtils.createVideoThumbnail(
                file.absolutePath,
                MediaStore.Images.Thumbnails.MINI_KIND
            )
            if (thumbnail != null) {
                filePreview.setImageBitmap(thumbnail)
            } else {
                filePreview.setImageResource(R.drawable.ic_vault_video)
                filePreview.setBackgroundColor(0xFF1F281B.toInt())
                filePreview.scaleType = ImageView.ScaleType.CENTER
            }
        }

        private fun bindDocument(file: File) {
            documentOverlay.visibility = View.VISIBLE
            videoBadge.visibility = View.GONE
            filePreview.setImageDrawable(null)
            fileTypeIcon.setImageResource(R.drawable.ic_vault_file)
            filePreview.setBackgroundColor(0x00000000)
        }
    }
}
