package sh.hnet.comfychair.ui.screens

import android.widget.Toast

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import sh.hnet.comfychair.R
import sh.hnet.comfychair.WorkflowEditorActivity
import sh.hnet.comfychair.connection.ConnectionManager
import sh.hnet.comfychair.model.ScreenType
import sh.hnet.comfychair.queue.JobRegistry
import sh.hnet.comfychair.storage.AppSettings
import sh.hnet.comfychair.ui.components.AppMenuDropdown
import sh.hnet.comfychair.ui.components.config.ConfigBottomSheetContent
import sh.hnet.comfychair.ui.components.config.UnifiedCallbacks
import sh.hnet.comfychair.ui.components.generate.GenerationScreenLayout
import sh.hnet.comfychair.ui.components.generate.MetaActionsRow
import sh.hnet.comfychair.ui.components.generate.PromptCardContent
import sh.hnet.comfychair.ui.components.generate.PreviewPlaceholder
import sh.hnet.comfychair.ui.components.generate.ProgressPill
import sh.hnet.comfychair.ui.components.generate.WorkflowSplitDropdown
import sh.hnet.comfychair.ui.components.shared.DimensionStepperRow
import sh.hnet.comfychair.ui.components.shared.PromptPresetDropdown
import sh.hnet.comfychair.ui.components.shared.ResolutionPresetRow
import sh.hnet.comfychair.ui.components.shared.rememberSpellCheckVisualTransformation
import sh.hnet.comfychair.ui.components.generate.StatusDot
import sh.hnet.comfychair.ui.components.generate.ratioOf
import sh.hnet.comfychair.viewmodel.ConnectionStatus
import sh.hnet.comfychair.viewmodel.GenerationViewModel
import sh.hnet.comfychair.viewmodel.PromptPresetEvent
import sh.hnet.comfychair.viewmodel.PromptPresetViewModel
import sh.hnet.comfychair.viewmodel.ReferenceGenerationEvent
import sh.hnet.comfychair.viewmodel.ReferenceGenerationViewModel
import sh.hnet.comfychair.viewmodel.toBottomSheetConfig
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import sh.hnet.comfychair.MediaViewerActivity
import sh.hnet.comfychair.ui.components.GenerationButton
import sh.hnet.comfychair.ui.components.VideoPlayer
import sh.hnet.comfychair.ui.components.VideoScaleMode
import sh.hnet.comfychair.ui.components.generate.GenerateWithGalleryRow
import sh.hnet.comfychair.ui.components.generate.AlbumDropdown
import sh.hnet.comfychair.ui.components.generate.SmallActionIcon
import sh.hnet.comfychair.repository.AlbumRepository
import sh.hnet.comfychair.util.VideoUtils
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share

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
    var promptExpanded by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    val optionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val previewRatio = ratioOf(uiState.width, uiState.height) ?: if (isVideoMode) 16f / 9f else 1f
    val progressVisible = queueState.executingOwnerId == ownerId &&
        generationState.maxProgress > 0 && generationState.progress > 0
    val batchLabel = if (queueState.batchTotal > 0) "${queueState.completedInBatch}/${queueState.batchTotal}" else null

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
        else uiState.previewBitmap?.let { bitmap -> context.startActivity(MediaViewerActivity.createPreviewIntent(context, generationViewModel.getHostname(), generationViewModel.getPort(), bitmap, null, null, null)) }
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
            onLengthChange = referenceViewModel::onLengthChange,
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

    val settingsRow: @Composable ColumnScope.() -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ResolutionPresetRow(
                width = uiState.width,
                height = uiState.height,
                onSelect = { width, height ->
                    referenceViewModel.setWidthAndHeight(width.toString(), height.toString())
                }
            )
            DimensionStepperRow(
                workflowName = uiState.selectedWorkflow,
                width = uiState.width,
                onWidthChange = referenceViewModel::onWidthChange,
                widthError = uiState.widthError,
                height = uiState.height,
                onHeightChange = referenceViewModel::onHeightChange,
                heightError = uiState.heightError
            )
        }
    }

    GenerationScreenLayout(
        expandPrompt = promptExpanded,
        previewRatio = previewRatio,
        onPreviewClick = if (uiState.previewBitmap != null || uiState.currentVideoUri != null) ({ openResult() }) else null,
        progress = { modifier, translucent ->
            ProgressPill(
                progress = generationState.progress,
                max = generationState.maxProgress,
                modifier = modifier,
                translucent = translucent,
                active = progressVisible,
                label = batchLabel
            )
        },
        workflowDropdown = { modifier ->
            WorkflowSplitDropdown(
                workflows = uiState.availableWorkflows.map { it.name },
                selected = uiState.selectedWorkflow,
                onSelect = referenceViewModel::onWorkflowChange,
                onSettings = { showOptionsSheet = true },
                settingsDescription = stringResource(R.string.button_options),
                modifier = modifier
            )
        },
        albumDropdown = { AlbumDropdown(albums, albumId, onSelect = { AlbumRepository.select(it) }) },
        serverMenu = {
            Box {
                AppMenuDropdown(onSettings = onNavigateToSettings, onLogout = onLogout)
                StatusDot(
                    connected = connectionStatus == ConnectionStatus.CONNECTED,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp)
                )
            }
        },
        previewContent = {
            if (uiState.availableWorkflows.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PreviewPlaceholder(stringResource(R.string.workflow_section_empty))
                    TextButton(onClick = onNavigateToSettings) {
                        Text(stringResource(R.string.nav_workflows_settings))
                    }
                }
            } else if (uiState.currentVideoUri != null) {
                VideoPlayer(uiState.currentVideoUri, Modifier.fillMaxSize(), isActive = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED), scaleMode = VideoScaleMode.FIT)
            } else if (uiState.previewBitmap != null) {
                Image(uiState.previewBitmap!!.asImageBitmap(), stringResource(R.string.content_description_preview), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else PreviewPlaceholder(stringResource(R.string.content_description_preview))
        },
        belowPreview = {
            MetaActionsRow(info = buildString {
                append(uiState.width)
                append('×')
                append(uiState.height)
                if (isVideoMode && uiState.capabilities.hasLength) {
                    append(" · ")
                    append(uiState.length)
                    append('f')
                }
                if (isVideoMode && uiState.capabilities.hasFrameRate) {
                    append(" · ")
                    append(uiState.fps)
                    append("fps")
                }
            }) {
                SmallActionIcon(Icons.Default.Save, stringResource(R.string.button_save_to_gallery), uiState.previewBitmap != null || uiState.currentVideoUri != null) {
                    val video = uiState.currentVideoUri
                    if (video != null) scope.launch { VideoUtils.saveVideoToGallery(context, video, VideoUtils.GalleryPrefix.REFERENCE_TO_VIDEO) }
                    else referenceViewModel.saveImage()
                }
                SmallActionIcon(Icons.Default.Share, stringResource(R.string.button_share), uiState.previewBitmap != null || uiState.currentVideoUri != null) {
                    uiState.currentVideoUri?.let { VideoUtils.shareVideo(context, it) } ?: referenceViewModel.shareImage()
                }
            }
        },
        prompt = { fill ->
            PromptCardContent(
                value = uiState.positivePrompt,
                onValueChange = {
                    referenceViewModel.onPositivePromptChange(it)
                    presetViewModel.clearActivePreset()
                },
                fill = fill,
                onFocusChanged = { promptExpanded = it },
                autoCorrect = spellCheckEnabled,
                visualTransformation = visualTransformation,
                presetButton = {
                    PromptPresetDropdown(
                        favorites = presetUiState.favorites,
                        activePresetId = presetUiState.activePresetId,
                        currentPromptIsEmpty = uiState.positivePrompt.isEmpty(),
                        onPresetSelected = presetViewModel::onPresetSelected,
                        onOpenLibrary = presetViewModel::showLibrary,
                        onSaveCurrentPrompt = { presetViewModel.showSaveDialog(uiState.positivePrompt) },
                        onResetPrompt = presetViewModel::resetPrompt
                    )
                }
            )
        },
        generateRow = {
            GenerateWithGalleryRow {
                GenerationButton(
                    queueSize = queueState.totalQueueSize,
                    isExecuting = queueState.isExecuting,
                    isEnabled = referenceViewModel.hasValidConfiguration(),
                    isOfflineMode = AppSettings.isOfflineMode(context),
                    isUploading = uiState.isUploading,
                    isFetching = uiState.isFetching,
                    isConnecting = isConnecting,
                    onGenerate = { referenceViewModel.generate(generationViewModel) },
                    onCancelCurrent = { generationViewModel.cancelGeneration {} },
                    onAddToFrontOfQueue = { referenceViewModel.generate(generationViewModel, true) },
                    onClearQueue = { client?.clearQueue {} },
                    modifier = Modifier.weight(1f)
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
