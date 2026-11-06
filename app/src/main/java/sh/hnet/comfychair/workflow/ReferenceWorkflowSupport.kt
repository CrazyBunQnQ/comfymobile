package sh.hnet.comfychair.workflow

import org.json.JSONArray
import org.json.JSONObject
import sh.hnet.comfychair.WorkflowType

/** Reference numbering is per media kind, one-based, and independent of output dimensions. */
enum class ReferenceMediaKind { IMAGE, VIDEO }

data class ReferenceSlot(val kind: ReferenceMediaKind, val index: Int, val nodeId: String, val inputKey: String) {
    val key: String get() = ReferenceWorkflowSupport.mediaKey(kind, index)
}

object ReferenceWorkflowSupport {
    private val keyPattern = Regex("reference_(image|video)_([1-9][0-9]*)")
    private val placeholderPattern = Regex("\\{\\{(reference_(?:image|video)_[0-9]+)\\}\\}")
    private val numericSuffix = Regex("[0-9]+")

    fun isReferenceType(type: WorkflowType): Boolean = type == WorkflowType.REF2I || type == WorkflowType.REF2V

    fun mediaKey(kind: ReferenceMediaKind, index: Int): String =
        "reference_${kind.name.lowercase()}_$index"

    fun parseKey(key: String): Pair<ReferenceMediaKind, Int>? {
        val match = keyPattern.matchEntire(key) ?: return null
        val index = match.groupValues[2].toIntOrNull() ?: return null
        return (if (match.groupValues[1] == "image") ReferenceMediaKind.IMAGE else ReferenceMediaKind.VIDEO) to index
    }

    private fun loader(classType: String): Pair<ReferenceMediaKind, String>? = when (classType) {
        "LoadImage" -> ReferenceMediaKind.IMAGE to "image"
        "VHS_LoadVideo" -> ReferenceMediaKind.VIDEO to "video"
        "LoadVideo" -> ReferenceMediaKind.VIDEO to "file"
        else -> null
    }

    fun findSlots(json: JSONObject, type: WorkflowType): Result<List<ReferenceSlot>> = runCatching {
        require(isReferenceType(type)) { "Not a reference workflow: $type" }
        val nodes = json.optJSONObject("nodes") ?: json
        val slots = mutableListOf<ReferenceSlot>()
        for (nodeId in nodes.keys()) {
            val node = nodes.optJSONObject(nodeId) ?: continue
            val inputs = node.optJSONObject("inputs") ?: continue
            for (inputKey in inputs.keys()) {
                val value = inputs.opt(inputKey) as? String ?: continue
                if (!value.contains("{{reference_")) continue
                val match = placeholderPattern.matchEntire(value)
                    ?: error("Node $nodeId.$inputKey: reference filename must be a complete numbered placeholder")
                val (kind, index) = parseKey(match.groupValues[1])
                    ?: error("Node $nodeId.$inputKey: invalid reference index")
                require(loader(node.optString("class_type")) == (kind to inputKey)) {
                    "Node $nodeId.$inputKey: reference placeholder must map to a matching media loader filename"
                }
                require(type != WorkflowType.REF2I || kind == ReferenceMediaKind.IMAGE) {
                    "Node $nodeId.$inputKey: REF2I does not accept reference videos"
                }
                slots.add(ReferenceSlot(kind, index, nodeId, inputKey))
            }
        }
        require(slots.isNotEmpty()) { "Reference workflow needs at least one mapped reference loader" }
        for ((kind, group) in slots.groupBy { it.kind }) {
            val sorted = group.sortedBy { it.index }
            sorted.forEachIndexed { offset, slot ->
                require(slot.index == offset + 1) {
                    "Node ${slot.nodeId}.${slot.inputKey}: ${kind.name.lowercase()} indices must be unique and continuous from 1"
                }
            }
        }
        slots.sortedWith(compareBy<ReferenceSlot> { it.kind.ordinal }.thenBy { it.index })
    }

