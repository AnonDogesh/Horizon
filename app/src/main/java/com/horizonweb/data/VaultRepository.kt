package com.horizonweb.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Singleton
class VaultRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultFileDao: VaultFileDao
) {
    fun observeVaultFiles(): Flow<List<VaultFileEntity>> = vaultFileDao.observeVaultFiles()

    suspend fun importFile(uri: Uri): Long = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val originalName = readDisplayName(uri)
            ?: resolver.getType(uri)?.let { guessNameFromMime(it) }
            ?: (uri.lastPathSegment ?: "imported_file")
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"

        val tempFile = File.createTempFile("vault_import_", null, context.cacheDir)
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open source file" }
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }

        val encryptedFilePath = File(vaultDir(), "${UUID.randomUUID()}.vault")
        val encryptedFile = encryptedFile(encryptedFilePath)

        tempFile.inputStream().use { plainInput ->
            encryptedFile.openFileOutput().use { encryptedOutput ->
                plainInput.copyTo(encryptedOutput)
            }
        }

        tempFile.delete()

        vaultFileDao.insert(
            VaultFileEntity(
                encryptedPath = encryptedFilePath.absolutePath,
                originalName = originalName,
                fileType = mimeType,
                dateAdded = System.currentTimeMillis()
            )
        )
    }


    suspend fun importDownloadedFile(path: String, originalName: String, mimeType: String): Long =
        withContext(Dispatchers.IO) {
            val sourceFile = File(path)
            require(sourceFile.exists()) { "Downloaded file does not exist" }
            val encryptedFilePath = File(vaultDir(), "${UUID.randomUUID()}.vault")
            val encryptedFile = encryptedFile(encryptedFilePath)

            sourceFile.inputStream().use { plainInput ->
                encryptedFile.openFileOutput().use { encryptedOutput ->
                    plainInput.copyTo(encryptedOutput)
                }
            }

            sourceFile.delete()

            vaultFileDao.insert(
                VaultFileEntity(
                    encryptedPath = encryptedFilePath.absolutePath,
                    originalName = originalName,
                    fileType = mimeType,
                    dateAdded = System.currentTimeMillis()
                )
            )
        }

    suspend fun decryptToTempFile(file: VaultFileEntity): File = withContext(Dispatchers.IO) {
        val encryptedInputFile = File(file.encryptedPath)
        val ext = file.originalName.substringAfterLast('.', "tmp")
        val tempOutput = File.createTempFile("vault_view_${file.id}_", ".$ext", context.cacheDir)
        encryptedFile(encryptedInputFile).openFileInput().use { encryptedInput ->
            tempOutput.outputStream().use { output ->
                encryptedInput.copyTo(output)
            }
        }
        tempOutput
    }

    suspend fun loadThumbnail(file: VaultFileEntity, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.fileType.startsWith("image/")) {
            return@withContext null
        }

        val source = File(file.encryptedPath)
        encryptedFile(source).openFileInput().use { stream ->
            val bytes = stream.readBytes()
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            options.inSampleSize = calculateInSampleSize(options, sizePx, sizePx)
            options.inJustDecodeBounds = false
            return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }

    suspend fun deleteVaultFile(file: VaultFileEntity) = withContext(Dispatchers.IO) {
        File(file.encryptedPath).delete()
        vaultFileDao.deleteById(file.id)
    }

    fun deleteTempFile(path: String?) {
        if (path.isNullOrBlank()) return
        File(path).delete()
    }

    private fun encryptedFile(file: File): EncryptedFile {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    private fun vaultDir(): File {
        val dir = File(context.filesDir, "vault")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun readDisplayName(uri: Uri): String? {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }

    private fun guessNameFromMime(mimeType: String): String {
        val safeType = mimeType.substringAfter('/').ifBlank { "file" }
        return "imported_$safeType"
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
                halfHeight /= 2
                halfWidth /= 2
            }
        }

        return inSampleSize.coerceAtLeast(1)
    }
}
