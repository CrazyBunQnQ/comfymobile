package sh.hnet.comfychair.util

import org.json.JSONObject

/**
 * Selects the produced media from a ComfyUI history `outputs` object.
 *
 * Recent ComfyUI versions echo input files back into `outputs` (with type "input"),
 * so the generated file has to be identified by its "output" type instead of by
 * node iteration order.
 */
object HistoryMediaSelector {

    private val VIDEO_EXTENSIONS = listOf(".mp4", ".webm", ".mov", ".mkv", ".avi", ".gif", ".m4v")

    data class MediaFile(
        val filename: String,
        val subfolder: String,
        val type: String,
        val isVideoFile: Boolean
    )

    /**
     * @param outputs the `outputs` object of a history entry
     * @param preferVideo true to prefer video files, false to prefer still images
     * @return the produced file, or null when the history carries no media
     */
    fun select(outputs: JSONObject?, preferVideo: Boolean): MediaFile? {
        if (outputs == null) return null
        val candidates = mutableListOf<MediaFile>()
        for (nodeId in outputs.keys()) {
            val nodeOutput = outputs.optJSONObject(nodeId) ?: continue
            for (arrayName in listOf("videos", "gifs", "images")) {
                val array = nodeOutput.optJSONArray(arrayName) ?: continue
                for (index in 0 until array.length()) {
                    val info = array.optJSONObject(index) ?: continue
                    val filename = info.optString("filename", "")
                    if (filename.isEmpty()) continue
                    candidates += MediaFile(
                        filename = filename,
                        subfolder = info.optString("subfolder", ""),
                        type = info.optString("type", "output"),
                        isVideoFile = VIDEO_EXTENSIONS.any { filename.lowercase().endsWith(it) }
                    )
                }
            }
        }
        if (candidates.isEmpty()) return null
        val produced = candidates.filter { !it.type.equals("input", ignoreCase = true) }
        val pool = produced.ifEmpty { candidates }
        return if (preferVideo) {
            pool.firstOrNull { it.isVideoFile } ?: pool.first()
        } else {
            pool.firstOrNull { !it.isVideoFile } ?: pool.first()
        }
    }
}