    /** Loader types, not IMAGE output types, distinguish videos decoded into frame batches. */
    fun findCandidates(graph: WorkflowGraph, type: WorkflowType, registry: NodeTypeRegistry): List<FieldMappingState> {
        if (!isReferenceType(type)) return emptyList()
        val outgoing = graph.edges.groupBy { it.sourceNodeId }
        val nodes = graph.nodes.associateBy { it.id }
        fun reachesConsumer(id: String): Boolean {
            val pending = ArrayDeque<String>()
            val visited = mutableSetOf<String>()
            pending.add(id)
            while (pending.isNotEmpty()) {
                val current = pending.removeFirst()
                if (!visited.add(current)) continue
                for (edge in outgoing[current].orEmpty()) {
                    val target = nodes[edge.targetNodeId] ?: continue
                    if (target.classType == "TextEncodeQwenImage21" || target.classType == "MiniMaxH3ReferenceToVideo" ||
                        edge.targetInputName.contains("ref_", true) ||
                        target.classType.startsWith("Save") || target.classType.contains("Sampler")) return true
                    pending.add(target.id)
                }
            }
            return false
        }
        fun consumerSlot(id: String): Int {
            val pending = ArrayDeque<String>()
            val visited = mutableSetOf<String>()
            pending.add(id)
            var slot = Int.MAX_VALUE
            while (pending.isNotEmpty()) {
                val current = pending.removeFirst()
                if (!visited.add(current)) continue
                for (edge in outgoing[current].orEmpty()) {
                    val name = edge.targetInputName
                    if (name.contains("ref", true) || name.contains("image", true) || name.contains("video", true)) {
                        numericSuffix.findAll(name).lastOrNull()?.value?.toIntOrNull()?.let { slot = minOf(slot, it) }
                    }
                    val target = nodes[edge.targetNodeId] ?: continue
                    if (target.classType != "TextEncodeQwenImage21" && target.classType != "MiniMaxH3ReferenceToVideo") pending.add(target.id)
                }
            }
            return slot
        }
        val candidates = graph.nodes.mapNotNull { node ->
            val (kind, inputKey) = loader(node.classType) ?: return@mapNotNull null
            if (type == WorkflowType.REF2I && kind == ReferenceMediaKind.VIDEO) return@mapNotNull null
            if (!node.inputs.containsKey(inputKey) || !reachesConsumer(node.id)) return@mapNotNull null
            val current = (node.inputs[inputKey] as? InputValue.Literal)?.value
            val existing = (current as? String)?.let { placeholderPattern.matchEntire(it) }
                ?.groupValues?.get(1)?.let(::parseKey)?.takeIf { it.first == kind }?.second
            Triple(kind, FieldCandidate(node.id, node.title, node.classType, inputKey, current), existing)
        }
        return ReferenceMediaKind.entries.flatMap { kind ->
            val sameKind = candidates.filter { it.first == kind }.sortedWith(
                compareBy<Triple<ReferenceMediaKind, FieldCandidate, Int?>> { it.third ?: Int.MAX_VALUE }
                    .thenBy { consumerSlot(it.second.nodeId) }
                    .thenBy { it.second.nodeId.toLongOrNull() ?: Long.MAX_VALUE }
                    .thenBy { it.second.nodeId }
            )
            val choices = sameKind.map { it.second }
            sameKind.mapIndexed { index, _ ->
                val key = mediaKey(kind, index + 1)
                FieldMappingState(RequiredField(key, key, key, false), choices, index)
            }
        }
    }

    private fun isLink(value: Any?): Boolean = value is JSONArray && value.length() == 2 &&
        value.opt(0) is String && value.opt(1) is Number

    /** Remove only reference-derived branches; never cut a required shared model path. */
    private fun removeBranches(nodes: JSONObject, initial: Set<String>, registry: NodeTypeRegistry) {
        val removed = initial.toMutableSet()
        val pending = ArrayDeque<String>()
        pending.addAll(initial)
        while (pending.isNotEmpty()) {
            val source = pending.removeFirst()
            for (targetId in nodes.keys().asSequence().toList()) {
                if (targetId in removed) continue
                val target = nodes.optJSONObject(targetId) ?: continue
                val inputs = target.optJSONObject("inputs") ?: continue
                for (inputKey in inputs.keys().asSequence().toList()) {
                    val link = inputs.opt(inputKey)
                    if (!isLink(link) || (link as JSONArray).optString(0) != source) continue
                    val classType = target.optString("class_type")
                    val definition = registry.getInputDefinition(classType, inputKey)
                    val referenceConsumer = classType == "TextEncodeQwenImage21" || classType == "MiniMaxH3ReferenceToVideo"
                    val optional = definition?.isRequired == false ||
                        (referenceConsumer && (inputKey.startsWith("ref_") || inputKey.startsWith("image")))
                    if (optional) {
                        inputs.remove(inputKey)
                    } else {
                        val requiredOthers = inputs.keys().asSequence().filter { it != inputKey }.any { key ->
                            val value = inputs.opt(key)
                            isLink(value) && (value as JSONArray).optString(0) !in removed &&
                                registry.getInputDefinition(classType, key)?.isRequired != false
                        }
                        require(!requiredOthers && !classType.startsWith("Save") && !classType.contains("Sampler") && !referenceConsumer) {
                            "Node $targetId.$inputKey: removing reference $source would cut a shared required chain"
                        }
                        if (removed.add(targetId)) pending.add(targetId)
                    }
                }
            }
        }
        removed.forEach(nodes::remove)
    }

