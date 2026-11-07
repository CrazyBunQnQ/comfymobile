package sh.hnet.comfychair.model

import android.net.Uri

/** A selected reference resource, stored as a URI rather than decoded media data. */
data class ReferenceMediaSelection(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0
)
