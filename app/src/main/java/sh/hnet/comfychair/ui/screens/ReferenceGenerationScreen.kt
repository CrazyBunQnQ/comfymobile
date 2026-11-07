package sh.hnet.comfychair.ui.screens

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.hnet.comfychair.MediaViewerActivity
import sh.hnet.comfychair.R
import sh.hnet.comfychair.WorkflowEditorActivity
import sh.hnet.comfychair.connection.ConnectionManager
import sh.hnet.comfychair.model.ScreenType
import sh.hnet.comfychair.queue.JobRegistry
import sh.hnet.comfychair.repository.AlbumRepository
import sh.hnet.comfychair.storage.AppSettings
import sh.hnet.comfychair.ui.components.AppMenuDropdown
import sh.hnet.comfychair.ui.components.GenerationButton
import sh.hnet.comfychair.ui.components.VideoPlayer
import sh.hnet.comfychair.ui.components.VideoScaleMode
import sh.hnet.comfychair.ui.components.config.ConfigBottomSheetContent
import sh.hnet.comfychair.ui.components.config.UnifiedCallbacks
import sh.hnet.comfychair.ui.components.generate.AlbumDropdown
import sh.hnet.comfychair.ui.components.generate.GenerateWithGalleryRow
import sh.hnet.comfychair.ui.components.generate.GenerationScreenLayout
import sh.hnet.comfychair.ui.components.generate.MetaActionsRow
import sh.hnet.comfychair.ui.components.generate.PromptCardContent
import sh.hnet.comfychair.ui.components.generate.PreviewPlaceholder
import sh.hnet.comfychair.ui.components.generate.ProgressPill
import sh.hnet.comfychair.ui.components.generate.SmallActionIcon
import sh.hnet.comfychair.ui.components.generate.SourceResultToggle
import sh.hnet.comfychair.ui.components.generate.StatusDot
import sh.hnet.comfychair.ui.components.generate.WorkflowSplitDropdown
import sh.hnet.comfychair.ui.components.generate.ratioOf
import sh.hnet.comfychair.ui.components.shared.PromptPresetDropdown
import sh.hnet.comfychair.ui.components.shared.ReferenceMediaPickerButton
import sh.hnet.comfychair.ui.components.shared.rememberSpellCheckVisualTransformation
import sh.hnet.comfychair.util.VideoUtils
import sh.hnet.comfychair.viewmodel.ConnectionStatus
import sh.hnet.comfychair.viewmodel.GenerationViewModel
import sh.hnet.comfychair.viewmodel.PromptPresetEvent
import sh.hnet.comfychair.viewmodel.PromptPresetViewModel
import sh.hnet.comfychair.viewmodel.ReferenceGenerationEvent
import sh.hnet.comfychair.viewmodel.ReferenceGenerationViewModel
import sh.hnet.comfychair.viewmodel.toBottomSheetConfig
import sh.hnet.comfychair.workflow.ReferenceResolution
import sh.hnet.comfychair.workflow.WorkflowConstraintsProvider

