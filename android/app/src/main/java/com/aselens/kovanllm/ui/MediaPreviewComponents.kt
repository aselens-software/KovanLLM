package com.aselens.kovanllm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aselens.kovanllm.data.FileHelper
import com.aselens.kovanllm.data.I18n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberImageBitmapFromUri(context: Context, uriString: String?): ImageBitmap? {
    return produceState<ImageBitmap?>(initialValue = null, uriString) {
        if (uriString.isNullOrBlank()) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                val bytes = inputStream.use { it.readBytes() }
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                val maxDim = 800
                var sampleSize = 1
                val width = options.outWidth
                val height = options.outHeight
                if (width > maxDim || height > maxDim) {
                    val halfW = width / 2
                    val halfH = height / 2
                    while ((halfW / sampleSize) >= maxDim && (halfH / sampleSize) >= maxDim) {
                        sampleSize *= 2
                    }
                }
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                bitmap?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }.value
}

/**
 * WhatsApp-style Rich Media Attachment Card
 * Handles both visual media (Images) and document files (Code, Text, PDF, JSON).
 */
@Composable
fun WhatsAppMediaAttachment(
    mediaUri: String?,
    mediaName: String,
    isUser: Boolean,
    language: String
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val ext = mediaName.substringAfterLast('.', "").lowercase()
    val isImage = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp").contains(ext)

    val imageBitmap = if (isImage) rememberImageBitmapFromUri(context, mediaUri) else null

    var showFullscreenImage by remember { mutableStateOf(false) }
    var showDocumentPreview by remember { mutableStateOf(false) }

    if (isImage) {
        // WhatsApp-style Image Bubble
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 220.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, if (isUser) Color(0x44FFFFFF) else colors.outline, RoundedCornerShape(10.dp))
                    .clickable { showFullscreenImage = true },
                contentAlignment = Alignment.Center
            ) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = mediaName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = colors.primary
                    )
                }
            }
        }

        if (showFullscreenImage && imageBitmap != null) {
            FullscreenImageViewer(
                bitmap = imageBitmap,
                title = mediaName,
                onDismiss = { showFullscreenImage = false }
            )
        }
    } else {
        // WhatsApp-style Document Card
        val badgeColor = when (ext) {
            "pdf" -> Color(0xFFEF4444) // Red
            "py", "kt", "java", "js", "ts", "html", "css", "c", "cpp", "rs", "go" -> Color(0xFF3B82F6) // Blue
            "json", "yaml", "yml", "xml", "csv" -> Color(0xFF10B981) // Green
            else -> Color(0xFFF59E0B) // Amber
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isUser) Color(0x2AFFFFFF) else colors.primaryContainer)
                .border(1.dp, if (isUser) Color(0x44FFFFFF) else colors.outline, RoundedCornerShape(10.dp))
                .clickable { showDocumentPreview = true }
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Colored File Extension Badge Box
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ext.uppercase().take(4),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mediaName,
                        color = if (isUser) Color.White else colors.onPrimaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (language == "tr") "Doküman • Önizlemek için dokunun" else "Document • Tap to preview",
                        color = if (isUser) Color.White.copy(alpha = 0.7f) else colors.onPrimaryContainer.copy(alpha = 0.65f),
                        fontSize = 10.sp
                    )
                }

                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "Preview",
                    tint = if (isUser) Color.White.copy(alpha = 0.8f) else colors.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (showDocumentPreview) {
            DocumentContentPreviewDialog(
                uriString = mediaUri,
                fileName = mediaName,
                language = language,
                onDismiss = { showDocumentPreview = false }
            )
        }
    }
}

/**
 * Fullscreen Image Viewer Modal
 */
@Composable
fun FullscreenImageViewer(
    bitmap: ImageBitmap,
    title: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = bitmap,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Bar with Close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x66000000))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * WhatsApp-style Document Content Viewer Dialog
 */
@Composable
fun DocumentContentPreviewDialog(
    uriString: String?,
    fileName: String,
    language: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val textContent by produceState(initialValue = "Belge yükleniyor...") {
        value = withContext(Dispatchers.IO) {
            if (uriString.isNullOrBlank()) {
                return@withContext "Dosya yolu bulunamadı."
            }
            try {
                val prepared = FileHelper.processUri(context, Uri.parse(uriString), fileName)
                prepared?.textContent?.ifBlank { "[Dosya içeriği boş veya metin formatında değil]" }
                    ?: "[Dosya içeriği okunamadı]"
            } catch (e: Exception) {
                "Hata: ${e.localizedMessage}"
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 32.dp),
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = fileName,
                            color = colors.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("KovanLLM File", textContent)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, I18n.t("copied", language), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = colors.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = colors.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = colors.outline.copy(alpha = 0.5f))

                // Scrollable text content area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.primaryContainer)
                        .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = textContent,
                        color = colors.onPrimaryContainer,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 17.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text(text = if (language == "tr") "Kapat" else "Close", color = Color.White)
                }
            }
        }
    }
}
