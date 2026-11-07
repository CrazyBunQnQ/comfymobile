package sh.hnet.comfychair.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sh.hnet.comfychair.WorkflowManager
import sh.hnet.comfychair.WorkflowType
import sh.hnet.comfychair.connection.ConnectionManager
import sh.hnet.comfychair.model.LoraSelection
import sh.hnet.comfychair.model.ReferenceMediaSelection
import sh.hnet.comfychair.model.WorkflowCapabilities
import sh.hnet.comfychair.model.WorkflowValues
import sh.hnet.comfychair.model.WorkflowDefaults
import sh.hnet.comfychair.storage.AppSettings
import sh.hnet.comfychair.ui.components.config.BottomSheetConfig
import sh.hnet.comfychair.ui.components.config.CommonGenerationState
import sh.hnet.comfychair.ui.components.config.PromptConfig
import sh.hnet.comfychair.ui.components.config.UnifiedCallbacks
import sh.hnet.comfychair.ui.components.config.WorkflowConfig
import sh.hnet.comfychair.ui.components.config.buildCommonLoraConfig
import sh.hnet.comfychair.ui.components.config.buildCommonModelConfig
import sh.hnet.comfychair.ui.components.config.buildCommonParameterConfig
import sh.hnet.comfychair.ui.components.shared.WorkflowItemBase
import sh.hnet.comfychair.util.LoraChainManager
import sh.hnet.comfychair.util.SeasonalPrompts
import sh.hnet.comfychair.util.ValidationUtils
import sh.hnet.comfychair.viewmodel.base.BaseGenerationViewModel
import android.net.Uri
import android.graphics.Bitmap
import sh.hnet.comfychair.util.HistoryMediaSelector
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.json.JSONObject
import sh.hnet.comfychair.R
import sh.hnet.comfychair.cache.MediaStateHolder
import sh.hnet.comfychair.connection.ConnectionFailure
import sh.hnet.comfychair.repository.GalleryRepository
import sh.hnet.comfychair.ui.components.config.ReferenceConfig
import sh.hnet.comfychair.util.UuidUtils
import sh.hnet.comfychair.util.VideoUtils
import sh.hnet.comfychair.workflow.ReferenceMediaKind
import sh.hnet.comfychair.workflow.ReferenceWorkflowSupport
import sh.hnet.comfychair.workflow.ReferenceVideoDuration
import sh.hnet.comfychair.workflow.WorkflowConstraintsProvider
import sh.hnet.comfychair.workflow.ReferenceResolution

/** Workflow option for the shared reference generation screen. */
data class ReferenceWorkflowItem(
    val id: String,
    override val name: String,
    override val displayName: String,
    val type: WorkflowType
) : WorkflowItemBase

/**
 * Shared UI state for Reference-to-Image and Reference-to-Video.
 * Reference selections retain only document URIs and metadata, never decoded bitmaps.
 */
data class ReferenceGenerationUiState(
    val selectedWorkflow: String = "",
    val selectedWorkflowId: String = "",
    val availableWorkflows: List<ReferenceWorkflowItem> = emptyList(),
    val workflowPlaceholders: Set<String> = emptySet(),
    override val capabilities: WorkflowCapabilities = WorkflowCapabilities(),
    val positivePrompt: String = "",
    val negativePrompt: String = "",

    override val selectedCheckpoint: String = "",
    override val selectedUnet: String = "",
    override val selectedVae: String = "",
    override val selectedClip: String = "",
    override val selectedClip1: String = "",
    override val selectedClip2: String = "",
    override val selectedClip3: String = "",
    override val selectedClip4: String = "",
    override val selectedTextEncoder: String = "",
    override val selectedLatentUpscaleModel: String = "",
    override val selectedLoraName: String = "",

    val width: String = "1024",
    val height: String = "1024",
    val durationSeconds: String = "5",
    val useOriginalRatio: Boolean = true,
    val referenceAspectRatio: String? = null,
    val megapixels: String = "1.0",
    val megapixelsError: String? = null,
    val fps: String = "24",
    override val steps: String = "20",
    override val cfg: String = "7.0",
    override val sampler: String = "euler",
    override val scheduler: String = "normal",
    override val randomSeed: Boolean = true,
    override val seed: String = "0",
    override val denoise: String = "1.0",
    override val batchSize: String = "1",
    override val upscaleMethod: String = "nearest-exact",
    override val scaleBy: String = "1.5",
    override val stopAtClipLayer: String = "-1",
    override val loraChain: List<LoraSelection> = emptyList(),

    override val availableCheckpoints: List<String> = emptyList(),
    override val availableUnets: List<String> = emptyList(),
    override val availableVaes: List<String> = emptyList(),
    override val availableClips: List<String> = emptyList(),
    override val availableLoras: List<String> = emptyList(),
    override val availableTextEncoders: List<String> = emptyList(),
    override val availableLatentUpscaleModels: List<String> = emptyList(),
    override val availableUpscaleMethods: List<String> = emptyList(),
    override val availableSamplers: List<String> = emptyList(),
    override val availableSchedulers: List<String> = emptyList(),
    override val filteredCheckpoints: List<String>? = null,
    override val filteredUnets: List<String>? = null,
    override val filteredVaes: List<String>? = null,
    override val filteredClips: List<String>? = null,
    override val filteredClips1: List<String>? = null,
    override val filteredClips2: List<String>? = null,
    override val filteredClips3: List<String>? = null,
    override val filteredClips4: List<String>? = null,
    override val filteredTextEncoders: List<String>? = null,
    override val filteredLatentUpscaleModels: List<String>? = null,
    override val filteredLoras: List<String>? = null,

    val referenceImages: List<ReferenceMediaSelection> = emptyList(),
    val referenceVideos: List<ReferenceMediaSelection> = emptyList(),
    val sourceMedia: ReferenceMediaSelection? = null,
    val isUploading: Boolean = false,
    val isFetching: Boolean = false,
    val imageCapacity: Int = 0,
    val videoCapacity: Int = 0,
    val sourceDimensionsError: String? = null,
    val invalidReferenceUris: Set<String> = emptySet(),
    val previewBitmap: Bitmap? = null,
    val currentVideoUri: Uri? = null,
    val lastPromptId: String? = null,

    // Server-side file info of the shown result (needed to read the generation metadata)
    val currentImageFilename: String? = null,
    val currentImageSubfolder: String? = null,
    val currentImageType: String? = null,

    val widthError: String? = null,
    val heightError: String? = null,
    val durationError: String? = null,
    val fpsError: String? = null,
    override val stepsError: String? = null,
    override val cfgError: String? = null,
    override val seedError: String? = null,
    override val denoiseError: String? = null,
    override val batchSizeError: String? = null,
    override val scaleByError: String? = null,
    override val stopAtClipLayerError: String? = null,

    val deferredCheckpoint: String? = null,
    val deferredUnet: String? = null,
    val deferredVae: String? = null,
    val deferredClip: String? = null,
    val deferredClip1: String? = null,
    val deferredClip2: String? = null,
    val deferredClip3: String? = null,
    val deferredClip4: String? = null,
    val deferredTextEncoder: String? = null,
    val deferredLatentUpscaleModel: String? = null,
    val deferredLoraName: String? = null
) : CommonGenerationState

