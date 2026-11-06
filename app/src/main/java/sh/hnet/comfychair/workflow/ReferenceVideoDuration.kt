package sh.hnet.comfychair.workflow

import kotlin.math.roundToInt

/** UI duration is seconds; ComfyUI video length inputs remain frame counts. */
object ReferenceVideoDuration {
    fun frameCount(seconds: Int, fps: Int): Int {
        require(seconds > 0 && fps > 0) { "Duration and frame rate must be positive" }
        val frames = seconds.toLong() * fps
        require(frames <= Int.MAX_VALUE) { "Video duration is too large" }
        return frames.toInt()
    }

    fun restoredSeconds(seconds: Int?, legacyFrames: Int?, fps: Int): Int =
        seconds ?: legacyFrames?.let {
            require(fps > 0) { "Frame rate must be positive" }
            (it.toDouble() / fps).roundToInt().coerceAtLeast(1)
        } ?: 5
}
