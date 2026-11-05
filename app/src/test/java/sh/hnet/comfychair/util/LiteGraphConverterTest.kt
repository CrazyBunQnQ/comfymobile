package sh.hnet.comfychair.util

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.hnet.comfychair.workflow.InputDefinition
import sh.hnet.comfychair.workflow.NodeTypeDefinition
import sh.hnet.comfychair.workflow.NodeTypeRegistry

class LiteGraphConverterTest {
    private fun definition(vararg inputs: InputDefinition) = NodeTypeDefinition(
        classType = "FixtureNode",
        category = "Test",
        inputs = inputs.toList(),
        outputs = emptyList()
    )

    private fun convert(node: JSONObject, nodeDefinition: NodeTypeDefinition? = definition()): JSONObject {
        val workflow = JSONObject().put("nodes", JSONArray().put(node))
        val result = LiteGraphConverter { type ->
            if (type == "FixtureNode") nodeDefinition else null
        }.convert(workflow)
        return JSONObject(result.jsonContent).getJSONObject("nodes").getJSONObject("1")
    }

    @Test
    fun namedObjectWidgetsKeepDottedValuesAndSkipNestedPayloads() {
        val node = JSONObject()
            .put("id", 1)
            .put("type", "FixtureNode")
            .put("widgets_values", JSONObject()
                .put("sampler_name", "array-value")
                .put("format", "jsonl"))
            .put("widgets_values_named", JSONObject()
                .put("sampler_name", "named-value")
                .put("format", "custom")
                .put("format.images.0", "frame.png")
                .put("format.nested", JSONObject().put("images", JSONArray().put("unused.png")))
                .put("videopreview", "ignored"))

        val inputs = convert(node).getJSONObject("inputs")

        assertEquals("named-value", inputs.getString("sampler_name"))
        assertEquals("custom", inputs.getString("format"))
        assertEquals("frame.png", inputs.getString("format.images.0"))
        assertFalse(inputs.has("format.nested"))
        assertFalse(inputs.has("videopreview"))
    }

    @Test
    fun objectWidgetsWithoutNamedValuesUseActualInputNames() {
        val node = JSONObject()
            .put("id", 1)
            .put("type", "FixtureNode")
            .put("inputs", JSONArray()
                .put(JSONObject().put("name", "model_name").put("link", JSONObject.NULL)))
            .put("widgets_values", JSONObject()
                .put("model_name", "custom-model.safetensors")
                .put("videopreview", "preview"))

        val inputs = convert(node).getJSONObject("inputs")

        assertEquals("custom-model.safetensors", inputs.getString("model_name"))
        assertFalse(inputs.has("videopreview"))
    }

    @Test
    fun declaredWidgetOrderAndSeedControlSkipBeatServerOrder() {
        val node = JSONObject()
            .put("id", 1)
            .put("type", "FixtureNode")
            .put("inputs", JSONArray()
                .put(JSONObject().put("name", "steps").put("widget", JSONObject()))
                .put(JSONObject().put("name", "seed").put("widget", JSONObject()))
                .put(JSONObject().put("name", "control_after_generate").put("widget", JSONObject())))
            .put("widgets_values", JSONArray().put(24).put(987654321L).put("randomize"))
        val nodeDefinition = definition(
            InputDefinition("seed", "INT", isRequired = true),
            InputDefinition("steps", "INT", isRequired = true),
            InputDefinition("control_after_generate", "STRING", isRequired = true)
        )

        val inputs = convert(node, nodeDefinition).getJSONObject("inputs")

        assertEquals(24, inputs.getInt("steps"))
        assertEquals(987654321L, inputs.getLong("seed"))
        assertFalse(inputs.has("control_after_generate"))
    }

    @Test
    fun legacyServerOrderArraysStillSkipSeedControlValue() {
        val node = JSONObject()
            .put("id", 1)
            .put("type", "FixtureNode")
            .put("widgets_values", JSONArray().put(123L).put("decrement").put(32))
        val nodeDefinition = definition(
            InputDefinition("seed", "INT", isRequired = true),
            InputDefinition("steps", "INT", isRequired = true)
        )

        val inputs = convert(node, nodeDefinition).getJSONObject("inputs")

        assertEquals(123L, inputs.getLong("seed"))
        assertEquals(32, inputs.getInt("steps"))
    }

    @Test
    fun subgraphWithoutProxyWidgetsAppliesExposedWidgetOverInternalDefault() {
        val workflow = subgraphFixture(externalConnection = false)
        val result = converter().convert(workflow)
        val node = JSONObject(result.jsonContent).getJSONObject("nodes").getJSONObject("2")

        assertEquals("outer-widget", node.getJSONObject("inputs").getString("prompt"))
    }

