package sh.hnet.comfychair.workflow

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import sh.hnet.comfychair.WorkflowType
import sh.hnet.comfychair.util.WorkflowJsonAnalyzer

class ReferenceWorkflowSupportTest {
    private fun node(type: String, inputs: JSONObject) = JSONObject().put("class_type", type).put("inputs", inputs)
    private fun link(id: String, output: Int = 0) = JSONArray().put(id).put(output)
    private fun fixture(images: Int = 4, videos: Int = 0): JSONObject {
        val nodes = JSONObject()
        val refs = JSONObject().put("prompt", "{{positive_prompt}}")
            .put("width", "{{width}}").put("height", "{{height}}")
        repeat(images) { i ->
            val id = (i + 1).toString()
            nodes.put(id, node("LoadImage", JSONObject().put("image", "{{reference_image_${i + 1}}}")))
            refs.put("ref_images.image_${i + 1}", link(id))
        }
        repeat(videos) { i ->
            val id = (100 + i).toString()
            nodes.put(id, node("VHS_LoadVideo", JSONObject().put("video", "{{reference_video_${i + 1}}}")))
            refs.put("ref_videos.video_${i + 1}", link(id))
        }
        nodes.put("200", node(if (videos > 0) "MiniMaxH3ReferenceToVideo" else "TextEncodeQwenImage21", refs))
        nodes.put("201", node(if (videos > 0) "SaveVideo" else "SaveImage", JSONObject().put("images", link("200"))))
        return JSONObject().put("nodes", nodes)
    }
    private val values = mapOf<String, Any>("positive_prompt" to "quoted \"prompt\"\n第二行", "width" to 1536, "height" to 1024)
    @Test fun videoConsumerPromptIsAvailableForPositiveMapping() {
        val json = fixture(1, 1)
        val inputs = json.getJSONObject("nodes").getJSONObject("200").getJSONObject("inputs")
        inputs.remove("prompt")
        inputs.put("positive_prompt", "Describe the generated video")
        val graph = WorkflowParser().parse(json.toString())
        val mappings = sh.hnet.comfychair.util.FieldMappingAnalyzer.createPromptFieldMappings(graph, NodeTypeRegistry())
        assertTrue(mappings.getValue("positive_text").any { it.nodeId == "200" && it.inputKey == "positive_prompt" })
        assertFalse(mappings.getValue("negative_text").any { it.nodeId == "200" })
    }
    @Test fun referenceVideoPromptTracesToBasicGuiderWithoutEncoderClassName() {
        val json = fixture(1, 1)
        json.getJSONObject("nodes").put("202", node("BasicGuider", JSONObject().put("conditioning", link("200"))))
        val mappings = sh.hnet.comfychair.util.FieldMappingAnalyzer.createPromptFieldMappings(
            WorkflowParser().parse(json.toString()), NodeTypeRegistry()
        )
        assertTrue(mappings.getValue("positive_text").any { it.nodeId == "200" && it.inputKey == "prompt" })
        assertFalse(mappings.getValue("negative_text").any { it.nodeId == "200" })
    }
    @Test fun noiseSeedCandidatePreparesIntegerInsteadOfEmptyInput() {
        val json = fixture(1, 1)
        json.getJSONObject("nodes").put("202", node("RandomNoise", JSONObject().put("noise_seed", "{{seed}}")))
        val candidates = sh.hnet.comfychair.util.FieldMappingAnalyzer.findCandidatesForField(
            "seed", WorkflowParser().parse(json.toString()), emptyMap(), NodeTypeRegistry()
        )
        assertEquals("noise_seed", candidates.single().inputKey)
        val prepared = ReferenceWorkflowSupport.prepare(json, WorkflowType.REF2V,
            values + ("seed" to 123456L), mapOf("reference_image_1" to "one.png"), NodeTypeRegistry()).getOrThrow()
        assertEquals(123456L, prepared.getJSONObject("nodes").getJSONObject("202").getJSONObject("inputs").getLong("noise_seed"))
    }




    @Test fun numberedSlotsSortNumericallyAndPreserveMediaKinds() {
        val slots = ReferenceWorkflowSupport.findSlots(fixture(16, 3), WorkflowType.REF2V).getOrThrow()
        assertEquals((1..16).toList(), slots.filter { it.kind == ReferenceMediaKind.IMAGE }.map { it.index })
        assertEquals(listOf(1, 2, 3), slots.filter { it.kind == ReferenceMediaKind.VIDEO }.map { it.index })
        assertEquals("10", slots[9].nodeId)
    }

    @Test fun duplicateAndGappedSlotsReportConflictingNodeField() {
        val json = fixture(2)
        json.getJSONObject("nodes").getJSONObject("2").getJSONObject("inputs").put("image", "{{reference_image_1}}")
        val duplicate = ReferenceWorkflowSupport.findSlots(json, WorkflowType.REF2I).exceptionOrNull()
        assertNotNull(duplicate)
        assertTrue(duplicate!!.message!!.contains("2.image"))
        json.getJSONObject("nodes").getJSONObject("2").getJSONObject("inputs").put("image", "{{reference_image_3}}")
        assertTrue(ReferenceWorkflowSupport.findSlots(json, WorkflowType.REF2I).isFailure)
    }

