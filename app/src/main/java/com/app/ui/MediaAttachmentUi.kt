package com.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.app.habits.MediaAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

fun isImageMedia(att: MediaAttachment): Boolean {
    return att.mimeType.startsWith("image/", ignoreCase = true) ||
        att.displayName.lowercase(Locale.ROOT).let {
            it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp")
        }
}

fun isAudioMedia(att: MediaAttachment): Boolean {
    return att.mimeType.startsWith("audio/", ignoreCase = true) ||
        att.displayName.lowercase(Locale.ROOT).let {
            it.endsWith(".mp3") || it.endsWith(".m4a") || it.endsWith(".wav") || it.endsWith(".ogg")
        }
}

fun isVideoMedia(att: MediaAttachment): Boolean {
    return att.mimeType.startsWith("video/", ignoreCase = true) ||
        att.displayName.lowercase(Locale.ROOT).let {
            it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".webm")
        }
}

fun importUriToDirectory(
    context: Context,
    uri: Uri,
    targetDir: File
): Result<MediaAttachment> = runCatching {
    if (!targetDir.exists()) targetDir.mkdirs()
    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
    var displayName = uri.lastPathSegment ?: "media_item"
    var size = 0L

    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIdx >= 0) {
                val qName = cursor.getString(nameIdx)
                if (!qName.isNullOrBlank()) displayName = qName
            }
            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                size = cursor.getLong(sizeIdx)
            }
        }
    }

    val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val id = UUID.randomUUID().toString()
    val dest = File(targetDir, "${id}_$safeName")

    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(dest).use { out ->
            val buf = ByteArray(8192)
            var total = 0L
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                out.write(buf, 0, read)
                total += read
            }
            if (size <= 0L) size = total
        }
    } ?: throw IllegalStateException("Unable to read media stream.")

    MediaAttachment(
        id = id,
        displayName = displayName,
        mimeType = mime,
        localPath = dest.absolutePath,
        sizeBytes = size
    )
}

fun openMediaAttachmentIntent(
    context: Context,
    att: MediaAttachment,
    onError: (String) -> Unit
) {
    val file = File(att.localPath)
    if (!file.exists()) {
        onError("Media file missing on device.")
        return
    }
    val res = runCatching {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val mime = att.mimeType.ifBlank { context.contentResolver.getType(uri) ?: "*/*" }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open ${att.displayName}"))
    }
    res.onFailure {
        onError("No app installed to play/view ${att.displayName}.")
    }
}

@Composable
fun InlineMediaImage(localPath: String, heightDp: Int = 150) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = localPath) {
        value = withContext(Dispatchers.IO) {
            val f = File(localPath)
            if (!f.exists()) return@withContext null
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(f.absolutePath, opts)?.asImageBitmap()
        }
    }
    bitmap?.let { img ->
        Image(
            bitmap = img,
            contentDescription = "Attached media image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    }
}

@Composable
fun MediaPickerStrip(
    targetDir: File,
    onMediaAdded: (MediaAttachment) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importUriToDirectory(context, uri, targetDir)
                .onSuccess { onMediaAdded(it) }
                .onFailure { onError(it.message ?: "Failed to attach media.") }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            "Photo" to arrayOf("image/*"),
            "Audio" to arrayOf("audio/*"),
            "Video" to arrayOf("video/*")
        ).forEach { (label, mimeFilter) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pressScale { picker.launch(mimeFilter) }
                    .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                    .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(10.dp))
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+ $label",
                    color = LairPalette.TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun MediaAttachmentCard(
    att: MediaAttachment,
    onOpen: (MediaAttachment) -> Unit,
    onRemove: (() -> Unit)? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val kindTag = when {
        isImageMedia(att) -> "PHOTO"
        isAudioMedia(att) -> "AUDIO"
        isVideoMedia(att) -> "VIDEO"
        else -> "FILE"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
            .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isImageMedia(att)) {
            InlineMediaImage(localPath = att.localPath)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "[$kindTag] ${att.displayName}",
                    color = LairPalette.TextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${att.mimeType} • ${att.sizeBytes} B",
                    color = LairPalette.TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .pressScale { onOpen(att) }
                        .background(primary, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isAudioMedia(att) || isVideoMedia(att)) "Play" else "Open",
                        color = onPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (onRemove != null) {
                    Box(
                        modifier = Modifier
                            .pressScale { onRemove() }
                            .background(LairPalette.SurfaceCard, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("✕", color = LairPalette.DangerRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
