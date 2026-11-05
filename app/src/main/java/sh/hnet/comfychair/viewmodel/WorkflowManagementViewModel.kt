package sh.hnet.comfychair.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import sh.hnet.comfychair.ComfyUIClient
import sh.hnet.comfychair.R
import sh.hnet.comfychair.WorkflowManager
import sh.hnet.comfychair.WorkflowType
import sh.hnet.comfychair.WorkflowValidationResult
import sh.hnet.comfychair.workflow.FieldCandidate
import sh.hnet.comfychair.workflow.FieldDisplayRegistry
import sh.hnet.comfychair.workflow.FieldMappingState
import sh.hnet.comfychair.workflow.PendingWorkflowUpload
import sh.hnet.comfychair.workflow.TemplateKeyRegistry
import sh.hnet.comfychair.workflow.WorkflowMappingState
import sh.hnet.comfychair.connection.ConnectionManager
import sh.hnet.comfychair.storage.AppSettings
import sh.hnet.comfychair.util.DebugLogger
import sh.hnet.comfychair.util.FieldMappingAnalyzer
import sh.hnet.comfychair.util.LiteGraphConverter
import sh.hnet.comfychair.util.ValidationUtils
import sh.hnet.comfychair.util.WorkflowJsonAnalyzer
import sh.hnet.comfychair.workflow.ReferenceWorkflowSupport
import sh.hnet.comfychair.workflow.WorkflowParser

/**
 * Export format options
 */
enum class ExportFormat {
    INTERNAL,  // Full internal format with placeholders, groups, notes
    API        // Raw ComfyUI API format with placeholders replaced
}

/**
 * UI state for the Workflow Management screen
 */
data class WorkflowManagementUiState(
    // Workflows organized by type
    val ttiWorkflows: List<WorkflowManager.Workflow> = emptyList(),
    val itiInpaintingWorkflows: List<WorkflowManager.Workflow> = emptyList(),
    val itiEditingWorkflows: List<WorkflowManager.Workflow> = emptyList(),
    val ttvWorkflows: List<WorkflowManager.Workflow> = emptyList(),
    val itvWorkflows: List<WorkflowManager.Workflow> = emptyList(),
    val ref2iWorkflows: List<WorkflowManager.Workflow> = emptyList(),
    val ref2vWorkflows: List<WorkflowManager.Workflow> = emptyList(),

    // Import dialog state
    val showImportDialog: Boolean = false,
    val pendingImportJsonContent: String = "",
    val importSelectedType: WorkflowType? = null,
    val importTypeDropdownExpanded: Boolean = false,
    val importName: String = "",
    val importDescription: String = "",
    val importNameError: String? = null,
    val importDescriptionError: String? = null,

    // Validation state
    val isValidatingNodes: Boolean = false,
    val showMissingNodesDialog: Boolean = false,
    val missingNodes: List<String> = emptyList(),
    val showMissingFieldsDialog: Boolean = false,
    val missingFields: List<String> = emptyList(),
    val showDuplicateNameDialog: Boolean = false,

    // Pending workflow for editor
    val pendingWorkflowForMapping: PendingWorkflowUpload? = null,

    // LiteGraph conversion warnings
    val pendingImportWarnings: List<String> = emptyList(),

    // Edit dialog state
    val showEditDialog: Boolean = false,
    val editingWorkflow: WorkflowManager.Workflow? = null,
    val editName: String = "",
    val editDescription: String = "",
    val editNameError: String? = null,
    val editDescriptionError: String? = null,

    // Delete confirmation dialog
    val showDeleteDialog: Boolean = false,
    val workflowToDelete: WorkflowManager.Workflow? = null,

    // Duplicate dialog state
    val showDuplicateDialog: Boolean = false,
    val duplicatingWorkflow: WorkflowManager.Workflow? = null,
    val duplicateName: String = "",
    val duplicateDescription: String = "",
    val duplicateNameError: String? = null,
    val duplicateDescriptionError: String? = null,

    // Export state
    val exportingWorkflow: WorkflowManager.Workflow? = null,
    val exportFormat: ExportFormat = ExportFormat.INTERNAL,

    // Loading state
    val isLoading: Boolean = false,

    // Import from ComfyUI's own workflow library (on the server PC)
    val showServerWorkflowsDialog: Boolean = false,
    val isLoadingServerWorkflows: Boolean = false,
    val serverWorkflows: List<String> = emptyList(),
    val serverWorkflowsError: Boolean = false
)

