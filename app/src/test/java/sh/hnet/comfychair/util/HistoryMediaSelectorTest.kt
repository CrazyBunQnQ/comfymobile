package sh.hnet.comfychair.util

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryMediaSelectorTest {

    private fun outputs(vararg nodes: Pair<String, JSONObject>): JSONObject =
        JSONObject().apply { nodes.forEach { (id, node) -> put(id, node) } }

    private fun media(filename: String, type: String) =
        JSONObject().put("filename", filename).put("subfolder", "").put("type", type)

    @Test fun producedFileWinsOverEchoedInputRegardlessOfNodeOrder() {
        val history = outputs(
            "2" to JSONObject().put("class_type", "LoadVideo")
                .put("images", JSONArray().put(media("reference_video_1.mp4", "input"))),
            "29" to JSONObject().put("class_type", "SaveVideo")
                .put("images", JSONArray().put(media("generated_00001_.mp4", "output")))
        )
        val selected = HistoryMediaSelector.select(history, preferVideo = true)
        assertEquals("generated_00001_.mp4", selected!!.filename)
        assertEquals("output", selected.type)
    }

    @Test fun imageSelectionSkipsInputFiles() {
        val history = outputs(
            "1" to JSONObject().put("images", JSONArray().put(media("source.png", "input"))),
            "25" to JSONObject().put("images", JSONArray().put(media("result.png", "output")))
        )
        assertEquals("result.png", HistoryMediaSelector.select(history, preferVideo = false)!!.filename)
    }

    @Test fun legacyHistoryWithoutTypeStillResolves() {
        val history = outputs(
            "25" to JSONObject().put("images", JSONArray().put(JSONObject().put("filename", "legacy.png")))
        )
        assertEquals("legacy.png", HistoryMediaSelector.select(history, preferVideo = false)!!.filename)
    }

    @Test fun historyWithoutMediaResolvesToNull() {
        assertNull(HistoryMediaSelector.select(outputs("1" to JSONObject()), preferVideo = false))
        assertNull(HistoryMediaSelector.select(null, preferVideo = true))
    }

    @Test fun videoSelectionPrefersVideoFileOverStillImage() {
        val history = outputs(
            "20" to JSONObject().put("images", JSONArray().put(media("preview.png", "output"))),
            "29" to JSONObject().put("videos", JSONArray().put(media("clip.mp4", "output")))
        )
        assertEquals("clip.mp4", HistoryMediaSelector.select(history, preferVideo = true)!!.filename)
    }
}
