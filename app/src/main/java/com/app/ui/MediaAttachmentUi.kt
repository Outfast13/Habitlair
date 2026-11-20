package com.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.habits.MediaAttachment
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private fun importUriIntoDir(context: Context, uri: Uri, targetDir: File): MediaAttachment? {
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
        } ?: return null
        if (size <= 0L) size = destFile.length()
        MediaAttachment(
            id = UUID.randomUUID().toString(),
            displayName = name,
            mimeType = mime,
            localPath = destFile.absolutePath,
            sizeBytes = size
        )
    }.getOrNull()
}

@Composable
fun MediaPickerActionRow(
    targetDir: File,
    onMediaAdded: (MediaAttachment) -> Unit
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importUriIntoDir(context, uri, targetDir)?.let(onMediaAdded)
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importUriIntoDir(context, uri, targetDir)?.let(onMediaAdded)
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importUriIntoDir(context, uri, targetDir)?.let(onMediaAdded)
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