sealed class ReferenceGenerationEvent {
    data class ShowToast(val messageResId: Int) : ReferenceGenerationEvent()
    data class ShowToastMessage(val message: String) : ReferenceGenerationEvent()
}

/**
 * Owns the independent output configuration and workflow selection shared by both reference modes.
 * Media upload and prompt preparation are deliberately supplied by the later reference-I/O layer.
 */
class ReferenceGenerationViewModel(val type: WorkflowType) :
    BaseGenerationViewModel<ReferenceGenerationUiState, ReferenceGenerationEvent>() {
    override val initialState = ReferenceGenerationUiState()
    private var metadataGeneration = 0L

    init {
        require(type == WorkflowType.REF2I || type == WorkflowType.REF2V) {
            "ReferenceGenerationViewModel only supports REF2I and REF2V"
        }
        viewModelScope.launch {
            ConnectionManager.modelCache.collect { cache -> updateModels(cache) }
        }
        viewModelScope.launch {
            WorkflowManager.workflowsVersion.collect { loadWorkflows() }
        }
    }


    val ownerId: String
        get() = if (type == WorkflowType.REF2I) OWNER_ID_REF2I else OWNER_ID_REF2V

    val isVideoMode: Boolean
        get() = type == WorkflowType.REF2V

    override fun onInitialize() {
        loadWorkflows()
        restorePreferences()
        restoreResult()
    }

    private fun loadWorkflows() {
        val context = applicationContext ?: return
        val showBuiltIn = AppSettings.isShowBuiltInWorkflows(context)
        val workflows = WorkflowManager.getWorkflowsByType(type)
            .filter { showBuiltIn || !it.isBuiltIn }
            .map { ReferenceWorkflowItem(it.id, it.name, it.name, it.type) }
            .sortedBy { it.displayName }

        val previous = _uiState.value
        val selected = workflows.firstOrNull { it.id == previous.selectedWorkflowId }
            ?: workflows.firstOrNull { it.name == previous.selectedWorkflow }
            ?: workflows.firstOrNull()
        _uiState.value = previous.copy(
            availableWorkflows = workflows,
            selectedWorkflow = selected?.name.orEmpty(),
            selectedWorkflowId = selected?.id.orEmpty()
        )
        selected?.let(::loadWorkflowValues)
    }

    private fun loadWorkflowValues(workflow: ReferenceWorkflowItem) {
        val storage = workflowValuesStorage ?: return
        val serverId = ConnectionManager.currentServerId ?: return
        val saved = storage.loadValues(serverId, workflow.id)
        val defaults = WorkflowManager.getWorkflowDefaultsById(workflow.id)
        val placeholders = WorkflowManager.getWorkflowPlaceholders(workflow.id)
        val capabilities = WorkflowCapabilities.fromPlaceholders(placeholders)
        val cache = ConnectionManager.modelCache.value

        fun choose(savedValue: String?, current: String, available: List<String>) =
            savedValue?.takeIf(String::isNotEmpty)
                ?: current.takeIf(String::isNotEmpty)
                ?: validateModelSelection(current, available)

        val referenceImages = saved?.referenceImageUris.orEmpty().map { restoreMedia(it) }
        val referenceVideos = saved?.referenceVideoUris.orEmpty().map { restoreMedia(it) }
        val megapixels = restoredMegapixels(saved, defaults, workflow.name)
        val state = _uiState.value
        val savedModel = saved?.model
        val fps = saved?.frameRate ?: defaults?.frameRate ?: 24
        val duration = ReferenceVideoDuration.restoredSeconds(
            saved?.durationSeconds, saved?.length, fps
        ).takeIf { saved?.durationSeconds != null || saved?.length != null }
            ?: ReferenceVideoDuration.restoredSeconds(defaults?.durationSeconds, defaults?.length, fps)
        _uiState.value = state.copy(
            selectedWorkflow = workflow.name,
            selectedWorkflowId = workflow.id,
            workflowPlaceholders = placeholders,
            capabilities = capabilities,
            imageCapacity = referenceCapacity(workflow.id, ReferenceMediaKind.IMAGE),
            videoCapacity = referenceCapacity(workflow.id, ReferenceMediaKind.VIDEO),
            referenceImages = referenceImages,
            referenceVideos = referenceVideos,
            sourceMedia = preferredSource(referenceImages, referenceVideos),
            sourceDimensionsError = null,
            invalidReferenceUris = (saved?.referenceImageUris.orEmpty() + saved?.referenceVideoUris.orEmpty()).filterNot { readable(Uri.parse(it)) }.toSet(),
            negativePrompt = saved?.negativePrompt ?: defaults?.negativePrompt.orEmpty(),
            width = saved?.width?.toString() ?: defaults?.width?.toString() ?: defaultWidth(),
            height = saved?.height?.toString() ?: defaults?.height?.toString() ?: defaultHeight(),
            useOriginalRatio = saved?.useOriginalRatio ?: true,
            referenceAspectRatio = saved?.referenceAspectRatio,
            megapixels = megapixels,
            megapixelsError = validateMegapixels(megapixels, workflow.name),
            fps = fps.toString(),
            steps = saved?.steps?.toString() ?: defaults?.steps?.toString() ?: "20",
            cfg = saved?.cfg?.toString() ?: defaults?.cfg?.toString() ?: "7.0",
            sampler = saved?.samplerName ?: defaults?.samplerName ?: "euler",
            scheduler = saved?.scheduler ?: defaults?.scheduler ?: "normal",
            seed = saved?.seed?.toString() ?: defaults?.seed?.toString() ?: "0",
            randomSeed = saved?.randomSeed ?: true,
            denoise = saved?.denoise?.toString() ?: defaults?.denoise?.toString() ?: "1.0",
            batchSize = saved?.batchSize?.toString() ?: defaults?.batchSize?.toString() ?: "1",
            upscaleMethod = saved?.upscaleMethod ?: defaults?.upscaleMethod ?: "nearest-exact",
            scaleBy = saved?.scaleBy?.toString() ?: defaults?.scaleBy?.toString() ?: "1.5",
            stopAtClipLayer = saved?.stopAtClipLayer?.toString() ?: defaults?.stopAtClipLayer?.toString() ?: "-1",
            selectedCheckpoint = if (capabilities.hasCheckpointName) choose(savedModel, state.selectedCheckpoint, cache.checkpoints) else state.selectedCheckpoint,
            selectedUnet = if (capabilities.hasUnetName) choose(saved?.unetModel ?: savedModel, state.selectedUnet, cache.unets) else state.selectedUnet,
            selectedVae = choose(saved?.vaeModel, state.selectedVae, cache.vaes),
            selectedClip = choose(saved?.clipModel, state.selectedClip, cache.clips),
            selectedClip1 = choose(saved?.clip1Model, state.selectedClip1, cache.clips),
            selectedClip2 = choose(saved?.clip2Model, state.selectedClip2, cache.clips),
            selectedClip3 = choose(saved?.clip3Model, state.selectedClip3, cache.clips),
            selectedClip4 = choose(saved?.clip4Model, state.selectedClip4, cache.clips),
            selectedTextEncoder = choose(saved?.textEncoderModel, state.selectedTextEncoder, cache.textEncoders),
            selectedLatentUpscaleModel = choose(saved?.latentUpscaleModel, state.selectedLatentUpscaleModel, cache.latentUpscaleModels),
            selectedLoraName = choose(saved?.loraModel, state.selectedLoraName, cache.loras),
            loraChain = saved?.loraChain?.let(LoraSelection::fromJsonString) ?: emptyList(),
            deferredCheckpoint = if (capabilities.hasCheckpointName) savedModel else null,
            deferredUnet = if (capabilities.hasUnetName) saved?.unetModel ?: savedModel else null,
            deferredVae = saved?.vaeModel,
            deferredClip = saved?.clipModel,
            deferredClip1 = saved?.clip1Model,
            deferredClip2 = saved?.clip2Model,
            deferredClip3 = saved?.clip3Model,
            deferredClip4 = saved?.clip4Model,
            deferredTextEncoder = saved?.textEncoderModel,
            deferredLatentUpscaleModel = saved?.latentUpscaleModel,
            deferredLoraName = saved?.loraModel,
            filteredCheckpoints = WorkflowManager.getNodeSpecificOptionsForField(workflow.id, "ckpt_name"),
            filteredUnets = WorkflowManager.getNodeSpecificOptionsForField(workflow.id, "unet_name"),
            filteredVaes = WorkflowManager.getNodeSpecificOptionsForField(workflow.id, "vae_name"),
            filteredClips = WorkflowManager.getNodeSpecificOptionsForField(workflow.id, "clip_name"),
            filteredClips1 = WorkflowManager.getNodeSpecificOptionsForField(workflow.id, "clip_name1"),
            filteredClips2 = WorkflowManager.getNodeSpecificOptionsForField(workflow.id, "clip_name2"),
            filteredClips3 = WorkflowManager.getNodeSpecificOptionsForField(workflow.id, "clip_name3"),
            filteredClips4 = WorkflowManager.getNodeSpecificOptionsForField(workflow.id, "clip_name4"),
            filteredTextEncoders = WorkflowManager.getNodeSpecificOptionsForField(workflow.id, "text_encoder_name"),
            filteredLatentUpscaleModels = WorkflowManager.getNodeSpecificOptionsForField(workflow.id, "latent_upscale_model"),
            filteredLoras = WorkflowManager.getNodeSpecificOptionsForField(workflow.id, "lora_name"),
            widthError = validateNumber("width", saved?.width?.toString() ?: defaults?.width?.toString() ?: defaultWidth(), workflow.name),
            heightError = validateNumber("height", saved?.height?.toString() ?: defaults?.height?.toString() ?: defaultHeight(), workflow.name),
            durationError = if (capabilities.hasLength) validateDuration(duration.toString(), fps.toString(), workflow.name) else null,
            fpsError = if (capabilities.hasFrameRate) validateNumber("frame_rate", fps.toString(), workflow.name) else null
        )
        metadataGeneration++
        recalculateOriginalCanvas()
    }

    private fun updateModels(cache: sh.hnet.comfychair.connection.ModelCache) {
        if (cache.unets.isEmpty() && cache.checkpoints.isEmpty() && cache.clips.isEmpty() && cache.vaes.isEmpty()) return
        _uiState.update { state ->
            fun selected(deferred: String?, current: String, available: List<String>) =
                deferred?.takeIf(String::isNotEmpty) ?: current.takeIf(String::isNotEmpty)
                    ?: validateModelSelection(current, available)
            val checkpoints = selected(state.deferredCheckpoint, state.selectedCheckpoint, cache.checkpoints)
            val unets = selected(state.deferredUnet, state.selectedUnet, cache.unets)
            val vaes = selected(state.deferredVae, state.selectedVae, cache.vaes)
            val clips = selected(state.deferredClip, state.selectedClip, cache.clips)
            val clip1 = selected(state.deferredClip1, state.selectedClip1, cache.clips)
            val clip2 = selected(state.deferredClip2, state.selectedClip2, cache.clips)
            val clip3 = selected(state.deferredClip3, state.selectedClip3, cache.clips)
            val clip4 = selected(state.deferredClip4, state.selectedClip4, cache.clips)
            val textEncoder = selected(state.deferredTextEncoder, state.selectedTextEncoder, cache.textEncoders)
            val latentModel = selected(state.deferredLatentUpscaleModel, state.selectedLatentUpscaleModel, cache.latentUpscaleModels)
            val lora = selected(state.deferredLoraName, state.selectedLoraName, cache.loras)
            state.copy(
                availableCheckpoints = cache.checkpoints,
                availableUnets = cache.unets,
                availableVaes = cache.vaes,
                availableClips = cache.clips,
                availableLoras = cache.loras,
                availableTextEncoders = cache.textEncoders,
                availableLatentUpscaleModels = cache.latentUpscaleModels,
                availableUpscaleMethods = cache.upscaleMethods,
                availableSamplers = cache.samplers,
                availableSchedulers = cache.schedulers,
                selectedCheckpoint = checkpoints,
                selectedUnet = unets,
                selectedVae = vaes,
                selectedClip = state.deferredClip?.takeIf { it in cache.clips } ?: clips,
                selectedClip1 = clip1,
                selectedClip2 = clip2,
                selectedClip3 = clip3,
                selectedClip4 = clip4,
                selectedTextEncoder = textEncoder,
                selectedLatentUpscaleModel = latentModel,
                selectedLoraName = lora,
                deferredCheckpoint = null,
                deferredUnet = null,
                deferredVae = null,
                deferredClip = null,
                deferredClip1 = null,
                deferredClip2 = null,
                deferredClip3 = null,
                deferredClip4 = null,
                deferredTextEncoder = null,
                deferredLatentUpscaleModel = null,
                deferredLoraName = null,
                loraChain = LoraChainManager.filterUnavailable(state.loraChain, cache.loras)
            )
        }
    }

    private fun restorePreferences() {
        val context = applicationContext ?: return
        val serverId = ConnectionManager.currentServerId ?: return
        val prefs = context.getSharedPreferences(preferencesName(), Context.MODE_PRIVATE)
        val defaultPrompt = if (isVideoMode) SeasonalPrompts.getTextToVideoPrompt() else SeasonalPrompts.getTextToImagePrompt()
        _uiState.update {
            it.copy(positivePrompt = prefs.getString("${serverId}_positivePrompt", null) ?: defaultPrompt)
        }
        val savedWorkflowId = prefs.getString("${serverId}_selectedWorkflowId", "").orEmpty()
        val workflow = _uiState.value.availableWorkflows.firstOrNull { it.id == savedWorkflowId }
            ?: _uiState.value.availableWorkflows.firstOrNull()
        workflow?.let(::loadWorkflowValues)
    }

    private fun saveWorkflowValues(workflowId: String) {
        val storage = workflowValuesStorage ?: return
        val serverId = ConnectionManager.currentServerId ?: return
        val state = _uiState.value
        val old = storage.loadValues(serverId, workflowId)
        storage.saveValues(
            serverId,
            workflowId,
            WorkflowValues(
                width = state.width.toIntOrNull(),
                height = state.height.toIntOrNull(),
                durationSeconds = if (isVideoMode) state.durationSeconds.toIntOrNull() else null,
                megapixels = state.megapixels.toFloatOrNull(),
                useOriginalRatio = state.useOriginalRatio,
                referenceAspectRatio = state.referenceAspectRatio,
                frameRate = state.fps.toIntOrNull(),
                steps = state.steps.toIntOrNull(),
                cfg = state.cfg.toFloatOrNull(),
                samplerName = state.sampler,
                scheduler = state.scheduler,
                negativePrompt = state.negativePrompt.takeIf(String::isNotEmpty),
                model = state.selectedCheckpoint.takeIf(String::isNotEmpty) ?: state.selectedUnet.takeIf(String::isNotEmpty) ?: old?.model,
                unetModel = state.selectedUnet.takeIf(String::isNotEmpty) ?: old?.unetModel,
                vaeModel = state.selectedVae.takeIf(String::isNotEmpty) ?: old?.vaeModel,
                clipModel = state.selectedClip.takeIf(String::isNotEmpty) ?: old?.clipModel,
                clip1Model = state.selectedClip1.takeIf(String::isNotEmpty) ?: old?.clip1Model,
                clip2Model = state.selectedClip2.takeIf(String::isNotEmpty) ?: old?.clip2Model,
                clip3Model = state.selectedClip3.takeIf(String::isNotEmpty) ?: old?.clip3Model,
                clip4Model = state.selectedClip4.takeIf(String::isNotEmpty) ?: old?.clip4Model,
                textEncoderModel = state.selectedTextEncoder.takeIf(String::isNotEmpty) ?: old?.textEncoderModel,
                latentUpscaleModel = state.selectedLatentUpscaleModel.takeIf(String::isNotEmpty) ?: old?.latentUpscaleModel,
                loraModel = state.selectedLoraName.takeIf(String::isNotEmpty),
                loraChain = LoraSelection.toJsonString(state.loraChain).takeIf { state.loraChain.isNotEmpty() },
                seed = state.seed.toLongOrNull(),
                randomSeed = state.randomSeed,
                denoise = state.denoise.toFloatOrNull(),
                batchSize = state.batchSize.toIntOrNull(),
                upscaleMethod = state.upscaleMethod.takeIf(String::isNotEmpty),
                scaleBy = state.scaleBy.toFloatOrNull(),
                stopAtClipLayer = state.stopAtClipLayer.toIntOrNull(),
                nodeAttributeEdits = old?.nodeAttributeEdits,
                referenceImageUris = state.referenceImages.map { it.uri.toString() },
                referenceVideoUris = state.referenceVideos.map { it.uri.toString() }
            )
        )
    }

    private fun saveConfiguration() {
        val context = applicationContext ?: return
        val serverId = ConnectionManager.currentServerId ?: return
        val state = _uiState.value
        context.getSharedPreferences(preferencesName(), Context.MODE_PRIVATE).edit()
            .putString("${serverId}_positivePrompt", state.positivePrompt)
            .putString("${serverId}_selectedWorkflowId", state.selectedWorkflowId)
            .apply()
        state.selectedWorkflowId.takeIf(String::isNotEmpty)?.let(::saveWorkflowValues)
    }

    fun onWorkflowChange(name: String) {
        val state = _uiState.value
        val workflow = state.availableWorkflows.firstOrNull { it.name == name } ?: return
        state.selectedWorkflowId.takeIf(String::isNotEmpty)?.let(::saveWorkflowValues)
        metadataGeneration++
        loadWorkflowValues(workflow)
        saveConfiguration()
    }

    fun onPositivePromptChange(value: String) {
        _uiState.update { it.copy(positivePrompt = value) }
        saveConfiguration()
    }

    fun onNegativePromptChange(value: String) {
        _uiState.update { it.copy(negativePrompt = value) }
        saveConfiguration()
    }

    fun resetPromptToDefault() = onPositivePromptChange(
        if (isVideoMode) SeasonalPrompts.getTextToVideoPrompt() else SeasonalPrompts.getTextToImagePrompt()
    )

    fun onWidthChange(value: String) = onDimensionChange(value, editingWidth = true)
    fun onHeightChange(value: String) = onDimensionChange(value, editingWidth = false)

    private fun onDimensionChange(value: String, editingWidth: Boolean) {
        metadataGeneration++
        update { state ->
            val key = if (editingWidth) "width" else "height"
            val otherKey = if (editingWidth) "height" else "width"
            val error = validateNumber(key, value)
            val complement = if (error == null && state.megapixelsError == null) {
                ReferenceResolution.complementaryDimension(
                    value.toInt(), state.megapixels.toFloat(),
                    WorkflowConstraintsProvider.getConstraints(otherKey, state.selectedWorkflow)
                )?.toString()
            } else null
            state.copy(
                width = if (editingWidth) value else complement ?: state.width,
                height = if (!editingWidth) value else complement ?: state.height,
                widthError = if (editingWidth) error else if (complement != null) validateNumber("width", complement) else state.widthError,
                heightError = if (!editingWidth) error else if (complement != null) validateNumber("height", complement) else state.heightError,
                useOriginalRatio = false,
                referenceAspectRatio = null,
                sourceDimensionsError = null
            )
        }
    }
    fun onDurationChange(value: String) = update {
        it.copy(durationSeconds = value, durationError = validateDuration(value, it.fps))
    }
    fun onFpsChange(value: String) = update {
        it.copy(fps = value, fpsError = validateNumber("frame_rate", value),
            durationError = if (it.capabilities.hasLength) validateDuration(it.durationSeconds, value) else null)
    }
    fun onStepsChange(value: String) = update { it.copy(steps = value, stepsError = ValidationUtils.validateSteps(value, applicationContext)) }
    fun onCfgChange(value: String) = update { it.copy(cfg = value, cfgError = ValidationUtils.validateCfg(value, applicationContext)) }
    fun onSeedChange(value: String) = update { it.copy(seed = value, seedError = ValidationUtils.validateSeed(value, applicationContext)) }
    fun onDenoiseChange(value: String) = update { it.copy(denoise = value, denoiseError = ValidationUtils.validateDenoise(value, applicationContext)) }
    fun onBatchSizeChange(value: String) = update { it.copy(batchSize = value, batchSizeError = ValidationUtils.validateBatchSize(value, applicationContext)) }
    fun onScaleByChange(value: String) = update { it.copy(scaleBy = value, scaleByError = ValidationUtils.validateScaleBy(value, applicationContext)) }
    fun onStopAtClipLayerChange(value: String) = update { it.copy(stopAtClipLayer = value, stopAtClipLayerError = ValidationUtils.validateStopAtClipLayer(value, applicationContext)) }
    fun onSamplerChange(value: String) = update { it.copy(sampler = value) }
    fun onSchedulerChange(value: String) = update { it.copy(scheduler = value) }
    fun onUpscaleMethodChange(value: String) = update { it.copy(upscaleMethod = value) }
    fun onRandomSeedToggle() = update { it.copy(randomSeed = !it.randomSeed) }
    fun onRandomizeSeed() = onSeedChange(kotlin.random.Random.nextLong(0, Long.MAX_VALUE).toString())
    fun onUploadingChange(value: Boolean) { _uiState.update { it.copy(isUploading = value) } }
    fun onFetchingChange(value: Boolean) { _uiState.update { it.copy(isFetching = value) } }

    fun onCheckpointChange(value: String) = update { it.copy(selectedCheckpoint = value) }
    fun onUnetChange(value: String) = update { it.copy(selectedUnet = value) }
    fun onVaeChange(value: String) = update { it.copy(selectedVae = value) }
    fun onClipChange(value: String) = update { it.copy(selectedClip = value) }
    fun onClip1Change(value: String) = update { it.copy(selectedClip1 = value) }
    fun onClip2Change(value: String) = update { it.copy(selectedClip2 = value) }
    fun onClip3Change(value: String) = update { it.copy(selectedClip3 = value) }
    fun onClip4Change(value: String) = update { it.copy(selectedClip4 = value) }
    fun onTextEncoderChange(value: String) = update { it.copy(selectedTextEncoder = value) }
    fun onLatentUpscaleModelChange(value: String) = update { it.copy(selectedLatentUpscaleModel = value) }
    fun onMandatoryLoraChange(value: String) = update { it.copy(selectedLoraName = value) }

    fun onAddLora() = update { it.copy(loraChain = LoraChainManager.addLora(it.loraChain, it.availableLoras)) }
    fun onRemoveLora(index: Int) = update { it.copy(loraChain = LoraChainManager.removeLora(it.loraChain, index)) }
    fun onLoraNameChange(index: Int, name: String) = update { it.copy(loraChain = LoraChainManager.updateLoraName(it.loraChain, index, name)) }
    fun onLoraStrengthChange(index: Int, strength: Float) = update { it.copy(loraChain = LoraChainManager.updateLoraStrength(it.loraChain, index, strength)) }

    private fun referenceCapacity(id: String, kind: ReferenceMediaKind): Int = WorkflowManager.getWorkflowById(id)?.let {
        ReferenceWorkflowSupport.findSlots(JSONObject(it.jsonContent), type).getOrNull()?.count { slot -> slot.kind == kind }
    } ?: 0

    private fun readable(uri: Uri): Boolean = runCatching {
        applicationContext?.contentResolver?.openInputStream(uri)?.use { it.read() } != null
    }.getOrDefault(false)

    private fun restoreMedia(value: String): ReferenceMediaSelection = media(Uri.parse(value))

    private fun media(uri: Uri): ReferenceMediaSelection {
        val resolver = applicationContext?.contentResolver
        val mime = runCatching { resolver?.getType(uri) }.getOrNull().orEmpty()
        val name = runCatching {
            resolver?.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        return ReferenceMediaSelection(uri, name, mime)
    }

    private fun preferredSource(
        images: List<ReferenceMediaSelection>,
        videos: List<ReferenceMediaSelection>
    ): ReferenceMediaSelection? = if (isVideoMode) videos.firstOrNull() ?: images.firstOrNull() else images.firstOrNull()

    fun addReferences(kind: ReferenceMediaKind, uris: List<Uri>) {
        val filtered = uris.filter { uri ->
            val mime = runCatching { applicationContext?.contentResolver?.getType(uri) }.getOrNull().orEmpty()
            mime.startsWith(if (kind == ReferenceMediaKind.IMAGE) "image/" else "video/")
        }
        if (kind == ReferenceMediaKind.VIDEO && !isVideoMode) return
        addMixedReferences(filtered)
    }

    fun addMixedReferences(uris: List<Uri>) {
        val ordered = uris.map(::media)
        val state = _uiState.value
        if (state.isUploading) return
        val accepted = ordered.filter { it.mimeType.startsWith("image/") || isVideoMode && it.mimeType.startsWith("video/") }
        val images = accepted.filter { it.mimeType.startsWith("image/") }
            .take((state.imageCapacity - state.referenceImages.size).coerceAtLeast(0))
        val videos = accepted.filter { it.mimeType.startsWith("video/") }
            .take((state.videoCapacity - state.referenceVideos.size).coerceAtLeast(0))
        if (images.isEmpty() && videos.isEmpty()) {
            if (uris.isNotEmpty()) showNotAdded(uris.size)
            return
        }
        _uiState.update { current ->
            val nextImages = current.referenceImages + images
            val nextVideos = current.referenceVideos + videos
            current.copy(
                referenceImages = nextImages,
                referenceVideos = nextVideos,
                sourceMedia = preferredSource(nextImages, nextVideos),
                sourceDimensionsError = null,
                invalidReferenceUris = (nextImages + nextVideos).filterNot { readable(it.uri) }.map { it.uri.toString() }.toSet()
            )
        }
        metadataGeneration++
        recalculateOriginalCanvas()
        saveConfiguration()
        val rejected = uris.size - images.size - videos.size
        if (rejected > 0) showNotAdded(rejected)
    }


    private fun showNotAdded(count: Int) {
        viewModelScope.launch {
            applicationContext?.let { context ->
                _events.emit(ReferenceGenerationEvent.ShowToastMessage(context.getString(R.string.reference_not_added, count)))
            }
        }
    }


    fun replaceReference(kind: ReferenceMediaKind, index: Int, uri: Uri) {
        if (_uiState.value.isUploading) return
        val item = media(uri)
        if (!item.mimeType.startsWith(if (kind == ReferenceMediaKind.IMAGE) "image/" else "video/")) return
        val current = if (kind == ReferenceMediaKind.IMAGE) _uiState.value.referenceImages else _uiState.value.referenceVideos
        if (index in current.indices) setReferences(kind, current.toMutableList().also { it[index] = item })
    }

    fun removeReference(kind: ReferenceMediaKind, index: Int) {
        if (_uiState.value.isUploading) return
        val current = if (kind == ReferenceMediaKind.IMAGE) _uiState.value.referenceImages else _uiState.value.referenceVideos
        if (index in current.indices) setReferences(kind, current.toMutableList().also { it.removeAt(index) })
    }

    private fun setReferences(kind: ReferenceMediaKind, list: List<ReferenceMediaSelection>) {
        _uiState.update { state ->
            val images = if (kind == ReferenceMediaKind.IMAGE) list else state.referenceImages
            val videos = if (kind == ReferenceMediaKind.VIDEO) list else state.referenceVideos
            val next = state.copy(
                referenceImages = images,
                referenceVideos = videos,
                sourceMedia = preferredSource(images, videos),
                sourceDimensionsError = null
            )
            next.copy(invalidReferenceUris = (images + videos).filterNot { readable(it.uri) }.map { it.uri.toString() }.toSet())
        }
        metadataGeneration++
        recalculateOriginalCanvas()
        saveConfiguration()
    }

    private fun validateMegapixels(value: String, workflow: String = _uiState.value.selectedWorkflow): String? {
        val number = value.toFloatOrNull() ?: return applicationContext?.getString(R.string.error_invalid_number) ?: "Invalid number"
        val constraints = WorkflowConstraintsProvider.getConstraints("megapixels", workflow)
        return if (!number.isFinite() || number < constraints.min || number > constraints.max)
            applicationContext?.getString(R.string.error_invalid_number) ?: "Invalid number" else null
    }
    private fun restoredMegapixels(saved: WorkflowValues?, defaults: WorkflowDefaults?, workflow: String): String {
        saved?.megapixels?.let { return it.toString() }
        val value = defaults?.megapixels ?: 1f
        val constraints = WorkflowConstraintsProvider.getConstraints("megapixels", workflow)
        return if (value.isFinite()) value.coerceIn(constraints.min, constraints.max).toString() else "1.0"
    }

    private fun recalculateOriginalCanvas() {
        val state = _uiState.value
        val source = state.sourceMedia ?: return
        if (source.width <= 0 || source.height <= 0) {
            loadSourceDimensions(source)
            return
        }
        if (!state.useOriginalRatio || state.megapixelsError != null) return
        val dimensions = calculateCanvas(source.width, source.height, state.megapixels, state.selectedWorkflow) ?: return
        _uiState.update { current ->
            if (!current.useOriginalRatio || current.selectedWorkflowId != state.selectedWorkflowId ||
                current.sourceMedia?.uri != source.uri || current.megapixels != state.megapixels) current
            else current.copy(
                width = dimensions.width.toString(),
                height = dimensions.height.toString(),
                widthError = validateNumber("width", dimensions.width.toString(), state.selectedWorkflow),
                heightError = validateNumber("height", dimensions.height.toString(), state.selectedWorkflow),
                sourceDimensionsError = null
            )
        }
        saveConfiguration()
    }

    private fun calculateCanvas(width: Int, height: Int, megapixels: String, workflow: String): ReferenceResolution.Dimensions? {
        val mp = megapixels.toFloatOrNull() ?: return null
        return ReferenceResolution.calculateCanvas(
            width, height, mp,
            WorkflowConstraintsProvider.getConstraints("width", workflow),
            WorkflowConstraintsProvider.getConstraints("height", workflow)
        )
    }
    private fun loadSourceDimensions(source: ReferenceMediaSelection) {
        val workflowId = _uiState.value.selectedWorkflowId
        val generation = ++metadataGeneration
        viewModelScope.launch {
            val dimensions = withContext(Dispatchers.IO) { readMediaDimensions(source) }
            if (generation != metadataGeneration) return@launch
            val current = _uiState.value
            if (current.selectedWorkflowId != workflowId || current.sourceMedia?.uri != source.uri) return@launch
            if (dimensions == null) {
                _uiState.update { state ->
                    if (state.selectedWorkflowId == workflowId && state.sourceMedia?.uri == source.uri) {
                        state.copy(sourceDimensionsError = "Unable to read reference dimensions; keeping current canvas size")
                    } else state
                }
                return@launch
            }
            _uiState.update { state -> state.copy(sourceDimensionsError = null) }
            val updated = when {
                current.referenceVideos.any { it.uri == source.uri } -> current.referenceVideos.map { if (it.uri == source.uri) it.copy(width = dimensions.width, height = dimensions.height) else it }
                else -> current.referenceImages.map { if (it.uri == source.uri) it.copy(width = dimensions.width, height = dimensions.height) else it }
            }
            _uiState.update { state ->
                if (state.selectedWorkflowId != workflowId || state.sourceMedia?.uri != source.uri) state
                else {
                    val images = if (state.referenceImages.any { it.uri == source.uri }) updated else state.referenceImages
                    val videos = if (state.referenceVideos.any { it.uri == source.uri }) updated else state.referenceVideos
                    state.copy(referenceImages = images, referenceVideos = videos, sourceMedia = preferredSource(images, videos))
                }
            }
            recalculateOriginalCanvas()
            saveConfiguration()
        }
    }

    private fun readMediaDimensions(media: ReferenceMediaSelection): ReferenceResolution.Dimensions? = runCatching {
        val context = applicationContext ?: return null
        if (media.mimeType.startsWith("video/")) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, media.uri)
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                ReferenceResolution.orientedDimensions(width, height, rotation)
            } finally {
                retriever.release()
            }
        } else {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(media.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val orientation = resolver.openInputStream(media.uri)?.use { input ->
                ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
                else -> 0
            }
            ReferenceResolution.orientedDimensions(bounds.outWidth, bounds.outHeight, rotation)
        }
    }.getOrNull()
    private fun validateNumber(key: String, value: String, workflow: String = _uiState.value.selectedWorkflow): String? {
        val number = value.toIntOrNull() ?: return applicationContext?.getString(R.string.error_invalid_integer) ?: "Invalid integer"
        val constraints = WorkflowConstraintsProvider.getConstraints(key, workflow)
        return if (number < constraints.min || number > constraints.max ||
            (constraints.step > 1f && (number - constraints.min) % constraints.step != 0f))
            applicationContext?.getString(R.string.error_invalid_number) ?: "Invalid number" else null
    }

    private fun validateDuration(value: String, fps: String, workflow: String = _uiState.value.selectedWorkflow): String? {
        val frames = runCatching { ReferenceVideoDuration.frameCount(value.toInt(), fps.toInt()) }.getOrNull()
            ?: return applicationContext?.getString(R.string.error_invalid_number) ?: "Invalid number"
        val constraints = WorkflowConstraintsProvider.getConstraints("length", workflow)
        return if (frames < constraints.min || frames > constraints.max)
            applicationContext?.getString(R.string.error_invalid_number) ?: "Invalid number" else null
    }

    fun onOriginalRatioSelect() {
        metadataGeneration++
        _uiState.update { it.copy(useOriginalRatio = true, referenceAspectRatio = null) }
        recalculateOriginalCanvas()
        saveConfiguration()
    }

    fun onMegapixelsChange(value: String) {
        val error = validateMegapixels(value)
        metadataGeneration++
        _uiState.update { it.copy(megapixels = value, megapixelsError = error) }
        if (error == null) {
            val ratio = _uiState.value.referenceAspectRatio
            if (_uiState.value.useOriginalRatio) recalculateOriginalCanvas()
            else if (ratio != null) onResolutionPresetSelect(ratio)
            else onWidthChange(_uiState.value.width)
        }
        saveConfiguration()
    }
    fun onResolutionPresetSelect(ratio: String) {
        val (width, height) = when (ratio) {
            "2:3" -> 2 to 3
            "1:1" -> 1 to 1
            "3:2" -> 3 to 2
            else -> return
        }
        val state = _uiState.value
        val size = calculateCanvas(width, height, state.megapixels, state.selectedWorkflow) ?: return
        setWidthAndHeight(size.width.toString(), size.height.toString())
        update { it.copy(referenceAspectRatio = ratio) }
    }

    fun setWidthAndHeight(width: String, height: String) {
        metadataGeneration++
        _uiState.update { state ->
            state.copy(
                width = width,
                height = height,
                useOriginalRatio = false,
                referenceAspectRatio = null,
                sourceDimensionsError = null,
                widthError = validateNumber("width", width),
                heightError = validateNumber("height", height)
            )
        }
        saveConfiguration()
    }

    private inline fun update(crossinline transform: (ReferenceGenerationUiState) -> ReferenceGenerationUiState) {
        _uiState.update(transform)
        saveConfiguration()
    }

    override fun hasValidConfiguration(): Boolean {
        val state = _uiState.value
        return !state.isUploading &&
            !(state.useOriginalRatio && state.sourceMedia?.let { it.width <= 0 || it.height <= 0 } == true) &&
            !(state.useOriginalRatio && state.sourceMedia != null && state.sourceDimensionsError != null) &&
            (!state.useOriginalRatio || state.megapixelsError == null) && state.invalidReferenceUris.isEmpty() &&
            (state.referenceImages.isNotEmpty() || (isVideoMode && state.referenceVideos.isNotEmpty())) &&
            state.positivePrompt.isNotBlank() && state.selectedWorkflowId.isNotEmpty() &&
            state.widthError == null && state.heightError == null && state.durationError == null &&
            state.fpsError == null && state.stepsError == null && state.cfgError == null &&
            state.seedError == null && state.denoiseError == null && state.batchSizeError == null &&
            state.scaleByError == null && state.stopAtClipLayerError == null
    }

    fun referenceConfig(): ReferenceConfig = _uiState.value.let { state ->
        ReferenceConfig(state.referenceImages, state.referenceVideos, state.imageCapacity, state.videoCapacity,
            isVideoMode, !state.isUploading, ::addReferences, ::replaceReference, ::removeReference, ::addMixedReferences)
    }

    private data class UploadResult(val filename: String?, val error: String?, val failure: ConnectionFailure)

    fun generate(generation: GenerationViewModel, front: Boolean = false) {
        if (!hasValidConfiguration()) return
        val context = applicationContext ?: return
        val client = ConnectionManager.clientOrNull ?: return
        val server = ConnectionManager.currentServerId ?: return
        val snapshot = _uiState.value
        saveConfiguration()
        _uiState.update { it.copy(isUploading = true) }
        viewModelScope.launch {
            try {
                val uploaded = linkedMapOf<String, String>()
                for ((kind, selections) in listOf(ReferenceMediaKind.IMAGE to snapshot.referenceImages, ReferenceMediaKind.VIDEO to snapshot.referenceVideos)) {
                    for ((index, selection) in selections.withIndex()) {
                        require(ConnectionManager.currentServerId == server) { "Server changed during reference upload" }
                        require(readable(selection.uri)) { context.getString(R.string.reference_reselect) + " " + selection.displayName }
                        require(selection.mimeType.startsWith(if (kind == ReferenceMediaKind.IMAGE) "image/" else "video/")) { "Invalid media type: ${selection.displayName}" }
                        val key = ReferenceWorkflowSupport.mediaKey(kind, index + 1)
                        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(selection.mimeType)
                            ?: selection.displayName.substringAfterLast('.', if (kind == ReferenceMediaKind.IMAGE) "png" else "mp4")
                        val filename = UuidUtils.generateUniqueUploadFilename("${ownerId}_${key}", extension)
                        val result = suspendCancellableCoroutine<UploadResult> { continuation ->
                            client.uploadMedia(selection.uri, filename, selection.mimeType) { success, path, error, failure ->
                                if (continuation.isActive) continuation.resume(UploadResult(if (success) path else null, error, failure))
                            }
                        }
                        if (result.filename == null) {
                            if (result.failure == ConnectionFailure.AUTHENTICATION || result.failure == ConnectionFailure.STALLED) {
                                ConnectionManager.showConnectionAlert(context, result.failure)
                            }
                            error(result.error ?: "Upload failed: ${selection.displayName}")
                        }
                        uploaded[key] = result.filename
                    }
                }
                require(ConnectionManager.currentServerId == server) { "Server changed during reference upload" }
                val seed = if (snapshot.randomSeed) (0L..999999999999L).random() else snapshot.seed.toLong()
                val values = mapOf<String, Any>(
                    "positive_prompt" to snapshot.positivePrompt, "negative_prompt" to snapshot.negativePrompt,
                    "width" to snapshot.width.toInt(), "height" to snapshot.height.toInt(),
                    "duration_seconds" to snapshot.durationSeconds.toInt(), "frame_rate" to snapshot.fps.toInt(),
                    "steps" to snapshot.steps.toInt(), "cfg" to snapshot.cfg.toDouble(),
                    "seed" to seed, "sampler_name" to snapshot.sampler, "scheduler" to snapshot.scheduler,
                    "denoise" to snapshot.denoise.toDouble(), "batch_size" to snapshot.batchSize.toInt(),
                    "upscale_method" to snapshot.upscaleMethod, "scale_by" to snapshot.scaleBy.toDouble(),
                    "stop_at_clip_layer" to snapshot.stopAtClipLayer.toInt(),
                    "ckpt_name" to snapshot.selectedCheckpoint, "unet_name" to snapshot.selectedUnet,
                    "vae_name" to snapshot.selectedVae, "clip_name" to snapshot.selectedClip,
                    "clip_name1" to snapshot.selectedClip1, "clip_name2" to snapshot.selectedClip2,
                    "clip_name3" to snapshot.selectedClip3, "clip_name4" to snapshot.selectedClip4,
                    "text_encoder_name" to snapshot.selectedTextEncoder, "latent_upscale_model" to snapshot.selectedLatentUpscaleModel,
                    "lora_name" to snapshot.selectedLoraName
                )
                val prepared = WorkflowManager.prepareReferenceWorkflowById(snapshot.selectedWorkflowId, values, uploaded).getOrThrow()
                val result = suspendCancellableCoroutine<Triple<Boolean, String?, String?>> { continuation ->
                    generation.startGeneration(prepared, ownerId, if (isVideoMode) ContentType.VIDEO else ContentType.IMAGE, front) { ok, id, message ->
                        if (continuation.isActive) continuation.resume(Triple(ok, id, message))
                    }
                }
                if (!result.first) result.third?.let { error(it) }
                result.second?.let(::rememberPrompt)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _events.emit(ReferenceGenerationEvent.ShowToastMessage(error.message ?: "Reference generation failed"))
            } finally {
                _uiState.update { it.copy(isUploading = false) }
            }
        }
    }

    private val previewKey: MediaStateHolder.MediaKey
        get() = if (isVideoMode) MediaStateHolder.MediaKey.Ref2vPreview else MediaStateHolder.MediaKey.Ref2iPreview

    private fun rememberPrompt(id: String) {
        _uiState.update { it.copy(lastPromptId = id) }
        val server = ConnectionManager.currentServerId ?: return
        applicationContext?.getSharedPreferences(preferencesName(), Context.MODE_PRIVATE)?.edit()
            ?.putString("${server}_lastPromptId", id)?.apply()
    }

    /**
     * Remember which server file the shown result came from, so the metadata can still be
     * read after a restart. Scoped by server id like the rest of the reference state.
     */
    private fun rememberResultFile(promptId: String, filename: String, subfolder: String, type: String) {
        val server = ConnectionManager.currentServerId ?: return
        applicationContext?.getSharedPreferences(preferencesName(), Context.MODE_PRIVATE)?.edit()
            ?.putString("${server}_lastResultPromptId", promptId)
            ?.putString("${server}_lastResultFilename", filename)
            ?.putString("${server}_lastResultSubfolder", subfolder)
            ?.putString("${server}_lastResultType", type)
            ?.apply()
    }

    private fun restoreResult() {
        val context = applicationContext ?: return
        val server = ConnectionManager.currentServerId ?: return
        val prefs = context.getSharedPreferences(preferencesName(), Context.MODE_PRIVATE)
        val id = prefs.getString("${server}_lastPromptId", null)
        val savedFile = prefs.getString("${server}_lastResultFilename", null)
            ?.takeIf { prefs.getString("${server}_lastResultPromptId", null) == id }
        val savedSubfolder = prefs.getString("${server}_lastResultSubfolder", "")
        val savedType = prefs.getString("${server}_lastResultType", "output")
        // The gallery is server scoped too, so it is a safe fallback for a file info that
        // was not persisted (older state or a result restored from disk).
        val galleryFile = id?.let { promptId ->
            GalleryRepository.getInstance().galleryItems.value
                .firstOrNull { it.promptId == promptId && !it.isVideo }
        }
        val filename = savedFile ?: galleryFile?.filename
        val subfolder = savedFile?.let { savedSubfolder } ?: galleryFile?.subfolder.orEmpty()
        val type = savedFile?.let { savedType } ?: galleryFile?.type ?: "output"
        _uiState.update {
            it.copy(
                previewBitmap = MediaStateHolder.getBitmap(previewKey, context),
                lastPromptId = id,
                currentImageFilename = filename,
                currentImageSubfolder = subfolder,
                currentImageType = if (filename != null) type else null
            )
        }
        if (isVideoMode) viewModelScope.launch {
            val videoId = MediaStateHolder.getCurrentRef2vPromptId() ?: id ?: return@launch
            val uri = MediaStateHolder.getVideoUri(context, MediaStateHolder.MediaKey.Ref2vVideo(videoId))
            _uiState.update { it.copy(currentVideoUri = uri) }
        }
    }

    fun startListening(generation: GenerationViewModel) {
        generationViewModelRef = generation
        restoreResult()
        generation.registerEventHandler(ownerId) { event ->
            when (event) {
                is GenerationEvent.PreviewImage -> {
                    // A live preview is not a finished result: its file info no longer applies
                    _uiState.update {
                        it.copy(
                            previewBitmap = event.bitmap,
                            currentImageFilename = null,
                            currentImageSubfolder = null,
                            currentImageType = null
                        )
                    }
                    MediaStateHolder.putBitmap(previewKey, event.bitmap, applicationContext)
                }
                is GenerationEvent.ImageGenerated -> fetchResult(event.promptId, generation)
                is GenerationEvent.VideoGenerated -> fetchResult(event.promptId, generation)
                is GenerationEvent.Error -> viewModelScope.launch { _events.emit(ReferenceGenerationEvent.ShowToastMessage(event.message)) }
                else -> Unit
            }
        }
    }

    fun stopListening() { generationViewModelRef?.unregisterEventHandler(ownerId) }

    private fun fetchResult(id: String, generation: GenerationViewModel) {
        val context = applicationContext ?: return
        val client = ConnectionManager.clientOrNull ?: return
        val server = ConnectionManager.currentServerId
        rememberPrompt(id)
        _uiState.update { it.copy(isFetching = true) }
        if (isVideoMode) {
            VideoUtils.fetchVideoFromHistory(context, client, id, VideoUtils.FilePrefix.REFERENCE_TO_VIDEO) { uri ->
                if (ConnectionManager.currentServerId == server) {
                    _uiState.update { it.copy(currentVideoUri = uri, isFetching = false) }
                    generation.completeGeneration(id)
                }
            }
        } else client.fetchHistory(id) { history ->
            val outputs = history?.optJSONObject(id)?.optJSONObject("outputs")
            val image = HistoryMediaSelector.select(outputs, preferVideo = false)
            if (image == null) _uiState.update { it.copy(isFetching = false) }
            else client.fetchImage(image.filename, image.subfolder, image.type) { bitmap, failure ->
                viewModelScope.launch {
                    if (ConnectionManager.currentServerId == server) {
                        _uiState.update { it.copy(isFetching = false) }
                        if (bitmap != null) {
                            _uiState.update {
                                it.copy(
                                    previewBitmap = bitmap,
                                    currentImageFilename = image.filename,
                                    currentImageSubfolder = image.subfolder,
                                    currentImageType = image.type
                                )
                            }
                            rememberResultFile(id, image.filename, image.subfolder, image.type)
                            MediaStateHolder.putBitmap(previewKey, bitmap, context)
                            generation.completeGeneration(id)
                        } else if (failure == ConnectionFailure.STALLED || failure == ConnectionFailure.AUTHENTICATION) {
                            ConnectionManager.showConnectionAlert(context, failure)
                        }
                    }
                }
            }
        }
    }

    fun saveImage() {
        val context = applicationContext ?: return
        val bitmap = _uiState.value.previewBitmap ?: return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "ComfyMobile_REF2I_${System.currentTimeMillis()}.png")
                        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ComfyMobile")
                    }
                    val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Cannot create image")
                    context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
                }.getOrDefault(false)
            }
            _events.emit(ReferenceGenerationEvent.ShowToast(if (success) R.string.msg_image_saved_to_gallery else R.string.error_save_image))
        }
    }

    fun shareImage() {
        val context = applicationContext ?: return
        val bitmap = _uiState.value.previewBitmap ?: return
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    val file = java.io.File(context.cacheDir, "${ownerId}_share.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.share_image)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) { _events.emit(ReferenceGenerationEvent.ShowToast(R.string.error_share_image)) }
        }
    }

    private fun defaultWidth() = if (isVideoMode) "832" else "1024"
    private fun defaultHeight() = if (isVideoMode) "480" else "1024"
    private fun preferencesName() = if (isVideoMode) PREFS_REF2V else PREFS_REF2I

    companion object {
        const val OWNER_ID_REF2I = "ref2i"
        const val OWNER_ID_REF2V = "ref2v"
        private const val PREFS_REF2I = "ReferenceToImagePrefs"
        private const val PREFS_REF2V = "ReferenceToVideoPrefs"
    }
}

