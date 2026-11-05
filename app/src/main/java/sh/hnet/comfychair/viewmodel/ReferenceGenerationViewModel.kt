package sh.hnet.comfychair.viewmodel

import android.content.Context
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
import sh.hnet.comfychair.ui.components.config.ReferenceConfig
import sh.hnet.comfychair.util.UuidUtils
import sh.hnet.comfychair.util.VideoUtils
import sh.hnet.comfychair.workflow.ReferenceMediaKind
import sh.hnet.comfychair.workflow.ReferenceWorkflowSupport
import sh.hnet.comfychair.workflow.WorkflowConstraintsProvider

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
    val length: String = "33",
    val fps: String = "16",
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
    val isUploading: Boolean = false,
    val isFetching: Boolean = false,
    val imageCapacity: Int = 0,
    val videoCapacity: Int = 0,
    val invalidReferenceUris: Set<String> = emptySet(),
    val previewBitmap: Bitmap? = null,
    val currentVideoUri: Uri? = null,
    val lastPromptId: String? = null,

    val widthError: String? = null,
    val heightError: String? = null,
    val lengthError: String? = null,
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
            savedValue?.takeIf { it in available }
                ?: validateModelSelection(current, available)

        val state = _uiState.value
        val savedModel = saved?.model
        _uiState.value = state.copy(
            selectedWorkflow = workflow.name,
            selectedWorkflowId = workflow.id,
            workflowPlaceholders = placeholders,
            capabilities = capabilities,
            imageCapacity = referenceCapacity(workflow.id, ReferenceMediaKind.IMAGE),
            videoCapacity = referenceCapacity(workflow.id, ReferenceMediaKind.VIDEO),
            referenceImages = saved?.referenceImageUris.orEmpty().map { restoreMedia(it) },
            referenceVideos = saved?.referenceVideoUris.orEmpty().map { restoreMedia(it) },
            invalidReferenceUris = (saved?.referenceImageUris.orEmpty() + saved?.referenceVideoUris.orEmpty()).filterNot { readable(Uri.parse(it)) }.toSet(),
            negativePrompt = saved?.negativePrompt ?: defaults?.negativePrompt.orEmpty(),
            width = saved?.width?.toString() ?: defaults?.width?.toString() ?: defaultWidth(),
            height = saved?.height?.toString() ?: defaults?.height?.toString() ?: defaultHeight(),
            length = saved?.length?.toString() ?: defaults?.length?.toString() ?: "33",
            fps = saved?.frameRate?.toString() ?: defaults?.frameRate?.toString() ?: "16",
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
            lengthError = if (capabilities.hasLength) validateNumber("length", saved?.length?.toString() ?: defaults?.length?.toString() ?: "33", workflow.name) else null,
            fpsError = if (capabilities.hasFrameRate) validateNumber("frame_rate", saved?.frameRate?.toString() ?: defaults?.frameRate?.toString() ?: "16", workflow.name) else null
        )
    }

    private fun updateModels(cache: sh.hnet.comfychair.connection.ModelCache) {
        _uiState.update { state ->
            val checkpoints = state.deferredCheckpoint?.takeIf { it in cache.checkpoints }
                ?: validateModelSelection(state.selectedCheckpoint, cache.checkpoints)
            val unets = state.deferredUnet?.takeIf { it in cache.unets }
                ?: validateModelSelection(state.selectedUnet, cache.unets)
            val vaes = state.deferredVae?.takeIf { it in cache.vaes }
                ?: validateModelSelection(state.selectedVae, cache.vaes)
            val clips = validateModelSelection(state.selectedClip, cache.clips)
            val clip1 = state.deferredClip1?.takeIf { it in cache.clips } ?: validateModelSelection(state.selectedClip1, cache.clips)
            val clip2 = state.deferredClip2?.takeIf { it in cache.clips } ?: validateModelSelection(state.selectedClip2, cache.clips)
            val clip3 = state.deferredClip3?.takeIf { it in cache.clips } ?: validateModelSelection(state.selectedClip3, cache.clips)
            val clip4 = state.deferredClip4?.takeIf { it in cache.clips } ?: validateModelSelection(state.selectedClip4, cache.clips)
            val textEncoder = state.deferredTextEncoder?.takeIf { it in cache.textEncoders }
                ?: validateModelSelection(state.selectedTextEncoder, cache.textEncoders)
            val latentModel = state.deferredLatentUpscaleModel?.takeIf { it in cache.latentUpscaleModels }
                ?: validateModelSelection(state.selectedLatentUpscaleModel, cache.latentUpscaleModels)
            val lora = state.deferredLoraName?.takeIf { it in cache.loras }
                ?: validateModelSelection(state.selectedLoraName, cache.loras)
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
                length = state.length.toIntOrNull(),
                frameRate = state.fps.toIntOrNull(),
                steps = state.steps.toIntOrNull(),
                cfg = state.cfg.toFloatOrNull(),
                samplerName = state.sampler,
                scheduler = state.scheduler,
                negativePrompt = state.negativePrompt.takeIf(String::isNotEmpty),
                model = state.selectedCheckpoint.takeIf(String::isNotEmpty) ?: state.selectedUnet.takeIf(String::isNotEmpty),
                unetModel = state.selectedUnet.takeIf(String::isNotEmpty),
                vaeModel = state.selectedVae.takeIf(String::isNotEmpty),
                clipModel = state.selectedClip.takeIf(String::isNotEmpty),
                clip1Model = state.selectedClip1.takeIf(String::isNotEmpty),
                clip2Model = state.selectedClip2.takeIf(String::isNotEmpty),
                clip3Model = state.selectedClip3.takeIf(String::isNotEmpty),
                clip4Model = state.selectedClip4.takeIf(String::isNotEmpty),
                textEncoderModel = state.selectedTextEncoder.takeIf(String::isNotEmpty),
                latentUpscaleModel = state.selectedLatentUpscaleModel.takeIf(String::isNotEmpty),
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

    fun onWidthChange(value: String) = update { it.copy(width = value, widthError = validateNumber("width", value)) }
    fun onHeightChange(value: String) = update { it.copy(height = value, heightError = validateNumber("height", value)) }
    fun onLengthChange(value: String) = update { it.copy(length = value, lengthError = validateNumber("length", value)) }
    fun onFpsChange(value: String) = update { it.copy(fps = value, fpsError = validateNumber("frame_rate", value)) }
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

    fun addReferences(kind: ReferenceMediaKind, uris: List<Uri>) {
        if (_uiState.value.isUploading || (kind == ReferenceMediaKind.VIDEO && !isVideoMode)) return
        val state = _uiState.value
        val current = if (kind == ReferenceMediaKind.IMAGE) state.referenceImages else state.referenceVideos
        val capacity = if (kind == ReferenceMediaKind.IMAGE) state.imageCapacity else state.videoCapacity
        val accepted = uris.map(::media).filter { it.mimeType.startsWith(if (kind == ReferenceMediaKind.IMAGE) "image/" else "video/") }
            .take((capacity - current.size).coerceAtLeast(0))
        setReferences(kind, current + accepted)
        if (accepted.size < uris.size) viewModelScope.launch {
            _events.emit(ReferenceGenerationEvent.ShowToastMessage(applicationContext!!.getString(R.string.reference_not_added, uris.size - accepted.size)))
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
            val next = if (kind == ReferenceMediaKind.IMAGE) state.copy(referenceImages = list) else state.copy(referenceVideos = list)
            next.copy(invalidReferenceUris = (next.referenceImages + next.referenceVideos).filterNot { readable(it.uri) }.map { it.uri.toString() }.toSet())
        }
        saveConfiguration()
    }

    private fun validateNumber(key: String, value: String, workflow: String = _uiState.value.selectedWorkflow): String? {
        val number = value.toIntOrNull() ?: return applicationContext?.getString(R.string.error_invalid_integer) ?: "Invalid integer"
        val constraints = WorkflowConstraintsProvider.getConstraints(key, workflow)
        return if (number < constraints.min || number > constraints.max ||
            (constraints.step > 1f && (number - constraints.min) % constraints.step != 0f))
            applicationContext?.getString(R.string.error_invalid_number) ?: "Invalid number" else null
    }

    fun setWidthAndHeight(width: String, height: String) {
        _uiState.update {
            it.copy(
                width = width,
                height = height,
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
        return !state.isUploading && state.invalidReferenceUris.isEmpty() &&
            (state.referenceImages.isNotEmpty() || (isVideoMode && state.referenceVideos.isNotEmpty())) &&
            state.positivePrompt.isNotBlank() && state.selectedWorkflowId.isNotEmpty() &&
            state.widthError == null && state.heightError == null && state.lengthError == null &&
            state.fpsError == null && state.stepsError == null && state.cfgError == null &&
            state.seedError == null && state.denoiseError == null && state.batchSizeError == null &&
            state.scaleByError == null && state.stopAtClipLayerError == null
    }

    fun referenceConfig(): ReferenceConfig = _uiState.value.let { state ->
        ReferenceConfig(state.referenceImages, state.referenceVideos, state.imageCapacity, state.videoCapacity,
            isVideoMode, !state.isUploading, ::addReferences, ::replaceReference, ::removeReference)
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
                    "length" to snapshot.length.toInt(), "frame_rate" to snapshot.fps.toInt(),
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

    private fun restoreResult() {
        val context = applicationContext ?: return
        val server = ConnectionManager.currentServerId ?: return
        val id = context.getSharedPreferences(preferencesName(), Context.MODE_PRIVATE).getString("${server}_lastPromptId", null)
        _uiState.update { it.copy(previewBitmap = MediaStateHolder.getBitmap(previewKey, context), lastPromptId = id) }
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
                    _uiState.update { it.copy(previewBitmap = event.bitmap) }
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
                        _uiState.update { it.copy(previewBitmap = bitmap ?: it.previewBitmap, isFetching = false) }
                        if (bitmap != null) { MediaStateHolder.putBitmap(previewKey, bitmap, context); generation.completeGeneration(id) }
                        else if (failure == ConnectionFailure.STALLED || failure == ConnectionFailure.AUTHENTICATION) ConnectionManager.showConnectionAlert(context, failure)
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
        lengthField = sh.hnet.comfychair.ui.components.config.NumericField(
            value = length,
            onValueChange = callbacks.onLengthChange ?: {},
            error = lengthError,
            isVisible = capabilities.hasLength
        ),
        fpsField = sh.hnet.comfychair.ui.components.config.NumericField(
            value = fps,
            onValueChange = callbacks.onFpsChange ?: {},
            error = fpsError,
            isVisible = capabilities.hasFrameRate
        )
    ),
    lora = buildCommonLoraConfig(this, callbacks)
)