    fun applyMappings(
        json: JSONObject, type: WorkflowType, mappings: Map<String, Pair<String, String>>, registry: NodeTypeRegistry
    ): Map<String, Pair<String, String>> {
        require(isReferenceType(type))
        val nodes = json.optJSONObject("nodes") ?: json
        require(mappings["width"] != null && mappings["height"] != null) { "Reference generation requires independent output width and height mappings" }
        for (key in listOf("width", "height")) {
            val (id, input) = mappings.getValue(key)
            val node = nodes.optJSONObject(id) ?: error("Node $id.$input: missing output canvas")
            require(loader(node.optString("class_type")) == null && input == key) {
                "Node $id.$input: reference generation requires an independent output canvas, not loader preprocessing dimensions"
            }
        }
        val media = mappings.filterKeys { parseKey(it) != null }
        require(media.isNotEmpty()) { "Map at least one reference loader" }
        require(media.values.toSet().size == media.size) { "A reference filename cannot bind two numbered slots" }
        val selected = media.values.toSet()
        val remove = mutableSetOf<String>()
        for (id in nodes.keys()) {
            val node = nodes.optJSONObject(id) ?: continue
            val mediaLoader = loader(node.optString("class_type")) ?: continue
            if ((id to mediaLoader.second) !in selected) remove.add(id)
        }
        removeBranches(nodes, remove, registry)
        val normalized = mappings.filterKeys { parseKey(it) == null }.toMutableMap()
        for (kind in ReferenceMediaKind.entries) {
            media.entries.filter { parseKey(it.key)?.first == kind }.sortedBy { parseKey(it.key)!!.second }
                .forEachIndexed { offset, entry ->
                    require(type != WorkflowType.REF2I || kind == ReferenceMediaKind.IMAGE) { "REF2I does not accept videos" }
                    val (id, input) = entry.value
                    val node = nodes.optJSONObject(id) ?: error("Node $id.$input: missing reference loader")
                    require(loader(node.optString("class_type")) == (kind to input)) { "Node $id.$input: incompatible reference loader" }
                    val key = mediaKey(kind, offset + 1)
                    node.getJSONObject("inputs").put(input, "{{$key}}")
                    normalized[key] = entry.value
                }
        }
        return normalized
    }

    fun validateIndependentCanvas(json: JSONObject, type: WorkflowType, registry: NodeTypeRegistry) {
        if (type != WorkflowType.REF2I) return
        val nodes = json.optJSONObject("nodes") ?: json
        fun referenceLatent(sourceId: String, output: Int, visited: MutableSet<String>): Boolean {
            if (!visited.add(sourceId)) return false
            val node = nodes.optJSONObject(sourceId) ?: return false
            val classType = node.optString("class_type")
            if (classType == "TextEncodeQwenImage21") return registry.getOutputType(classType, output) == "LATENT" || output == 1
            if (!classType.contains("switch", true)) return false
            val inputs = node.optJSONObject("inputs") ?: return false
            val selector = inputs.optInt("select", inputs.optInt("switch", -1))
            val candidates = inputs.keys().asSequence().filter { key ->
                isLink(inputs.opt(key)) && (selector < 0 || key.endsWith(selector.toString()))
            }
            return candidates.any { key ->
                val link = inputs.getJSONArray(key)
                referenceLatent(link.getString(0), link.getInt(1), visited)
            }
        }
        for (id in nodes.keys()) {
            val node = nodes.optJSONObject(id) ?: continue
            val inputs = node.optJSONObject("inputs") ?: continue
            for (key in listOf("latent_image", "latent")) {
                val link = inputs.opt(key)
                if (isLink(link) && referenceLatent((link as JSONArray).getString(0), link.getInt(1), mutableSetOf())) {
                    error("Node $id.$key: reference generation requires an independent output canvas; replace the Qwen reference latent connection with an independent latent")
                }
            }
        }
    }