/** Uses the existing shared model, sampling, and LoRA config builders. */
fun ReferenceGenerationUiState.toBottomSheetConfig(
    callbacks: UnifiedCallbacks,
    referenceConfig: ReferenceConfig? = null
): BottomSheetConfig = BottomSheetConfig(
    referenceConfig = referenceConfig,
    workflow = WorkflowConfig(
        selectedWorkflow = selectedWorkflow,
        availableWorkflows = availableWorkflows,
        onWorkflowChange = callbacks.onWorkflowChange,
        onViewWorkflow = callbacks.onViewWorkflow
    ),
    prompts = PromptConfig(
        negativePrompt = negativePrompt,
        onNegativePromptChange = callbacks.onNegativePromptChange,
        hasNegativePrompt = capabilities.hasNegativePrompt
    ),
    models = buildCommonModelConfig(this, callbacks),
    parameters = buildCommonParameterConfig(
        state = this,
        callbacks = callbacks,
        // Output dimensions belong to the generation canvas, not to any selected reference media.
        widthField = sh.hnet.comfychair.ui.components.config.NumericField(
            value = width,
            onValueChange = callbacks.onWidthChange ?: {},
            error = widthError,
            isVisible = true
        ),
        heightField = sh.hnet.comfychair.ui.components.config.NumericField(
            value = height,
            onValueChange = callbacks.onHeightChange ?: {},
            error = heightError,
            isVisible = true
        ),
        megapixelsField = sh.hnet.comfychair.ui.components.config.NumericField(
            value = megapixels,
            onValueChange = callbacks.onMegapixelsChange ?: {},
            error = megapixelsError,
            isVisible = true
        ),
        fpsField = sh.hnet.comfychair.ui.components.config.NumericField(
            value = fps,
            onValueChange = callbacks.onFpsChange ?: {},
            error = fpsError,
            isVisible = capabilities.hasFrameRate
        )
    ).copy(durationSeconds = sh.hnet.comfychair.ui.components.config.NumericField(
        value = durationSeconds,
        onValueChange = callbacks.onLengthChange ?: {},
        error = durationError,
        isVisible = capabilities.hasLength
    )),
    lora = buildCommonLoraConfig(this, callbacks)
)
