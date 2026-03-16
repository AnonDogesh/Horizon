package com.dean.browservault

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object FileUtils {

    private const val VAULT_FOLDER_NAME = ".vault"
    private const val NO_MEDIA_FILE = ".nomedia"

    fun ensureVaultDirectory(context: Context): File {
        val root = context.getExternalFilesDir(null)
            ?: throw IllegalStateException("External files directory unavailable")
        val vaultDirectory = File(root, VAULT_FOLDER_NAME)
        if (!vaultDirectory.exists()) {
            vaultDirectory.mkdirs()
        }

        val noMedia = File(vaultDirectory, NO_MEDIA_FILE)
        if (!noMedia.exists()) {
            noMedia.createNewFile()
        }

        return vaultDirectory
    }

    fun copyUriToVault(context: Context, sourceUri: Uri): File {
        val vaultDirectory = ensureVaultDirectory(context)
        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalArgumentException("Unable to read selected file")

        val originalName = queryDisplayName(context, sourceUri)
        val safeName = sanitizeName(originalName ?: "file_${System.currentTimeMillis()}")
        val destination = uniqueDestination(vaultDirectory, safeName)

        inputStream.use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
        return destination
    }

    fun listVaultFiles(context: Context): List<File> {
        val vaultDirectory = ensureVaultDirectory(context)
        return vaultDirectory
            .listFiles()
            .orEmpty()
            .filter { file -> file.isFile && file.name != NO_MEDIA_FILE }
            .sortedByDescending { file -> file.lastModified() }
    }

    fun isImageFile(file: File): Boolean {
        val extension = file.extension.lowercase(Locale.US)
        return extension in setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    }

    fun isVideoFile(file: File): Boolean {
        val extension = file.extension.lowercase(Locale.US)
        return extension in setOf("mp4", "m3u8", "webm", "mkv")
    }

    fun guessMimeType(file: File): String {
        val extension = file.extension.lowercase(Locale.US)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: if (isImageFile(file)) "image/*" else if (isVideoFile(file)) "video/*" else "*/*"
    }

    private fun sanitizeName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    private fun uniqueDestination(directory: File, baseName: String): File {
        var candidate = File(directory, baseName)
        if (!candidate.exists()) return candidate

        val dotIndex = baseName.lastIndexOf('.')
        val prefix = if (dotIndex > 0) baseName.substring(0, dotIndex) else baseName
        val suffix = if (dotIndex > 0) baseName.substring(dotIndex) else ""

        var counter = 1
        while (candidate.exists()) {
            candidate = File(directory, "${prefix}_$counter$suffix")
            counter++
        }
        return candidate
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx)
            }
        }
        return null
    }
}