    fun prepare(
        template: JSONObject, type: WorkflowType, values: Map<String, Any>, uploads: Map<String, String>, registry: NodeTypeRegistry
    ): Result<JSONObject> = runCatching {
        val json = JSONObject(template.toString())
        val nodes = json.optJSONObject("nodes") ?: json
        val slots = findSlots(json, type).getOrThrow()
        val resolved = values.toMutableMap()
        if (type == WorkflowType.REF2V && values["duration_seconds"] != null) {
            val seconds = values.getValue("duration_seconds").toString().toInt()
            val fps = values.getValue("frame_rate").toString().toInt()
            resolved["length"] = ReferenceVideoDuration.frameCount(seconds, fps)
        }
        require(uploads.isNotEmpty()) { "Select at least one reference" }
        require(uploads.keys.all { key -> slots.any { it.key == key } }) { "Uploaded reference has no mapped slot" }
        for (kind in ReferenceMediaKind.entries) {
            val indices = uploads.keys.mapNotNull(::parseKey).filter { it.first == kind }.map { it.second }.sorted()
            require(indices == (1..indices.size).toList()) { "Uploaded references must be continuous from 1" }
        }
        val remove = slots.filter { it.key !in uploads }.map { it.nodeId }.toSet()
        removeBranches(nodes, remove, registry)
        for (slot in slots.filter { it.key in uploads }) {
            val filename = uploads.getValue(slot.key)
            require(filename.isNotBlank()) { "Node ${slot.nodeId}.${slot.inputKey}: empty uploaded filename" }
            nodes.getJSONObject(slot.nodeId).getJSONObject("inputs").put(slot.inputKey, filename)
            nodes.getJSONObject(slot.nodeId).put("mode", 0)
            val pending = ArrayDeque<String>()
            val visited = mutableSetOf<String>()
            pending.add(slot.nodeId)
            while (pending.isNotEmpty()) {
                val source = pending.removeFirst()
                if (!visited.add(source)) continue
                for (id in nodes.keys()) {
                    val node = nodes.optJSONObject(id) ?: continue
                    if (loader(node.optString("class_type")) != null || node.optString("class_type") !in setOf("GetVideoComponents", "ImageScale", "ImageScaleBy", "ImageResize")) continue
                    val inputs = node.optJSONObject("inputs") ?: continue
                    if (inputs.keys().asSequence().any { key -> val v = inputs.opt(key); isLink(v) && (v as JSONArray).optString(0) == source }) {
                        node.put("mode", 0)
                        pending.add(id)
                    }
                }
            }
        }
        val placeholder = Regex("\\{\\{(\\w+)\\}\\}")
        for (id in nodes.keys()) {
            val inputs = nodes.optJSONObject(id)?.optJSONObject("inputs") ?: continue
            for (key in inputs.keys().asSequence().toList()) {
                val value = inputs.opt(key) as? String ?: continue
                val match = placeholder.matchEntire(value) ?: continue
                val name = match.groupValues[1]
                require(parseKey(name) == null) { "Node $id.$key: unreplaced reference placeholder" }
                val replacement = resolved[name] ?: error("Node $id.$key: missing value $name")
                require(replacement !is String || replacement.isNotEmpty() || name == "negative_prompt") { "Node $id.$key: missing value $name" }
                inputs.put(key, replacement)
            }
        }
        validateIndependentCanvas(json, type, registry)
        validatePrepared(nodes)
        JSONObject().put("nodes", nodes)
    }

    fun validatePrepared(nodes: JSONObject) {
        for (id in nodes.keys()) {
            val node = nodes.optJSONObject(id) ?: continue
            val inputs = node.optJSONObject("inputs") ?: continue
            for (key in inputs.keys()) {
                val value = inputs.opt(key)
                if (isLink(value)) require(nodes.has((value as JSONArray).getString(0))) { "Node $id.$key: dangling link" }
                if (value is String) require(!value.contains("{{reference_")) { "Node $id.$key: unreplaced reference placeholder" }
            }
            loader(node.optString("class_type"))?.let { (_, key) ->
                require(inputs.optString(key).isNotBlank()) { "Node $id.$key: empty reference filename" }
            }
        }
    }
}