/**
 * Events emitted by the Workflow Management screen
 */
sealed class WorkflowManagementEvent {
    data class ShowToast(val messageResId: Int) : WorkflowManagementEvent()
    data class ShowToastMessage(val message: String) : WorkflowManagementEvent()
    data class LaunchEditor(val pendingUpload: PendingWorkflowUpload) : WorkflowManagementEvent()
    data class LaunchExportFilePicker(val suggestedFilename: String) : WorkflowManagementEvent()
    object WorkflowsChanged : WorkflowManagementEvent()
}

/**
 * ViewModel for the Workflow Management screen
 */
class WorkflowManagementViewModel : ViewModel() {

    companion object {
        private const val TAG = "WorkflowImport"
    }

    // State
    private val _uiState = MutableStateFlow(WorkflowManagementUiState())
    val uiState: StateFlow<WorkflowManagementUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<WorkflowManagementEvent>()
    val events: SharedFlow<WorkflowManagementEvent> = _events.asSharedFlow()

    private var applicationContext: Context? = null

    // Initialization
    /**
     * Initialize the ViewModel with context
     */
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        WorkflowManager.ensureInitialized(context)
        loadWorkflows()
    }

    /**
     * Load all workflows and organize by type
     */
    fun loadWorkflows() {
        val ctx = applicationContext ?: return
        val showBuiltIn = AppSettings.isShowBuiltInWorkflows(ctx)

        _uiState.value = _uiState.value.copy(
            ttiWorkflows = WorkflowManager.getWorkflowsByType(WorkflowType.TTI)
                .filter { showBuiltIn || !it.isBuiltIn },
            itiInpaintingWorkflows = WorkflowManager.getWorkflowsByType(WorkflowType.ITI_INPAINTING)
                .filter { showBuiltIn || !it.isBuiltIn },
            itiEditingWorkflows = WorkflowManager.getWorkflowsByType(WorkflowType.ITI_EDITING)
                .filter { showBuiltIn || !it.isBuiltIn },
            ttvWorkflows = WorkflowManager.getWorkflowsByType(WorkflowType.TTV)
                .filter { showBuiltIn || !it.isBuiltIn },
            itvWorkflows = WorkflowManager.getWorkflowsByType(WorkflowType.ITV)
                .filter { showBuiltIn || !it.isBuiltIn },
            ref2iWorkflows = WorkflowManager.getWorkflowsByType(WorkflowType.REF2I)
                .filter { showBuiltIn || !it.isBuiltIn },
            ref2vWorkflows = WorkflowManager.getWorkflowsByType(WorkflowType.REF2V)
                .filter { showBuiltIn || !it.isBuiltIn }
        )
    }

    /**
     * Reload workflows from disk and refresh UI.
     * Use this when workflows were modified by another component (e.g., WorkflowEditorViewModel).
     * Also emits WorkflowsChanged to notify parent activities (triggers generation screen refresh).
     */
    fun reloadAndRefreshWorkflows() {
        WorkflowManager.reloadWorkflows()
        loadWorkflows()
        viewModelScope.launch {
            _events.emit(WorkflowManagementEvent.WorkflowsChanged)
        }
    }

    // New import flow

    /**
     * Handle file selection from file picker - new flow without filename prefix requirement
     */
    fun onFileSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Read file content
                val jsonContent = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                }
                startImport(jsonContent, suggestedName = "")
            } catch (e: Exception) {
                _events.emit(WorkflowManagementEvent.ShowToast(R.string.error_workflow_import))
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    // Import from ComfyUI's workflow library

    /** Open the list of workflows saved in ComfyUI on the server PC. */
    fun openServerWorkflows() {
        val client = ConnectionManager.clientOrNull
        _uiState.value = _uiState.value.copy(
            showServerWorkflowsDialog = true,
            isLoadingServerWorkflows = client != null,
            serverWorkflows = emptyList(),
            serverWorkflowsError = client == null
        )
        client ?: return
        client.fetchSavedWorkflowList { paths ->
            _uiState.value = _uiState.value.copy(
                isLoadingServerWorkflows = false,
                serverWorkflows = paths ?: emptyList(),
                serverWorkflowsError = paths == null
            )
        }
    }

    fun dismissServerWorkflows() {
        _uiState.value = _uiState.value.copy(showServerWorkflowsDialog = false)
    }

    /** Download a ComfyUI-saved workflow and continue with the normal import flow. */
    fun importServerWorkflow(path: String) {
        val client = ConnectionManager.clientOrNull ?: return
        _uiState.value = _uiState.value.copy(showServerWorkflowsDialog = false, isLoading = true)
        client.fetchSavedWorkflow(path) { json ->
            viewModelScope.launch {
                if (json == null) {
                    _events.emit(WorkflowManagementEvent.ShowToast(R.string.error_workflow_import))
                    _uiState.value = _uiState.value.copy(isLoading = false)
                } else {
                    val name = path.substringAfterLast('/').removeSuffix(".json").removeSuffix(".JSON")
                    startImport(json, ValidationUtils.truncateWorkflowName(name))
                }
            }
        }
    }

    /** Shared import entry: validate JSON and show the import dialog. */
    private suspend fun startImport(jsonContent: String, suggestedName: String) {
        // Validate JSON format
        try {
            JSONObject(jsonContent)
        } catch (e: Exception) {
            _events.emit(WorkflowManagementEvent.ShowToast(R.string.error_workflow_invalid_json))
            _uiState.value = _uiState.value.copy(isLoading = false)
            return
        }

        // Auto-detect type as suggestion (user can override)
        val detectedType = WorkflowManager.detectWorkflowType(jsonContent)

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            showImportDialog = true,
            pendingImportJsonContent = jsonContent,
            importSelectedType = detectedType,
            importTypeDropdownExpanded = false,
            importName = suggestedName,
            importDescription = "",
            importNameError = null,
            importDescriptionError = null
        )
    }

    fun onImportTypeSelected(type: WorkflowType) {
        _uiState.value = _uiState.value.copy(
            importSelectedType = type,
            importTypeDropdownExpanded = false
        )
    }

    fun onToggleTypeDropdown() {
        _uiState.value = _uiState.value.copy(
            importTypeDropdownExpanded = !_uiState.value.importTypeDropdownExpanded
        )
    }

    fun onImportNameChange(name: String) {
        val error = if (name.length > ValidationUtils.MAX_WORKFLOW_NAME_LENGTH) {
            applicationContext?.getString(R.string.error_workflow_name_too_long)
        } else null
        _uiState.value = _uiState.value.copy(
            importName = ValidationUtils.truncateWorkflowName(name),
            importNameError = error
        )
    }

    fun onImportDescriptionChange(description: String) {
        val error = if (description.length > ValidationUtils.MAX_WORKFLOW_DESCRIPTION_LENGTH) {
            applicationContext?.getString(R.string.error_workflow_description_too_long)
        } else null
        _uiState.value = _uiState.value.copy(
            importDescription = ValidationUtils.truncateWorkflowDescription(description),
            importDescriptionError = error
        )
    }

    /**
     * Proceed with import - validate and launch editor
     */
    fun proceedWithImport(context: Context, comfyUIClient: ComfyUIClient) {
        val state = _uiState.value

        val selectedType = state.importSelectedType ?: return
        val name = state.importName.trim()
        val description = state.importDescription.trim()

        // Validate name format
        val nameError = WorkflowManager.validateWorkflowName(name)
        if (nameError != null) {
            _uiState.value = _uiState.value.copy(importNameError = nameError)
            return
        }

        // Validate description format
        val descError = WorkflowManager.validateWorkflowDescription(description)
        if (descError != null) {
            _uiState.value = _uiState.value.copy(importDescriptionError = descError)
            return
        }

        // Check for duplicate name
        if (WorkflowManager.isWorkflowNameTaken(name)) {
            _uiState.value = _uiState.value.copy(showDuplicateNameDialog = true)
            return
        }

        // Start node validation
        _uiState.value = _uiState.value.copy(isValidatingNodes = true)

        // Fetch available nodes from server
        comfyUIClient.fetchAllNodeTypes { availableNodes ->
            viewModelScope.launch {
                if (availableNodes == null) {
                    _events.emit(WorkflowManagementEvent.ShowToast(R.string.error_workflow_fetch_nodes_failed))
                    _uiState.value = _uiState.value.copy(isValidatingNodes = false)
                    return@launch
                }

                // Check if JSON is LiteGraph format and convert if needed
                var jsonContent = state.pendingImportJsonContent
                var conversionWarnings = emptyList<String>()

                try {
                    val jsonObject = JSONObject(jsonContent)
                    val isLiteGraph = WorkflowJsonAnalyzer.isLiteGraphFormat(jsonObject)
                    DebugLogger.i(TAG, "Import: format=${if (isLiteGraph) "LiteGraph" else "API/ComfyChair"}")

                    if (isLiteGraph) {
                        val converter = LiteGraphConverter { classType ->
                            ConnectionManager.nodeTypeRegistry.getNodeDefinition(classType)
                        }
                        val result = converter.convert(jsonObject)
                        jsonContent = result.jsonContent
                        conversionWarnings = result.warnings
                    }
                } catch (e: Exception) {
                    DebugLogger.e(TAG, "Import failed: ${e.message}")
                    _events.emit(WorkflowManagementEvent.ShowToast(R.string.error_workflow_invalid_json))
                    _uiState.value = _uiState.value.copy(isValidatingNodes = false)
                    return@launch
                }

                // Store conversion warnings
                if (conversionWarnings.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(pendingImportWarnings = conversionWarnings)
                }

                // Extract class types from workflow
                val classTypesResult = WorkflowManager.extractClassTypes(jsonContent)
                if (classTypesResult.isFailure) {
                    _events.emit(WorkflowManagementEvent.ShowToast(R.string.error_workflow_invalid_json))
                    _uiState.value = _uiState.value.copy(isValidatingNodes = false)
                    return@launch
                }
                val workflowClassTypes = classTypesResult.getOrThrow()

                // Validate nodes
                val missingNodes = WorkflowManager.validateNodesAgainstServer(workflowClassTypes, availableNodes)
                if (missingNodes.isNotEmpty()) {
                    DebugLogger.w(TAG, "Import blocked: ${missingNodes.size} missing nodes: ${missingNodes.joinToString()}")
                    _uiState.value = _uiState.value.copy(
                        isValidatingNodes = false,
                        showMissingNodesDialog = true,
                        missingNodes = missingNodes
                    )
                    return@launch
                }

                // Validate required fields for type
                val keyValidation = WorkflowManager.validateWorkflowKeys(jsonContent, selectedType)
                when (keyValidation) {
                    is WorkflowValidationResult.MissingKeys -> {
                        _uiState.value = _uiState.value.copy(
                            isValidatingNodes = false,
                            showMissingFieldsDialog = true,
                            missingFields = keyValidation.missing
                        )
                        return@launch
                    }
                    is WorkflowValidationResult.Success -> { /* Continue */ }
                    else -> { /* Continue - may have placeholders instead of keys */ }
                }

                // Create field mapping state
                val mappingState = createFieldMappingState(
                    context,
                    jsonContent,
                    selectedType
                )

                // Check if all REQUIRED fields are mapped (optional fields can be unmapped)
                if (!mappingState.allRequiredFieldsMapped) {
                    val unmappedFieldNames = mappingState.unmappedRequiredFields.map { it.displayName } +
                        if (!mappingState.hasValidReferenceMediaMapping) listOf(context.getString(R.string.reference_media_mapping_required)) else emptyList()
                    _uiState.value = _uiState.value.copy(
                        isValidatingNodes = false,
                        showMissingFieldsDialog = true,
                        missingFields = unmappedFieldNames
                    )
                    return@launch
                }

                // Prepare pending import and launch editor
                val pendingImport = PendingWorkflowUpload(
                    jsonContent = jsonContent,
                    name = name,
                    description = description,
                    type = selectedType,
                    mappingState = mappingState
                )

                _uiState.value = _uiState.value.copy(
                    isValidatingNodes = false,
                    showImportDialog = false,
                    pendingWorkflowForMapping = pendingImport
                )

                _events.emit(WorkflowManagementEvent.LaunchEditor(pendingImport))
            }
        }
    }

    /**
     * Create field mapping state by analyzing workflow JSON
     */
    private fun createFieldMappingState(context: Context, jsonContent: String, type: WorkflowType): WorkflowMappingState {
        val json = try {
            JSONObject(jsonContent)
        } catch (e: Exception) {
            return WorkflowMappingState(type, emptyList())
        }

        // Get strictly required keys and optional keys based on workflow structure
        val strictlyRequiredKeys = TemplateKeyRegistry.getStrictlyRequiredKeysForWorkflow(type, json)
        val optionalKeys = TemplateKeyRegistry.getOptionalKeysForWorkflow(type, json)
        val allKeys = strictlyRequiredKeys + optionalKeys

        val graph = WorkflowParser().parse(jsonContent)
        val referenceMappings = if (ReferenceWorkflowSupport.isReferenceType(type)) {
            ReferenceWorkflowSupport.findCandidates(graph, type, ConnectionManager.nodeTypeRegistry)
        } else emptyList()

        // For positive_text and negative_text, we need graph tracing to determine which CLIPTextEncode
        // connects to which sampler input (positive vs negative)
        val promptMappings = if (allKeys.contains("positive_text") || allKeys.contains("negative_text")) {
            FieldMappingAnalyzer.createPromptFieldMappings(graph, ConnectionManager.nodeTypeRegistry)
        } else {
            emptyMap()
        }

        val fieldMappings = allKeys.map { fieldKey ->
            val isRequired = fieldKey in strictlyRequiredKeys

            // Handle positive_text and negative_text specially with graph tracing results
            if (fieldKey == "positive_text" || fieldKey == "negative_text") {
                val candidates = promptMappings[fieldKey] ?: emptyList()
                FieldMappingState(
                    field = FieldDisplayRegistry.createRequiredField(context, fieldKey, isRequired),
                    candidates = candidates,
                    selectedCandidateIndex = if (candidates.isNotEmpty()) 0 else -1
                )
            } else {
                val candidates = FieldMappingAnalyzer.findCandidatesForField(
                    fieldKey, graph, promptMappings, ConnectionManager.nodeTypeRegistry
                ).filterNot { candidate ->
                    ReferenceWorkflowSupport.isReferenceType(type) &&
                        fieldKey in setOf("width", "height") &&
                        candidate.classType in setOf("LoadImage", "VHS_LoadVideo", "LoadVideo")
                }
                val canvasCandidates = if (ReferenceWorkflowSupport.isReferenceType(type) &&
                    fieldKey in setOf("width", "height")) {
                    candidates.sortedBy { candidate ->
                        when {
                            candidate.classType.contains("Latent", ignoreCase = true) -> 0
                            candidate.classType.contains("Reference", ignoreCase = true) -> 1
                            candidate.classType.contains("QwenImage21", ignoreCase = true) -> 1
                            candidate.classType.contains("MiniMaxH3ReferenceToVideo", ignoreCase = true) -> 1
                            else -> 2
                        }
                    }
                } else {
                    candidates
                }

                FieldMappingState(
                    field = FieldDisplayRegistry.createRequiredField(context, fieldKey, isRequired),
                    candidates = canvasCandidates,
                    selectedCandidateIndex = if (canvasCandidates.isNotEmpty()) 0 else -1
                )
            }
        }

        val localizedReferenceMappings = referenceMappings.map { mapping ->
            mapping.copy(
                field = FieldDisplayRegistry.createRequiredField(context, mapping.field.fieldKey, isRequired = false)
            )
        }

        return WorkflowMappingState(type, fieldMappings + localizedReferenceMappings)
    }


    fun cancelImport() {
        _uiState.value = _uiState.value.copy(
            showImportDialog = false,
            pendingImportJsonContent = "",
            importSelectedType = null,
            importName = "",
            importDescription = "",
            importNameError = null,
            importDescriptionError = null,
            pendingWorkflowForMapping = null,
            pendingImportWarnings = emptyList()
        )
    }

    fun dismissMissingNodesDialog() {
        _uiState.value = _uiState.value.copy(
            showMissingNodesDialog = false,
            missingNodes = emptyList(),
            showImportDialog = false
        )
        cancelImport()
    }

    fun dismissMissingFieldsDialog() {
        _uiState.value = _uiState.value.copy(
            showMissingFieldsDialog = false,
            missingFields = emptyList(),
            showImportDialog = false
        )
        cancelImport()
    }

    fun dismissDuplicateNameDialog() {
        _uiState.value = _uiState.value.copy(showDuplicateNameDialog = false)
    }

    /**
     * Complete the import after mapping is confirmed in editor
     */
    fun completeImport(fieldMappings: Map<String, Pair<String, String>>) {
        val pending = _uiState.value.pendingWorkflowForMapping ?: return
        val warnings = _uiState.value.pendingImportWarnings

        viewModelScope.launch {
            val result = WorkflowManager.addUserWorkflowWithMapping(
                name = pending.name,
                description = pending.description,
                jsonContent = pending.jsonContent,
                type = pending.type,
                fieldMappings = fieldMappings
            )

            if (result.isSuccess) {
                _events.emit(WorkflowManagementEvent.ShowToast(R.string.msg_workflow_import_success))
                // Show conversion warnings if any
                if (warnings.isNotEmpty()) {
                    val warningMessage = applicationContext?.getString(
                        R.string.msg_litegraph_conversion_notes,
                        warnings.size
                    ) ?: "Conversion completed with ${warnings.size} note(s)"
                    _events.emit(WorkflowManagementEvent.ShowToastMessage(warningMessage))
                }
                _events.emit(WorkflowManagementEvent.WorkflowsChanged)
                loadWorkflows()
            } else {
                _events.emit(WorkflowManagementEvent.ShowToastMessage(
                    result.exceptionOrNull()?.message ?: "Import failed"
                ))
            }

            _uiState.value = _uiState.value.copy(
                pendingWorkflowForMapping = null,
                pendingImportWarnings = emptyList()
            )
        }
    }

    /**
     * Cancel mapping and discard workflow
     */
    fun cancelMapping() {
        _uiState.value = _uiState.value.copy(pendingWorkflowForMapping = null)
    }

    // Edit flow

    fun onEditWorkflow(workflow: WorkflowManager.Workflow) {
        if (workflow.isBuiltIn) return

        _uiState.value = _uiState.value.copy(
            showEditDialog = true,
            editingWorkflow = workflow,
            editName = workflow.name,
            editDescription = workflow.description,
            editNameError = null,
            editDescriptionError = null
        )
    }

    fun onEditNameChange(name: String) {
        val error = if (name.length > ValidationUtils.MAX_WORKFLOW_NAME_LENGTH) {
            applicationContext?.getString(R.string.error_workflow_name_too_long)
        } else null
        _uiState.value = _uiState.value.copy(
            editName = ValidationUtils.truncateWorkflowName(name),
            editNameError = error
        )
    }

    fun onEditDescriptionChange(description: String) {
        val error = if (description.length > ValidationUtils.MAX_WORKFLOW_DESCRIPTION_LENGTH) {
            applicationContext?.getString(R.string.error_workflow_description_too_long)
        } else null
        _uiState.value = _uiState.value.copy(
            editDescription = ValidationUtils.truncateWorkflowDescription(description),
            editDescriptionError = error
        )
    }

    fun confirmEdit() {
        val state = _uiState.value
        val workflow = state.editingWorkflow ?: return

        val name = state.editName.trim()
        val description = state.editDescription.trim()

        // Validate name format
        val nameError = WorkflowManager.validateWorkflowName(name)
        if (nameError != null) {
            _uiState.value = _uiState.value.copy(editNameError = nameError)
            return
        }

        // Validate description format
        val descError = WorkflowManager.validateWorkflowDescription(description)
        if (descError != null) {
            _uiState.value = _uiState.value.copy(editDescriptionError = descError)
            return
        }

        // Check for duplicate name (excluding current workflow)
        if (WorkflowManager.isWorkflowNameTaken(name, excludeWorkflowId = workflow.id)) {
            _uiState.value = _uiState.value.copy(
                editNameError = applicationContext?.getString(R.string.msg_duplicate_name)
            )
            return
        }

        viewModelScope.launch {
            val success = WorkflowManager.updateUserWorkflowMetadata(
                workflowId = workflow.id,
                name = name,
                description = description
            )

            if (success) {
                _events.emit(WorkflowManagementEvent.ShowToast(R.string.msg_workflow_update_success))
                _events.emit(WorkflowManagementEvent.WorkflowsChanged)
                loadWorkflows()
            } else {
                _events.emit(WorkflowManagementEvent.ShowToast(R.string.error_workflow_update))
            }

            _uiState.value = _uiState.value.copy(
                showEditDialog = false,
                editingWorkflow = null,
                editName = "",
                editDescription = "",
                editNameError = null,
                editDescriptionError = null
            )
        }
    }

    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(
            showEditDialog = false,
            editingWorkflow = null,
            editName = "",
            editDescription = "",
            editNameError = null,
            editDescriptionError = null
        )
    }

    // Delete flow

    fun onDeleteWorkflow(workflow: WorkflowManager.Workflow) {
        if (workflow.isBuiltIn) return

        _uiState.value = _uiState.value.copy(
            showDeleteDialog = true,
            workflowToDelete = workflow
        )
    }

    fun confirmDelete() {
        val state = _uiState.value
        val workflow = state.workflowToDelete ?: return

        viewModelScope.launch {
            val success = WorkflowManager.deleteUserWorkflow(workflow.id)

            if (success) {
                _events.emit(WorkflowManagementEvent.ShowToast(R.string.msg_workflow_delete_success))
                _events.emit(WorkflowManagementEvent.WorkflowsChanged)
                loadWorkflows()
            } else {
                _events.emit(WorkflowManagementEvent.ShowToast(R.string.error_workflow_delete))
            }

            _uiState.value = _uiState.value.copy(
                showDeleteDialog = false,
                workflowToDelete = null
            )
        }
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(
            showDeleteDialog = false,
            workflowToDelete = null
        )
    }

    // Duplicate flow

    fun onDuplicateWorkflow(workflow: WorkflowManager.Workflow) {
        // Generate a unique name for the duplicate
        val suggestedName = WorkflowManager.generateUniqueDuplicateName(workflow.name)

        _uiState.value = _uiState.value.copy(
            showDuplicateDialog = true,
            duplicatingWorkflow = workflow,
            duplicateName = suggestedName,
            duplicateDescription = workflow.description,
            duplicateNameError = null
        )
    }

    fun onDuplicateNameChange(name: String) {
        val error = if (name.length > ValidationUtils.MAX_WORKFLOW_NAME_LENGTH) {
            applicationContext?.getString(R.string.error_workflow_name_too_long)
        } else null
        _uiState.value = _uiState.value.copy(
            duplicateName = ValidationUtils.truncateWorkflowName(name),
            duplicateNameError = error
        )
    }

    fun onDuplicateDescriptionChange(description: String) {
        val error = if (description.length > ValidationUtils.MAX_WORKFLOW_DESCRIPTION_LENGTH) {
            applicationContext?.getString(R.string.error_workflow_description_too_long)
        } else null
        _uiState.value = _uiState.value.copy(
            duplicateDescription = ValidationUtils.truncateWorkflowDescription(description),
            duplicateDescriptionError = error
        )
    }

    fun confirmDuplicate() {
        val state = _uiState.value
        val workflow = state.duplicatingWorkflow ?: return

        val name = state.duplicateName.trim()
        val description = state.duplicateDescription.trim()

        // Validate name
        val nameError = WorkflowManager.validateWorkflowName(name)
        if (nameError != null) {
            _uiState.value = _uiState.value.copy(duplicateNameError = nameError)
            return
        }

        // Validate description
        val descError = WorkflowManager.validateWorkflowDescription(description)
        if (descError != null) {
            _uiState.value = _uiState.value.copy(duplicateDescriptionError = descError)
            return
        }

        // Check for duplicate name
        if (WorkflowManager.isWorkflowNameTaken(name)) {
            _uiState.value = _uiState.value.copy(duplicateNameError = applicationContext?.getString(R.string.msg_duplicate_name))
            return
        }

        viewModelScope.launch {
            val result = WorkflowManager.duplicateWorkflow(
                sourceWorkflowId = workflow.id,
                newName = name,
                newDescription = description
            )

            if (result.isSuccess) {
                _events.emit(WorkflowManagementEvent.ShowToast(R.string.msg_workflow_duplicate_success))
                _events.emit(WorkflowManagementEvent.WorkflowsChanged)
                loadWorkflows()
            } else {
                _events.emit(WorkflowManagementEvent.ShowToastMessage(
                    result.exceptionOrNull()?.message ?: "Duplicate failed"
                ))
            }

            _uiState.value = _uiState.value.copy(
                showDuplicateDialog = false,
                duplicatingWorkflow = null,
                duplicateName = "",
                duplicateDescription = "",
                duplicateNameError = null,
                duplicateDescriptionError = null
            )
        }
    }

    fun cancelDuplicate() {
        _uiState.value = _uiState.value.copy(
            showDuplicateDialog = false,
            duplicatingWorkflow = null,
            duplicateName = "",
            duplicateDescription = "",
            duplicateNameError = null,
            duplicateDescriptionError = null
        )
    }

    // Export flow

    fun onExportWorkflow(workflow: WorkflowManager.Workflow, format: ExportFormat) {
        val suggestedFilename = WorkflowManager.generateExportFilename(workflow.name)

        _uiState.value = _uiState.value.copy(
            exportingWorkflow = workflow,
            exportFormat = format
        )

        viewModelScope.launch {
            _events.emit(WorkflowManagementEvent.LaunchExportFilePicker(suggestedFilename))
        }
    }

    fun performExport(context: Context, uri: Uri) {
        val workflow = _uiState.value.exportingWorkflow ?: return
        val format = _uiState.value.exportFormat

        viewModelScope.launch {
            val result = when (format) {
                ExportFormat.INTERNAL -> WorkflowManager.exportWorkflowInternal(workflow.id)
                ExportFormat.API -> WorkflowManager.exportWorkflowToComfyUIFormat(workflow.id)
            }

            if (result.isSuccess) {
                val jsonContent = result.getOrThrow()
                val writeSuccess = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(jsonContent.toByteArray(Charsets.UTF_8))
                        }
                        true
                    } catch (e: Exception) {
                        false
                    }
                }

                if (writeSuccess) {
                    _events.emit(WorkflowManagementEvent.ShowToast(R.string.msg_workflow_export_success))
                } else {
                    _events.emit(WorkflowManagementEvent.ShowToast(R.string.error_workflow_export))
                }
            } else {
                _events.emit(WorkflowManagementEvent.ShowToastMessage(
                    result.exceptionOrNull()?.message ?: "Export failed"
                ))
            }

            _uiState.value = _uiState.value.copy(exportingWorkflow = null)
        }
    }

    fun cancelExport() {
        _uiState.value = _uiState.value.copy(exportingWorkflow = null)
    }
}
