package sh.hnet.comfychair.ui.components.shared

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
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
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (config.allowVideos && config.onAddMixed != null) {
            Text(stringResource(R.string.reference_media), style = MaterialTheme.typography.titleSmall)
            ReferenceMediaPickerButton(
                allowVideos = true,
                imageCapacity = (config.imageCapacity - config.images.size).coerceAtLeast(0),
                videoCapacity = (config.videoCapacity - config.videos.size).coerceAtLeast(0),
                enabled = config.enabled,
                onSelected = config.onAddMixed
            ) { onClick, enabled ->
                OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.reference_picker_add_media))
                }
            }
        }
        ReferenceSlotPicker(ReferenceMediaKind.IMAGE, config.images, config.imageCapacity, config)
        if (config.allowVideos) ReferenceSlotPicker(ReferenceMediaKind.VIDEO, config.videos, config.videoCapacity, config)
    }
}

@Composable
private fun ReferenceSlotPicker(kind: ReferenceMediaKind, selections: List<ReferenceMediaSelection>, capacity: Int, config: ReferenceConfig) {
    val title = stringResource(if (kind == ReferenceMediaKind.IMAGE) R.string.reference_images else R.string.reference_videos)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$title (${selections.size}/$capacity)", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(selections, key = { index, item -> "$index:${item.uri}" }) { index, item ->
                val label = stringResource(if (kind == ReferenceMediaKind.IMAGE) R.string.reference_image_slot else R.string.reference_video_slot, index + 1)
                Card(Modifier.width(132.dp)) {
                    ReferenceMediaPickerButton(
                        allowVideos = kind == ReferenceMediaKind.VIDEO,
                        imageCapacity = if (kind == ReferenceMediaKind.IMAGE) 1 else 0,
                        videoCapacity = if (kind == ReferenceMediaKind.VIDEO) 1 else 0,
                        enabled = config.enabled,
                        onSelected = { uris -> uris.firstOrNull()?.let { config.onReplace(kind, index, it) } }
                    ) { onClick, enabled ->
                        Box(Modifier.size(132.dp).clickable(enabled = enabled, onClick = onClick)) {
                            ReferenceThumbnail(item, kind, label)
                            IconButton(onClick = { config.onRemove(kind, index) }, enabled = config.enabled) {
                                Icon(Icons.Default.Close, stringResource(R.string.button_delete))
                            }
                        }
                    }
                    Text(label, Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelMedium)
                    Text(item.displayName, Modifier.padding(6.dp), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (kind == ReferenceMediaKind.IMAGE || !config.allowVideos || config.onAddMixed == null) item {
                ReferenceMediaPickerButton(
                    allowVideos = false,
                    imageCapacity = (capacity - selections.size).coerceAtLeast(0),
                    videoCapacity = 0,
                    enabled = config.enabled,
                    onSelected = { config.onAdd(kind, it) }
                ) { onClick, enabled ->
                    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(132.dp)) {
                        Icon(Icons.Default.Add, stringResource(R.string.reference_add))
                    }
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
