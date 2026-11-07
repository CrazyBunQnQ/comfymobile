package sh.hnet.comfychair.ui.components.shared

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.hnet.comfychair.R

private data class GalleryMedia(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val dateAdded: Long,
    val durationLabel: String?
)

/** Opens an in-app thumbnail grid whose numbered selections retain their tap order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceMediaPickerButton(
    allowVideos: Boolean,
    imageCapacity: Int,
    videoCapacity: Int,
    enabled: Boolean,
    onSelected: (List<Uri>) -> Unit,
    trigger: @Composable (onClick: () -> Unit, enabled: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    var media by remember { mutableStateOf<List<GalleryMedia>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasAccess by remember { mutableStateOf(false) }
    var accessDenied by remember { mutableStateOf(false) }
    val selectedUris = remember { mutableStateListOf<Uri>() }
    val requestedPermissions = remember(allowVideos) { galleryPermissions(allowVideos) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun loadGallery() {
        scope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) { runCatching { queryGallery(context, allowVideos) } }
            media = result.getOrElse { emptyList() }
            accessDenied = result.isFailure
            isLoading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasAccess = hasGalleryReadAccess(context, allowVideos)
        accessDenied = !hasAccess
        if (hasAccess) loadGallery()
    }

    fun openPicker() {
        showPicker = true
        selectedUris.clear()
        accessDenied = false
        hasAccess = hasGalleryReadAccess(context, allowVideos)
        if (hasAccess) loadGallery()
        if (!hasAccess || allowVideos && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(requestedPermissions)
        }
    }

    trigger(::openPicker, enabled && (imageCapacity > 0 || (allowVideos && videoCapacity > 0)))

    if (showPicker) {
        ModalBottomSheet(onDismissRequest = { showPicker = false }, sheetState = sheetState) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.82f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(if (allowVideos) R.string.reference_picker_title_mixed else R.string.reference_picker_title_images),
                    style = MaterialTheme.typography.titleLarge
                )
                val selectedImages = selectedUris.count { uri -> media.firstOrNull { it.uri == uri }?.isVideo == false }
                val selectedVideos = selectedUris.size - selectedImages
                val remaining = (imageCapacity - selectedImages).coerceAtLeast(0) +
                    if (allowVideos) (videoCapacity - selectedVideos).coerceAtLeast(0) else 0
                Text(
                    stringResource(R.string.reference_picker_selected, selectedUris.size, remaining) +
                        if (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)
                            " · ${stringResource(R.string.reference_picker_choose_more)}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(enabled = Build.VERSION.SDK_INT >= 34 && hasAccess) { permissionLauncher.launch(requestedPermissions) }
                )
                when {
                    !hasAccess || accessDenied -> Column(
                        Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(if (accessDenied) R.string.reference_picker_permission_denied else R.string.reference_picker_permission_description),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = { permissionLauncher.launch(requestedPermissions) }) {
                            Text(stringResource(if (hasAccess) R.string.reference_picker_choose_more else R.string.reference_picker_grant_access))
                        }
                    }
                    isLoading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    media.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.reference_picker_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(144.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(media, key = { it.uri.toString() }) { item ->
                            val selectionOrder = selectedUris.indexOf(item.uri).takeIf { it >= 0 }?.plus(1)
                            val selectedCountForKind = if (item.isVideo) selectedVideos else selectedImages
                            val capacityForKind = if (item.isVideo) videoCapacity else imageCapacity
                            val canSelect = selectionOrder != null || selectedCountForKind < capacityForKind
                            GalleryMediaTile(item, selectionOrder, canSelect) {
                                if (selectionOrder != null) selectedUris.remove(item.uri)
                                else if (canSelect) selectedUris.add(item.uri)
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.button_cancel)) }
                    Button(
                        onClick = {
                            val ordered = selectedUris.toList()
                            ordered.forEach { uri ->
                                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                            }
                            onSelected(ordered)
                            showPicker = false
                        },
                        enabled = hasAccess && selectedUris.isNotEmpty()
                    ) { Text(stringResource(R.string.reference_picker_add, selectedUris.size)) }
                }
            }
        }
    }
}

@Composable
private fun GalleryMediaTile(item: GalleryMedia, selectionOrder: Int?, enabled: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(item.uri, Size(480, 480), null) }.getOrNull()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    width = if (selectionOrder != null) 3.dp else 0.dp,
                    color = if (selectionOrder != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.small
                ).clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            thumbnail?.let {
                Image(it.asImageBitmap(), item.displayName, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
            if (thumbnail == null && item.isVideo) Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(32.dp))
            if (item.isVideo) Text(
                item.durationLabel.orEmpty(),
                Modifier.align(Alignment.BottomStart).padding(5.dp).clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f)).padding(horizontal = 5.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall
            )
            if (selectionOrder != null) Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(selectionOrder.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
    }
}

private fun galleryPermissions(allowVideos: Boolean): Array<String> = buildList {
    add(Manifest.permission.READ_MEDIA_IMAGES)
    if (allowVideos) add(Manifest.permission.READ_MEDIA_VIDEO)
    if (Build.VERSION.SDK_INT >= 34) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
}.toTypedArray()

private fun hasGalleryReadAccess(context: Context, allowVideos: Boolean): Boolean =
    galleryPermissions(allowVideos).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

private fun queryGallery(context: Context, allowVideos: Boolean): List<GalleryMedia> {
    val resolver = context.contentResolver
    val hasSelectedAccess = Build.VERSION.SDK_INT >= 34 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    val canReadImages = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED || hasSelectedAccess
    val canReadVideos = allowVideos && (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED || hasSelectedAccess)
    val results = mutableListOf<GalleryMedia>()

    fun collect(collection: Uri, isVideo: Boolean) {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            if (isVideo) add(MediaStore.Video.VideoColumns.DURATION)
        }.toTypedArray()
        resolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val dateColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val durationColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(collection, id)
                val durationMs = if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L
                results.add(
                    GalleryMedia(
                        uri = uri,
                        displayName = if (nameColumn >= 0) cursor.getString(nameColumn).orEmpty() else uri.lastPathSegment.orEmpty(),
                        mimeType = if (mimeColumn >= 0) cursor.getString(mimeColumn).orEmpty() else if (isVideo) "video/*" else "image/*",
                        isVideo = isVideo,
                        dateAdded = if (dateColumn >= 0) cursor.getLong(dateColumn) else 0L,
                        durationLabel = durationMs.takeIf { it > 0 }?.let { "${it / 1000}s" }
                    )
                )
            }
        }
    }

    if (canReadImages) collect(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
    if (canReadVideos) collect(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
    return results.sortedByDescending { it.dateAdded }
}
