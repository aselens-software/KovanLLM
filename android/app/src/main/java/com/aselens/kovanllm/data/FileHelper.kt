package com.aselens.kovanllm.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream

data class PreparedMedia(
    val isImage: Boolean,
    val fileName: String,
    val mimeType: String,
    val base64DataUri: String? = null,
    val textContent: String? = null
)

object FileHelper {

    fun getFileName(context: Context, uri: Uri): String {
        var name = "attachment"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex) ?: name
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return name
    }

    fun getMimeType(context: Context, uri: Uri, fallbackName: String): String {
        val mime = context.contentResolver.getType(uri)
        if (!mime.isNullOrBlank()) return mime
        val ext = fallbackName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "txt", "md", "csv", "json", "py", "kt", "java", "js", "ts", "html", "css", "xml", "yaml", "yml" -> "text/plain"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    fun processUri(context: Context, uri: Uri, providedName: String? = null): PreparedMedia? {
        val name = providedName ?: getFileName(context, uri)
        val mime = getMimeType(context, uri, name)

        val isImg = mime.startsWith("image/") || listOf("jpg", "jpeg", "png", "webp", "gif").contains(name.substringAfterLast('.', "").lowercase())

        return try {
            if (isImg) {
                // Decode and downscale to max 1024px to keep network payload light (~100-200 KB)
                // This prevents Cloudflare Quick Tunnel 530 / Error 1033 drops and memory spikes
                val inputStream = context.contentResolver.openInputStream(uri) ?: return null
                val bytes = inputStream.use { it.readBytes() }

                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

                val targetMaxDim = 1024
                val largestDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
                var sampleSize = 1
                while (largestDim / (sampleSize * 2) >= targetMaxDim) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                    ?: return null

                val curW = bitmap.width
                val curH = bitmap.height
                if (curW > targetMaxDim || curH > targetMaxDim) {
                    val ratio = if (curW > curH) targetMaxDim.toFloat() / curW else targetMaxDim.toFloat() / curH
                    val newW = (curW * ratio).toInt().coerceAtLeast(1)
                    val newH = (curH * ratio).toInt().coerceAtLeast(1)
                    bitmap = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
                }

                val baos = ByteArrayOutputStream()
                // Use JPEG 80 for universal LLM vision compatibility and compact size
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val finalBytes = baos.toByteArray()
                val b64 = Base64.encodeToString(finalBytes, Base64.NO_WRAP)
                val imgMime = "image/jpeg"

                PreparedMedia(
                    isImage = true,
                    fileName = name,
                    mimeType = imgMime,
                    base64DataUri = "data:$imgMime;base64,$b64"
                )
            } else {
                // Read text / document / code
                val inputStream = context.contentResolver.openInputStream(uri) ?: return null
                val text = inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    // Read up to 64 KB
                    val buffer = CharArray(65536)
                    val readChars = reader.read(buffer, 0, 65536)
                    if (readChars > 0) String(buffer, 0, readChars) else ""
                }

                PreparedMedia(
                    isImage = false,
                    fileName = name,
                    mimeType = mime,
                    textContent = text
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