/**
 * Shared screen for the reference-to-image and reference-to-video modes.
 * Reference selection, independent output canvas, and owner-scoped results.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReferenceGenerationScreen(
    generationViewModel: GenerationViewModel,
    referenceViewModel: ReferenceGenerationViewModel,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val presetViewModel: PromptPresetViewModel = viewModel()
    val uiState by referenceViewModel.uiState.collectAsState()
    val generationState by generationViewModel.generationState.collectAsState()
    val connectionStatus by generationViewModel.connectionStatus.collectAsState()
    val queueState by JobRegistry.queueState.collectAsState()
    val presetUiState by presetViewModel.uiState.collectAsState()
    val isConnecting by ConnectionManager.isConnecting.collectAsState()

    val isVideoMode = referenceViewModel.isVideoMode
    val screenType = if (isVideoMode) ScreenType.REFERENCE_TO_VIDEO else ScreenType.REFERENCE_TO_IMAGE
    val ownerId = referenceViewModel.ownerId
    val client = generationViewModel.getClient()
    var spellCheckEnabled by remember { mutableStateOf(AppSettings.isPromptSpellCheckEnabled(context)) }
    val imeHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    var previousImeHeight by remember { mutableStateOf(imeHeight) }
    SideEffect { previousImeHeight = imeHeight }
    var promptFocused by remember { mutableStateOf(false) }
    val promptExpanded = promptFocused && imeHeight > 0 && imeHeight >= previousImeHeight

    var showOptionsSheet by remember { mutableStateOf(false) }
    val optionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showingSource by remember(referenceViewModel) { mutableStateOf(true) }
    val sourceMedia = uiState.sourceMedia
    val hasSourceMedia = sourceMedia != null
    val hasResult = uiState.previewBitmap != null || uiState.currentVideoUri != null
    val showingSourceNow = showingSource
    val previewRatio = when {
        showingSourceNow && sourceMedia != null && sourceMedia.width > 0 && sourceMedia.height > 0 -> sourceMedia.width.toFloat() / sourceMedia.height
        showingSourceNow -> if (isVideoMode) 16f / 9f else 1f
        else -> ratioOf(uiState.width, uiState.height) ?: if (isVideoMode) 16f / 9f else 1f
    }
    val progressVisible = queueState.executingOwnerId == ownerId && generationState.maxProgress > 0 && generationState.progress > 0
    val batchLabel = if (queueState.batchTotal > 0) "${queueState.completedInBatch}/${queueState.batchTotal}" else null
    LaunchedEffect(referenceViewModel, uiState.previewBitmap, uiState.currentVideoUri) {
        if (generationState.ownerId == ownerId && hasResult) showingSource = false
    }

    LaunchedEffect(client) {
        if (client != null) referenceViewModel.initialize(context, client)
        presetViewModel.initialize(context, screenType)
    }
    DisposableEffect(referenceViewModel, generationViewModel) {
        referenceViewModel.startListening(generationViewModel)
        onDispose { referenceViewModel.stopListening() }
    }
    LaunchedEffect(queueState.executingOwnerId, queueState.executingPromptId) {
        if (queueState.executingOwnerId == ownerId) referenceViewModel.startListening(generationViewModel)
    }
    LaunchedEffect(Unit) { AlbumRepository.ensureLoaded(context) }
    val albums by AlbumRepository.albums.collectAsState()
    val albumId by AlbumRepository.currentAlbumId.collectAsState()
    fun openResult() {
        val video = uiState.currentVideoUri
        if (video != null) context.startActivity(MediaViewerActivity.createSingleVideoIntent(context, video, generationViewModel.getHostname(), generationViewModel.getPort()))
        else uiState.previewBitmap?.let { bitmap ->
            context.startActivity(MediaViewerActivity.createPreviewIntent(
                context, generationViewModel.getHostname(), generationViewModel.getPort(), bitmap,
                uiState.currentImageFilename, uiState.currentImageSubfolder, uiState.currentImageType
            ))
        }
    }
    fun openSource() {
        val source = sourceMedia ?: return
        if (source.mimeType.startsWith("video/")) context.startActivity(MediaViewerActivity.createSingleVideoIntent(context, source.uri))
        else scope.launch {
            runCatching {
                val bitmap = withContext(Dispatchers.IO) { loadReferenceBitmap(context, source.uri, 2048) }
                context.startActivity(MediaViewerActivity.createSingleImageIntent(context, bitmap))
            }.onFailure { Toast.makeText(context, context.getString(R.string.reference_preview_unavailable), Toast.LENGTH_SHORT).show() }
        }
    }

    fun saveSourceImage() {
        val source = sourceMedia?.takeIf { it.mimeType.startsWith("image/") } ?: return
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "ComfyMobile_REF_${System.currentTimeMillis()}.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ComfyMobile")
                    }
                    val bitmap = loadReferenceBitmap(context, source.uri)
                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Cannot create image")
                    context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
                }.getOrDefault(false)
            }
            Toast.makeText(context, context.getString(if (saved) R.string.msg_image_saved_to_gallery else R.string.error_save_image), Toast.LENGTH_SHORT).show()
        }
    }

    fun shareSourceImage() {
        val source = sourceMedia?.takeIf { it.mimeType.startsWith("image/") } ?: return
        scope.launch {
            runCatching {
                val bitmap = withContext(Dispatchers.IO) { loadReferenceBitmap(context, source.uri) }
                val file = java.io.File(context.cacheDir, "reference_source_share.png")
                withContext(Dispatchers.IO) { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.share_image)))
            }.onFailure { Toast.makeText(context, context.getString(R.string.error_share_image), Toast.LENGTH_SHORT).show() }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                spellCheckEnabled = AppSettings.isPromptSpellCheckEnabled(context)
                presetViewModel.refreshPresets()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        referenceViewModel.events.collect { event ->
            when (event) {
                is ReferenceGenerationEvent.ShowToast ->
                    Toast.makeText(context, context.getString(event.messageResId), Toast.LENGTH_SHORT).show()
                is ReferenceGenerationEvent.ShowToastMessage ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        presetViewModel.events.collect { event ->
            when (event) {
                is PromptPresetEvent.PresetApplied -> {
                    referenceViewModel.onPositivePromptChange(event.prompt)
                }
                is PromptPresetEvent.ShowToast ->
                    Toast.makeText(context, context.getString(event.messageResId), Toast.LENGTH_SHORT).show()
                is PromptPresetEvent.MaxFavoritesReached ->
                    Toast.makeText(context, R.string.prompt_preset_max_favorites, Toast.LENGTH_SHORT).show()
                is PromptPresetEvent.ResetPrompt -> referenceViewModel.resetPromptToDefault()
            }
        }
    }

    val visualTransformation = rememberSpellCheckVisualTransformation(
        text = uiState.positivePrompt,
        enabled = spellCheckEnabled
    )
    val callbacks = remember(referenceViewModel, uiState.availableWorkflows, uiState.selectedWorkflow) {
        UnifiedCallbacks(
            onWorkflowChange = referenceViewModel::onWorkflowChange,
            onViewWorkflow = {
                val workflowId = referenceViewModel.uiState.value.availableWorkflows
                    .firstOrNull { it.name == referenceViewModel.uiState.value.selectedWorkflow }?.id
                if (workflowId != null) {
                    context.startActivity(WorkflowEditorActivity.createIntent(context, workflowId))
                }
            },
            onNegativePromptChange = referenceViewModel::onNegativePromptChange,
            onCheckpointChange = referenceViewModel::onCheckpointChange,
            onUnetChange = referenceViewModel::onUnetChange,
            onVaeChange = referenceViewModel::onVaeChange,
            onClipChange = referenceViewModel::onClipChange,
            onClip1Change = referenceViewModel::onClip1Change,
            onClip2Change = referenceViewModel::onClip2Change,
            onClip3Change = referenceViewModel::onClip3Change,
            onClip4Change = referenceViewModel::onClip4Change,
            onTextEncoderChange = referenceViewModel::onTextEncoderChange,
            onLatentUpscaleModelChange = referenceViewModel::onLatentUpscaleModelChange,
            onMandatoryLoraChange = referenceViewModel::onMandatoryLoraChange,
            onWidthChange = referenceViewModel::onWidthChange,
            onHeightChange = referenceViewModel::onHeightChange,
            onMegapixelsChange = referenceViewModel::onMegapixelsChange,
            onLengthChange = referenceViewModel::onDurationChange,

            onFpsChange = referenceViewModel::onFpsChange,
            onStepsChange = referenceViewModel::onStepsChange,
            onCfgChange = referenceViewModel::onCfgChange,
            onSamplerChange = referenceViewModel::onSamplerChange,
            onSchedulerChange = referenceViewModel::onSchedulerChange,
            onRandomSeedToggle = referenceViewModel::onRandomSeedToggle,
            onSeedChange = referenceViewModel::onSeedChange,
            onRandomizeSeed = referenceViewModel::onRandomizeSeed,
            onDenoiseChange = referenceViewModel::onDenoiseChange,
            onBatchSizeChange = referenceViewModel::onBatchSizeChange,
            onUpscaleMethodChange = referenceViewModel::onUpscaleMethodChange,
            onScaleByChange = referenceViewModel::onScaleByChange,
            onStopAtClipLayerChange = referenceViewModel::onStopAtClipLayerChange,
            onAddLora = referenceViewModel::onAddLora,
            onRemoveLora = referenceViewModel::onRemoveLora,
            onLoraNameChange = referenceViewModel::onLoraNameChange,
            onLoraStrengthChange = referenceViewModel::onLoraStrengthChange
        )
    }
    val bottomSheetConfig = remember(uiState, callbacks) { uiState.toBottomSheetConfig(callbacks, referenceViewModel.referenceConfig()) }

    val resolutionChoices = remember(sourceMedia, uiState.megapixels, uiState.selectedWorkflow) {
        val widthConstraints = WorkflowConstraintsProvider.getConstraints("width", uiState.selectedWorkflow)
        val heightConstraints = WorkflowConstraintsProvider.getConstraints("height", uiState.selectedWorkflow)
        val mp = uiState.megapixels.toFloatOrNull() ?: 0f
        fun dimensions(width: Int, height: Int) = ReferenceResolution.calculateCanvas(width, height, mp, widthConstraints, heightConstraints)
        listOf(
            sourceMedia?.let { dimensions(it.width, it.height) },
            dimensions(2, 3), dimensions(1, 1), dimensions(3, 2)
        )
    }
    val settingsRow: @Composable ColumnScope.() -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReferenceResolutionRow(
                useOriginalRatio = uiState.useOriginalRatio,
                dimensions = resolutionChoices,
                width = uiState.width,
                height = uiState.height,
                onOriginalRatioSelect = referenceViewModel::onOriginalRatioSelect,
                onFixedSelect = referenceViewModel::onResolutionPresetSelect
            )
            if (isVideoMode && uiState.capabilities.hasLength) {
                DurationPresetRow(
                    durationSeconds = uiState.durationSeconds,
                    onSelect = referenceViewModel::onDurationChange
                )
            }
        }
    }


    GenerationScreenLayout(
        expandPrompt = promptExpanded,
        previewRatio = previewRatio,
        onPreviewClick = when {
            showingSourceNow -> ({ openSource() })
            hasResult -> ({ openResult() })
            else -> null
        },
        progress = { modifier, translucent ->
            ProgressPill(generationState.progress, generationState.maxProgress, modifier, translucent = translucent, active = progressVisible, label = batchLabel)
        },
        workflowDropdown = { modifier ->
            WorkflowSplitDropdown(
                workflows = uiState.availableWorkflows.map { it.name }, selected = uiState.selectedWorkflow,
                onSelect = referenceViewModel::onWorkflowChange, onSettings = { showOptionsSheet = true },
                settingsDescription = stringResource(R.string.button_options), modifier = modifier
            )
        },
        albumDropdown = { AlbumDropdown(albums, albumId, onSelect = { AlbumRepository.select(it) }) },
        serverMenu = {
            Box {
                AppMenuDropdown(onSettings = onNavigateToSettings, onLogout = onLogout)
                StatusDot(connectionStatus == ConnectionStatus.CONNECTED, modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp))
            }
        },
        headerActions = {
            ReferenceMediaPickerButton(
                allowVideos = isVideoMode,
                imageCapacity = (uiState.imageCapacity - uiState.referenceImages.size).coerceAtLeast(0),
                videoCapacity = (uiState.videoCapacity - uiState.referenceVideos.size).coerceAtLeast(0),
                enabled = !uiState.isUploading,
                onSelected = { uris ->
                    if (isVideoMode) referenceViewModel.addMixedReferences(uris)
                    else referenceViewModel.addReferences(sh.hnet.comfychair.workflow.ReferenceMediaKind.IMAGE, uris)
                    if (uris.isNotEmpty()) showingSource = true
                }
            ) { onClick, enabled ->
                OutlinedIconButton(onClick = onClick, enabled = enabled) {
                    androidx.compose.material3.Icon(Icons.Default.AddPhotoAlternate, contentDescription = stringResource(R.string.reference_picker_add_media))
                }
            }
        },
        previewContent = {
            if (uiState.availableWorkflows.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PreviewPlaceholder(stringResource(R.string.workflow_section_empty))
                    TextButton(onClick = onNavigateToSettings) { Text(stringResource(R.string.nav_workflows_settings)) }
                }
            } else if (showingSourceNow && !hasSourceMedia) {
                PreviewPlaceholder(stringResource(R.string.msg_no_source_image))
            } else if (showingSourceNow) {
                val source = sourceMedia
                if (source != null && source.mimeType.startsWith("video/")) {
                    VideoPlayer(source.uri, Modifier.fillMaxSize(), isActive = lifecycleState.isAtLeast(Lifecycle.State.RESUMED), scaleMode = VideoScaleMode.FIT)
                } else if (source != null) {
                    val bitmapState = produceReferenceBitmap(context, source.uri)
                    val bitmap by bitmapState
                    if (bitmap != null) Image(bitmap!!.asImageBitmap(), stringResource(R.string.content_description_source_image), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else PreviewPlaceholder(stringResource(R.string.reference_preview_unavailable))
                }
            } else if (uiState.currentVideoUri != null) {
                VideoPlayer(uiState.currentVideoUri, Modifier.fillMaxSize(), isActive = lifecycleState.isAtLeast(Lifecycle.State.RESUMED), scaleMode = VideoScaleMode.FIT)
            } else if (uiState.previewBitmap != null) {
                Image(uiState.previewBitmap!!.asImageBitmap(), stringResource(R.string.content_description_preview), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else PreviewPlaceholder(if (hasSourceMedia) stringResource(R.string.reference_result_empty) else stringResource(R.string.msg_no_source_image))
        },
        belowPreview = {
            SourceResultToggle(showingSourceNow, onShowSource = { showingSource = true }, onShowResult = { showingSource = false })
            MetaActionsRow(info = buildString {
                append(uiState.width)
                append('×')
                append(uiState.height)
                if (isVideoMode && uiState.capabilities.hasLength) append(" · ${uiState.durationSeconds}s")
                if (isVideoMode && uiState.capabilities.hasFrameRate) append(" · ${uiState.fps}fps")
            }) {
                uiState.sourceDimensionsError?.let {
                    Text(stringResource(R.string.reference_dimensions_unavailable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                val canSaveOrShare = if (showingSourceNow) hasSourceMedia else hasResult
                SmallActionIcon(Icons.Default.Save, stringResource(R.string.button_save_to_gallery), canSaveOrShare) {
                    if (showingSourceNow) {
                        if (sourceMedia?.mimeType?.startsWith("video/") == true) scope.launch { VideoUtils.saveVideoToGallery(context, sourceMedia.uri, VideoUtils.GalleryPrefix.REFERENCE_TO_VIDEO) }
                        else saveSourceImage()
                    } else if (uiState.currentVideoUri != null) scope.launch { VideoUtils.saveVideoToGallery(context, uiState.currentVideoUri, VideoUtils.GalleryPrefix.REFERENCE_TO_VIDEO) }
                    else referenceViewModel.saveImage()
                }
                SmallActionIcon(Icons.Default.Share, stringResource(R.string.button_share), canSaveOrShare) {
                    if (showingSourceNow) {
                        if (sourceMedia?.mimeType?.startsWith("video/") == true) VideoUtils.shareVideo(context, sourceMedia.uri)
                        else shareSourceImage()
                    } else uiState.currentVideoUri?.let { VideoUtils.shareVideo(context, it) } ?: referenceViewModel.shareImage()
                }
            }
        },
        prompt = { fill ->
            PromptCardContent(
                value = uiState.positivePrompt,
                onValueChange = { referenceViewModel.onPositivePromptChange(it); presetViewModel.clearActivePreset() },
                fill = fill, onFocusChanged = { promptFocused = it }, autoCorrect = spellCheckEnabled,
                visualTransformation = visualTransformation,
                presetButton = {
                    PromptPresetDropdown(
                        favorites = presetUiState.favorites, activePresetId = presetUiState.activePresetId,
                        currentPromptIsEmpty = uiState.positivePrompt.isEmpty(), onPresetSelected = presetViewModel::onPresetSelected,
                        onOpenLibrary = presetViewModel::showLibrary, onSaveCurrentPrompt = { presetViewModel.showSaveDialog(uiState.positivePrompt) },
                        onResetPrompt = presetViewModel::resetPrompt
                    )
                }
            )
        },
        generateRow = {
            GenerateWithGalleryRow {
                GenerationButton(
                    queueSize = queueState.totalQueueSize, isExecuting = queueState.isExecuting,
                    isEnabled = referenceViewModel.hasValidConfiguration(), isOfflineMode = AppSettings.isOfflineMode(context),
                    isUploading = uiState.isUploading, isFetching = uiState.isFetching, isConnecting = isConnecting,
                    onGenerate = { referenceViewModel.generate(generationViewModel) },
                    onCancelCurrent = { generationViewModel.cancelGeneration {} },
                    onAddToFrontOfQueue = { referenceViewModel.generate(generationViewModel, true) },
                    onClearQueue = { client?.clearQueue {} }, modifier = Modifier.weight(1f)
                )
            }
        },
        settingsRow = settingsRow
    )
    if (showOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsSheet = false },
            sheetState = optionsSheetState
        ) {
            ConfigBottomSheetContent(
                config = bottomSheetConfig,
                workflowName = uiState.selectedWorkflow,
                spellCheckEnabled = spellCheckEnabled
            )
        }
    }
    if (presetUiState.showLibrarySideSheet) {
        sh.hnet.comfychair.ui.components.PromptLibraryDialog(
            presets = presetViewModel.getFilteredPresets(),
            availableTags = presetUiState.availableTags,
            searchQuery = presetUiState.searchQuery,
            selectedTags = presetUiState.selectedTags,
            filterFavoritesOnly = presetUiState.filterFavoritesOnly,
            activePresetId = presetUiState.activePresetId,
            onSearchQueryChange = presetViewModel::onSearchQueryChange,
            onTagToggle = presetViewModel::onTagToggle,
            onToggleFavoritesFilter = presetViewModel::onToggleFavoritesFilter,
            onPresetSelected = presetViewModel::onPresetSelected,
            onToggleFavorite = presetViewModel::onToggleFavorite,
            onEditPreset = presetViewModel::showEditDialog,
            onDuplicatePreset = presetViewModel::onDuplicatePreset,
            onDeletePreset = presetViewModel::onDeletePreset,
            onDismiss = presetViewModel::dismissLibrary
        )
    }
    if (presetUiState.showSaveDialog) {
        sh.hnet.comfychair.ui.components.PromptPresetDialog(
            editingPreset = presetUiState.editingPreset,
            currentPrompt = presetUiState.currentPromptForSave,
            existingTags = presetUiState.availableTags,
            isNameTaken = presetViewModel::isNameTaken,
            onDismiss = presetViewModel::dismissSaveDialog,
            onSave = presetViewModel::onSavePreset
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationPresetRow(durationSeconds: String, onSelect: (String) -> Unit) {
    val selectedSeconds = durationSeconds.toIntOrNull()
    val presets = listOf(5, 10, 15)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        presets.forEachIndexed { index, seconds ->
            SegmentedButton(
                selected = selectedSeconds == seconds,
                onClick = { onSelect(seconds.toString()) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = presets.size)
            ) {
                Text("${seconds}s")
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReferenceResolutionRow(
    useOriginalRatio: Boolean,
    dimensions: List<ReferenceResolution.Dimensions?>,
    width: String,
    height: String,
    onOriginalRatioSelect: () -> Unit,
    onFixedSelect: (String) -> Unit
) {
    val presets = listOf("2:3", "1:1", "3:2")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(56.dp)) {
        SegmentedButton(
            selected = useOriginalRatio,
            onClick = onOriginalRatioSelect,
            shape = SegmentedButtonDefaults.itemShape(0, 4),
            modifier = Modifier.fillMaxHeight()
        ) {
            val dimensions = dimensions[0]?.let { "${it.width}×${it.height}" } ?: "—×—"
            Text("${stringResource(R.string.reference_resolution_original)}\n$dimensions", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        presets.forEachIndexed { index, label ->
            val size = dimensions[index + 1]
            SegmentedButton(
                selected = !useOriginalRatio && size != null && width.toIntOrNull() == size.width && height.toIntOrNull() == size.height,
                onClick = { onFixedSelect(label) },
                enabled = size != null,
                shape = SegmentedButtonDefaults.itemShape(index + 1, 4),
                modifier = Modifier.fillMaxHeight()
            ) { Text("$label\n${size?.let { "${it.width}×${it.height}" } ?: "—×—"}", textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

@Composable
private fun produceReferenceBitmap(context: android.content.Context, uri: android.net.Uri): androidx.compose.runtime.State<Bitmap?> =
    produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { runCatching { loadReferenceBitmap(context, uri) }.getOrNull() }
    }

private fun loadReferenceBitmap(context: android.content.Context, uri: android.net.Uri, maxEdge: Int = 2048): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        val longest = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
        if (longest > maxEdge) {
            val ratio = maxEdge.toFloat() / longest
            decoder.setTargetSize((info.size.width * ratio).toInt().coerceAtLeast(1), (info.size.height * ratio).toInt().coerceAtLeast(1))
        }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = false
    }