    @Test
    fun subgraphExternalConnectionOverridesExposedWidget() {
        val workflow = subgraphFixture(externalConnection = true)
        val result = converter().convert(workflow)
        val node = JSONObject(result.jsonContent).getJSONObject("nodes").getJSONObject("2")
        val connection = node.getJSONObject("inputs").getJSONArray("prompt")

        assertEquals("0", connection.getString(0))
        assertEquals(0, connection.getInt(1))
    }

    @Test
    fun registryParsesAutogrowNamesAndPrefixAndResolvesDottedChildren() {
        val imageTemplate = JSONObject()
            .put("names", JSONArray().put("image_1").put("image_2"))
            .put("input", JSONArray().put("IMAGE"))
        val videoTemplate = JSONObject().put("prefix", "video_").put("min", 1).put("max", 9)
            .put("input", JSONObject().put("type", "VIDEO"))
        val required = JSONObject()
            .put("ref_images", JSONArray().put("COMFY_AUTOGROW_V3").put(JSONObject().put("template", imageTemplate)))
            .put("ref_videos", JSONArray().put("COMFY_AUTOGROW_V3").put(JSONObject().put("template", videoTemplate)))
        val objectInfo = JSONObject().put("FixtureNode", JSONObject()
            .put("input", JSONObject().put("required", required))
            .put("output", JSONArray().put("*")))
        val registry = NodeTypeRegistry()

        registry.parseObjectInfo(objectInfo)

        val images = registry.getInputDefinition("FixtureNode", "ref_images")
        assertEquals(listOf("image_1", "image_2"), images?.autogrowNames)
        assertEquals("IMAGE", registry.getInputType("FixtureNode", "ref_images.image_2"))
        assertEquals("IMAGE", registry.getInputDefinition("FixtureNode", "ref_images.image_2")?.type)
        val videos = registry.getInputDefinition("FixtureNode", "ref_videos")
        assertEquals("video_", videos?.autogrowPrefix)
        assertEquals(1, videos?.autogrowMin)
        assertEquals(9, videos?.autogrowMax)
        assertEquals("VIDEO", registry.getInputType("FixtureNode", "ref_videos.video_4"))
        assertNotNull(registry.getInputDefinition("FixtureNode", "ref_videos.video_4"))
    }

    private fun converter() = LiteGraphConverter { type ->
        if (type == "FixtureNode") definition(InputDefinition("prompt", "STRING", isRequired = true)) else null
    }

    private fun subgraphFixture(externalConnection: Boolean): JSONObject {
        val instanceInput = JSONObject()
            .put("name", "prompt")
            .put("type", "STRING")
            .put("link", if (externalConnection) 5 else -1)
            .put("widget", JSONObject().put("name", "prompt"))
        val internalNode = JSONObject()
            .put("id", 1)
            .put("type", "FixtureNode")
            .put("inputs", JSONArray().put(JSONObject()
                .put("name", "prompt")
                .put("type", "STRING")
                .put("link", 40)
                .put("widget", JSONObject().put("name", "prompt"))))
            .put("widgets_values", JSONArray().put("inner-default"))
            .put("outputs", JSONArray().put(JSONObject().put("name", "output").put("type", "STRING")))
        val subgraph = JSONObject()
            .put("id", "fixture-subgraph")
            .put("name", "Fixture subgraph")
            .put("inputNode", JSONObject().put("id", -10))
            .put("outputNode", JSONObject().put("id", -20))
            .put("inputs", JSONArray().put(JSONObject()
                .put("name", "prompt")
                .put("type", "STRING")
                .put("linkIds", JSONArray().put(40))))
            .put("outputs", JSONArray().put(JSONObject()
                .put("name", "output")
                .put("type", "STRING")
                .put("linkIds", JSONArray().put(41))))
            .put("nodes", JSONArray().put(internalNode))
            .put("links", JSONArray()
                .put(JSONObject().put("id", 40).put("origin_id", -10).put("origin_slot", 0)
                    .put("target_id", 1).put("target_slot", 0).put("type", "STRING"))
                .put(JSONObject().put("id", 41).put("origin_id", 1).put("origin_slot", 0)
                    .put("target_id", -20).put("target_slot", 0).put("type", "STRING")))
        val subgraphInstance = JSONObject()
            .put("id", 1)
            .put("type", "fixture-subgraph")
            .put("inputs", JSONArray().put(instanceInput))
            .put("widgets_values", JSONArray().put("outer-widget"))
        val workflow = JSONObject()
            .put("nodes", JSONArray().put(subgraphInstance))
            .put("links", if (externalConnection) {
                JSONArray().put(JSONArray().put(5).put(0).put(0).put(1).put(0).put("STRING"))
            } else JSONArray())
            .put("definitions", JSONObject().put("subgraphs", JSONArray().put(subgraph)))
        if (externalConnection) {
            workflow.getJSONArray("nodes").put(JSONObject()
                .put("id", 0)
                .put("type", "FixtureNode")
                .put("widgets_values_named", JSONObject().put("prompt", "source")))
        }
        return workflow
    }
}
