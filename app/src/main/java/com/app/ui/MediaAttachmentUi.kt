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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.app.habits.MediaAttachment
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

fun importUriToDirectory(context: Context, uri: Uri, targetDir: File): Result<MediaAttachment> {
    return runCatching {
        if (!targetDir.exists()) targetDir.mkdirs()
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        var name = "media_${UUID.randomUUID().toString().take(8)}"
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) {
                    val rawName = cursor.getString(nameIdx)
                    if (!rawName.isNullOrBlank()) name = rawName
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    size = cursor.getLong(sizeIdx)
                }
            }
        }
        val safeName = "${UUID.randomUUID().toString().take(8)}_${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}"
        val destFile = File(targetDir, safeName)
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to read selected file.")
        if (size <= 0L) size = destFile.length()
        MediaAttachment(
            id = UUID.randomUUID().toString(),
            displayName = name,
            mimeType = mime,
            localPath = destFile.absolutePath,
            sizeBytes = size
        )
    }
}

fun openMediaAttachmentIntent(
    context: Context,
    attachment: MediaAttachment,
    onError: (String) -> Unit = {}
) {
    runCatching {
        val file = File(attachment.localPath)
        if (!file.exists()) {
            onError("Attachment file not found.")
            return
        }
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, attachment.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(viewIntent, "Open ${attachment.displayName}")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { err ->
        onError(err.message ?: "Unable to open attachment.")
    }
}

@Composable
fun InlineMediaImage(
    path: String? = null,
    localPath: String? = null,
    heightDp: Int = 150,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    val resolvedPath = path ?: localPath ?: return
    val heightVal: Dp = heightDp.dp
    val bitmap = remember(resolvedPath) {
        runCatching {
            val file = File(resolvedPath)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxWidth()
                .height(heightVal)
                .clip(RoundedCornerShape(10.dp))
        )
    }
}

@Composable
fun MediaAttachmentCard(
    attachment: MediaAttachment? = null,
    att: MediaAttachment? = null,
    onOpen: (MediaAttachment) -> Unit = {},
    onRemove: ((MediaAttachment) -> Unit)? = null
) {
    val target = attachment ?: att ?: return
    val icon = when {
        target.mimeType.startsWith("image/") -> Icons.Outlined.Image
        target.mimeType.startsWith("video/") -> Icons.Outlined.Videocam
        target.mimeType.startsWith("audio/") -> Icons.Outlined.Audiotrack
        else -> Icons.Outlined.AttachFile
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale { onOpen(target) }
            .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
            .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (target.mimeType.startsWith("image/")) {
            InlineMediaImage(path = target.localPath, contentDescription = target.displayName)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = target.displayName,
                    color = LairPalette.TextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${target.mimeType} • ${(target.sizeBytes / 1024L).coerceAtLeast(1L)} KB",
                    color = LairPalette.TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Icon(Icons.Outlined.OpenInNew, contentDescription = "Open", tint = LairPalette.TextSecondary, modifier = Modifier.size(16.dp))
            if (onRemove != null) {
                Box(modifier = Modifier.pressScale { onRemove(target) }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove", tint = LairPalette.TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun MediaPickerActionRow(
    targetDir: File,
    onMediaAdded: (MediaAttachment) -> Unit,
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importUriToDirectory(context, uri, targetDir)
                .onSuccess(onMediaAdded)
                .onFailure { err -> onError(err.message ?: "Failed to attach photo.") }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importUriToDirectory(context, uri, targetDir)
                .onSuccess(onMediaAdded)
                .onFailure { err -> onError(err.message ?: "Failed to attach video.") }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importUriToDirectory(context, uri, targetDir)
                .onSuccess(onMediaAdded)
                .onFailure { err -> onError(err.message ?: "Failed to attach audio.") }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Attach Media (Photo, Video, Audio)",
            color = LairPalette.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pressScale { imagePicker.launch("image/*") }
                    .background(LairPalette.SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, primary.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Image, contentDescription = null, tint = primary, modifier = Modifier.size(16.dp))
                    Text("Photo", color = LairPalette.TextPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .pressScale { videoPicker.launch("video/*") }
                    .background(LairPalette.SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(8.dp))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Text("Video", color = LairPalette.TextPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .pressScale { audioPicker.launch("audio/*") }
                    .background(LairPalette.SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(8.dp))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Audiotrack, contentDescription = null, tint = LairPalette.MintSuccess, modifier = Modifier.size(16.dp))
                    Text("Audio", color = LairPalette.TextPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MediaPickerStrip(
    targetDir: File,
    attachments: List<MediaAttachment> = emptyList(),
    onAdd: (MediaAttachment) -> Unit = {},
    onRemove: (MediaAttachment) -> Unit = {},
    onMediaAdded: ((MediaAttachment) -> Unit)? = null,
    onError: (String) -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MediaPickerActionRow(
            targetDir = targetDir,
            onMediaAdded = { added ->
                onAdd(added)
                onMediaAdded?.invoke(added)
            },
            onError = onError
        )
        if (attachments.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                attachments.forEach { att ->
                    MediaAttachmentCard(
                        attachment = att,
                        onRemove = onRemove
                    )
                }
            }
        }
    }
}

@Composable
fun AttachmentPreviewStrip(
    attachments: List<MediaAttachment>,
    onRemove: ((MediaAttachment) -> Unit)? = null
) {
    if (attachments.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(attachments, key = { it.id }) { att ->
            val icon = when {
                att.mimeType.startsWith("image/") -> Icons.Outlined.Image
                att.mimeType.startsWith("video/") -> Icons.Outlined.Videocam
                att.mimeType.startsWith("audio/") -> Icons.Outlined.Audiotrack
                else -> Icons.Outlined.AttachFile
            }
            Row(
                modifier = Modifier
                    .background(LairPalette.SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                Text(
                    text = att.displayName,
                    color = LairPalette.TextPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (onRemove != null) {
                    Box(modifier = Modifier.pressScale { onRemove(att) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Remove", tint = LairPalette.TextSecondary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
