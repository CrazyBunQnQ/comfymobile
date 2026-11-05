package sh.hnet.comfychair.ui.components.shared

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.hnet.comfychair.R
import sh.hnet.comfychair.model.ReferenceMediaSelection
import sh.hnet.comfychair.ui.components.config.ReferenceConfig
import sh.hnet.comfychair.workflow.ReferenceMediaKind

@Composable
fun ReferenceSlotsSection(config: ReferenceConfig) {
    ReferenceSlotPicker(ReferenceMediaKind.IMAGE, config.images, config.imageCapacity, config)
    if (config.allowVideos) ReferenceSlotPicker(ReferenceMediaKind.VIDEO, config.videos, config.videoCapacity, config)
}

@Composable
private fun ReferenceSlotPicker(kind: ReferenceMediaKind, selections: List<ReferenceMediaSelection>, capacity: Int, config: ReferenceConfig) {
    val context = LocalContext.current
    val mime = if (kind == ReferenceMediaKind.IMAGE) "image/*" else "video/*"
    val title = stringResource(if (kind == ReferenceMediaKind.IMAGE) R.string.reference_images else R.string.reference_videos)
    var replaceIndex by remember { mutableIntStateOf(-1) }
    fun retain(uri: Uri) { runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
    val add = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach(::retain)
        if (uris.isNotEmpty()) config.onAdd(kind, uris)
    }
    val replace = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && replaceIndex >= 0) { retain(uri); config.onReplace(kind, replaceIndex, uri) }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$title (${selections.size}/$capacity)", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(selections, key = { index, item -> "$index:${item.uri}" }) { index, item ->
                val label = stringResource(if (kind == ReferenceMediaKind.IMAGE) R.string.reference_image_slot else R.string.reference_video_slot, index + 1)
                Card(Modifier.width(132.dp)) {
                    Box(Modifier.size(132.dp).clickable(enabled = config.enabled) { replaceIndex = index; replace.launch(arrayOf(mime)) }) {
                        ReferenceThumbnail(item, kind, label)
                        IconButton(onClick = { config.onRemove(kind, index) }, enabled = config.enabled) {
                            Icon(Icons.Default.Close, stringResource(R.string.button_delete))
                        }
                    }
                    Text(label, Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelMedium)
                    Text(item.displayName, Modifier.padding(6.dp), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                OutlinedButton(onClick = { add.launch(arrayOf(mime)) }, enabled = config.enabled && selections.size < capacity,
                    modifier = Modifier.size(132.dp)) {
                    Icon(Icons.Default.Add, stringResource(R.string.reference_add))
                }
            }
        }
    }
}

private data class Thumbnail(val bitmap: Bitmap?, val duration: String?, val failed: Boolean)

@Composable
private fun ReferenceThumbnail(item: ReferenceMediaSelection, kind: ReferenceMediaKind, label: String) {
    val context = LocalContext.current
    val thumbnail by produceState<Thumbnail?>(null, item.uri, item.mimeType) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (kind == ReferenceMediaKind.IMAGE) {
                    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, info, _ ->
                        val scale = maxOf(info.size.width, info.size.height).toFloat() / 256f
                        if (scale > 1f) decoder.setTargetSize((info.size.width / scale).toInt().coerceAtLeast(1), (info.size.height / scale).toInt().coerceAtLeast(1))
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    Thumbnail(bitmap, null, false)
                } else {
                    MediaMetadataRetriever().use { retriever ->
                        retriever.setDataSource(context, item.uri)
                        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        Thumbnail(retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 256, 256),
                            duration?.let { "${it / 1000}s" }, false)
                    }
                }
            }.getOrElse { Thumbnail(null, null, true) }
        }
    }
    val result = thumbnail
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        if (result?.bitmap != null) Image(result.bitmap.asImageBitmap(), label, Modifier.fillMaxWidth().weight(1f), contentScale = ContentScale.Fit)
        else if (kind == ReferenceMediaKind.VIDEO) Icon(Icons.Default.Videocam, label, Modifier.fillMaxWidth())
        if (result?.failed == true) Text(stringResource(R.string.reference_reselect), style = MaterialTheme.typography.labelSmall)
        result?.duration?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}