    @Test fun ref2iRejectsVideoAndVideoModeAllowsVideoOnly() {
        assertTrue(ReferenceWorkflowSupport.findSlots(fixture(1, 1), WorkflowType.REF2I).isFailure)
        val json = fixture(0, 2)
        val prepared = ReferenceWorkflowSupport.prepare(json, WorkflowType.REF2V, values,
            mapOf("reference_video_1" to "clips/a.mp4", "reference_video_2" to "clips/b.webm"), NodeTypeRegistry()).getOrThrow()
        assertEquals("clips/b.webm", prepared.getJSONObject("nodes").getJSONObject("101").getJSONObject("inputs").getString("video"))
    }

    @Test fun preparationRemovesUnselectedLoaderAndOptionalConsumerEdgeWithoutMutatingTemplate() {
        val template = fixture()
        val original = template.toString()
        val first = ReferenceWorkflowSupport.prepare(template, WorkflowType.REF2I, values,
            mapOf("reference_image_1" to "refs/one.png", "reference_image_2" to "refs/two.jpg", "reference_image_3" to "refs/three.webp"), NodeTypeRegistry()).getOrThrow()
        val nodes = first.getJSONObject("nodes")
        assertFalse(nodes.has("4"))
        assertFalse(nodes.getJSONObject("200").getJSONObject("inputs").has("ref_images.image_4"))
        assertEquals(1536, nodes.getJSONObject("200").getJSONObject("inputs").get("width"))
        assertEquals(values["positive_prompt"], nodes.getJSONObject("200").getJSONObject("inputs").get("prompt"))
        assertEquals(original, template.toString())
        val second = ReferenceWorkflowSupport.prepare(template, WorkflowType.REF2I, values,
            (1..4).associate { "reference_image_$it" to "run2/$it.png" }, NodeTypeRegistry()).getOrThrow()
        assertEquals("run2/4.png", second.getJSONObject("nodes").getJSONObject("4").getJSONObject("inputs").getString("image"))
        assertEquals(original, template.toString())
    }

    @Test fun emptyUploadsAndUnknownOrGappedUploadedKeysCannotPrepare() {
        val template = fixture()
        for (uploads in listOf(emptyMap(), mapOf("reference_image_2" to "two.png"), mapOf("reference_image_5" to "five.png"))) {
            assertTrue(ReferenceWorkflowSupport.prepare(template, WorkflowType.REF2I, values, uploads, NodeTypeRegistry()).isFailure)
        }
    }

    @Test fun videoFrameOutputsAreNotCountedAsImages() {
        val graph = WorkflowParser().parse(fixture(2, 2).toString())
        val mappings = ReferenceWorkflowSupport.findCandidates(graph, WorkflowType.REF2V, NodeTypeRegistry())
        assertEquals(listOf("reference_image_1", "reference_image_2", "reference_video_1", "reference_video_2"), mappings.map { it.field.fieldKey })
        assertTrue(mappings.filter { it.field.fieldKey.startsWith("reference_video") }.all { state -> state.candidates.all { it.classType == "VHS_LoadVideo" } })
    }

    @Test fun nativeVideoDecoderIsRemovedWithUnusedReference() {
        val json = fixture(1, 1)
        val nodes = json.getJSONObject("nodes")
        nodes.put("150", node("GetVideoComponents", JSONObject().put("video", link("100"))))
        nodes.getJSONObject("200").getJSONObject("inputs").put("ref_videos.video_1", link("150"))
        val prepared = ReferenceWorkflowSupport.prepare(json, WorkflowType.REF2V, values, mapOf("reference_image_1" to "one.png"), NodeTypeRegistry()).getOrThrow()
        assertFalse(prepared.getJSONObject("nodes").has("100"))
        assertFalse(prepared.getJSONObject("nodes").has("150"))
        assertFalse(prepared.getJSONObject("nodes").getJSONObject("200").getJSONObject("inputs").has("ref_videos.video_1"))
    }

    @Test fun knownReferenceLatentIsBlockedButIndependentLatentAllowed() {
        val json = fixture(1)
        val nodes = json.getJSONObject("nodes")
        nodes.put("300", node("KSampler", JSONObject().put("latent_image", link("200", 1))))
        assertTrue(runCatching { ReferenceWorkflowSupport.validateIndependentCanvas(json, WorkflowType.REF2I, NodeTypeRegistry()) }.isFailure)
        nodes.put("301", node("EmptyLatentImage", JSONObject().put("width", "{{width}}").put("height", "{{height}}")))
        nodes.getJSONObject("300").getJSONObject("inputs").put("latent_image", link("301"))
        ReferenceWorkflowSupport.validateIndependentCanvas(json, WorkflowType.REF2I, NodeTypeRegistry())
    }

    @Test fun oldQwenEditingGraphsRetainOriginalCategory() {
        val nodes = JSONObject().put("1", node("LoadImage", JSONObject().put("image", "uploaded_image.png [input]")))
            .put("2", node("TextEncodeQwenImageEditPlus", JSONObject().put("image", link("1"))))
        assertEquals(WorkflowType.ITI_EDITING, WorkflowJsonAnalyzer.detectWorkflowType(nodes.toString()))
        assertEquals(WorkflowType.REF2V, WorkflowJsonAnalyzer.detectWorkflowType(fixture(0, 1).toString()))
    }
}
