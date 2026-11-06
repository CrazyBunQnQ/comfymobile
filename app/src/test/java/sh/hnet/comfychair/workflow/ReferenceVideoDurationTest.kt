package sh.hnet.comfychair.workflow

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import sh.hnet.comfychair.WorkflowType
import sh.hnet.comfychair.model.WorkflowDefaults
import sh.hnet.comfychair.model.WorkflowValues

class ReferenceVideoDurationTest {
    @Test fun secondsUseCurrentFrameRateAndRejectOverflow() {
        assertEquals(120, ReferenceVideoDuration.frameCount(5, 24))
        assertEquals(240, ReferenceVideoDuration.frameCount(10, 24))
        assertEquals(360, ReferenceVideoDuration.frameCount(15, 24))
        assertEquals(300, ReferenceVideoDuration.frameCount(10, 30))
        assertTrue(runCatching { ReferenceVideoDuration.frameCount(0, 24) }.isFailure)
        assertTrue(runCatching { ReferenceVideoDuration.frameCount(5, 0) }.isFailure)
        assertTrue(runCatching { ReferenceVideoDuration.frameCount(Int.MAX_VALUE, 24) }.isFailure)
    }

    @Test fun oldFramePreferencesMigrateWithoutTreatingFramesAsSeconds() {
        val old = WorkflowValues.fromJson("""{"length":124,"frameRate":24}""")
        assertEquals(5, ReferenceVideoDuration.restoredSeconds(old.durationSeconds, old.length, old.frameRate!!))
        assertEquals(10, ReferenceVideoDuration.restoredSeconds(null, 243, 24))
        assertEquals(15, ReferenceVideoDuration.restoredSeconds(null, 362, 24))
        val seconds = WorkflowValues.fromJson(WorkflowValues.toJson(old.copy(length = null, durationSeconds = 10)))
        assertEquals(10, seconds.durationSeconds)
        assertNull(seconds.length)
        assertEquals(10, ReferenceVideoDuration.restoredSeconds(seconds.durationSeconds, 124, 24))
        val defaults = WorkflowDefaults.fromJson(JSONObject("""{"duration_seconds":5,"frame_rate":24}"""))
        assertEquals(5, WorkflowDefaults.fromJson(WorkflowDefaults.toJson(defaults)).durationSeconds)
    }

    @Test fun videoPreparationConvertsSecondsAndKeepsNumericFrameInput() {
        val template = JSONObject("""{"nodes":{
          "1":{"class_type":"LoadVideo","inputs":{"file":"{{reference_video_1}}"}},
          "2":{"class_type":"MiniMaxH3ReferenceToVideo","inputs":{"length":"{{length}}","fps":"{{frame_rate}}","ref_videos.video_1":["1",0]}}
        }}""")
        val prepared = ReferenceWorkflowSupport.prepare(template, WorkflowType.REF2V,
            mapOf("duration_seconds" to 10, "frame_rate" to 24, "length" to 124),
            mapOf("reference_video_1" to "safe.mp4"), NodeTypeRegistry()).getOrThrow()
        val inputs = prepared.getJSONObject("nodes").getJSONObject("2").getJSONObject("inputs")
        assertEquals(240, inputs.get("length"))
        assertEquals(24, inputs.get("fps"))
        assertEquals("{{length}}", template.getJSONObject("nodes").getJSONObject("2").getJSONObject("inputs").getString("length"))
    }
}
