package sh.hnet.comfychair.workflow

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Pure resolution math shared by reference-mode automatic output sizing and its tests. */
object ReferenceResolution {
    data class Dimensions(val width: Int, val height: Int)

    /**
     * Scale the source aspect ratio to the requested pixel area, then choose the closest pair
     * accepted by each workflow's min/max/step constraints. Returns null for unusable inputs or
     * constraints rather than inventing a source aspect ratio.
     */
    fun calculateCanvas(
        sourceWidth: Int,
        sourceHeight: Int,
        megapixels: Float,
        widthConstraints: WorkflowConstraintsProvider.NumericConstraints,
        heightConstraints: WorkflowConstraintsProvider.NumericConstraints
    ): Dimensions? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || !megapixels.isFinite() || megapixels <= 0f) return null
        val targetPixels = megapixels.toDouble() * 1_000_000.0
        val sourcePixels = sourceWidth.toDouble() * sourceHeight.toDouble()
        val scale = sqrt(targetPixels / sourcePixels)
        val targetWidth = sourceWidth * scale
        val targetHeight = sourceHeight * scale
        if (!targetWidth.isFinite() || !targetHeight.isFinite()) return null

        val widths = candidates(targetWidth, widthConstraints)
        val heights = candidates(targetHeight, heightConstraints)
        if (widths.isEmpty() || heights.isEmpty()) return null

        val sourceAspect = sourceWidth.toDouble() / sourceHeight
        var best: Dimensions? = null
        var bestScore = Double.POSITIVE_INFINITY
        for (width in widths) for (height in heights) {
            val aspectError = abs(ln((width.toDouble() / height) / sourceAspect))
            val areaError = abs(ln((width.toDouble() * height) / targetPixels))
            val score = aspectError * 2.0 + areaError
            if (score < bestScore) {
                bestScore = score
                best = Dimensions(width, height)
            }
        }
        return best
    }

    fun complementaryDimension(
        editedDimension: Int,
        megapixels: Float,
        constraints: WorkflowConstraintsProvider.NumericConstraints
    ): Int? {
        if (editedDimension <= 0 || !megapixels.isFinite() || megapixels <= 0f) return null
        val target = megapixels.toDouble() * 1_000_000.0 / editedDimension
        return candidates(target, constraints).minByOrNull { abs(it - target) }
    }

    /** Apply a clockwise media rotation; only quarter turns change display dimensions. */
    fun orientedDimensions(width: Int, height: Int, rotationDegrees: Int): Dimensions? {
        if (width <= 0 || height <= 0) return null
        val normalized = ((rotationDegrees % 360) + 360) % 360
        return if (normalized == 90 || normalized == 270) Dimensions(height, width) else Dimensions(width, height)
    }

    private fun candidates(
        target: Double,
        constraints: WorkflowConstraintsProvider.NumericConstraints
    ): List<Int> {
        if (!constraints.min.isFinite() || !constraints.max.isFinite() || !constraints.step.isFinite() || constraints.step <= 0f) {
            return emptyList()
        }
        val min = ceil(constraints.min.toDouble()).toInt()
        val max = floor(constraints.max.toDouble()).toInt()
        if (min > max) return emptyList()

        val valid: (Int) -> Boolean = { value ->
            val stepIndex = (value - constraints.min.toDouble()) / constraints.step.toDouble()
            value >= constraints.min && value <= constraints.max &&
                abs(stepIndex - kotlin.math.round(stepIndex)) < 0.0001
        }
        if (constraints.step <= 1f) {
            return listOf(target.roundToInt(), floor(target).toInt(), ceil(target).toInt(), min, max)
                .filter { it in min..max && valid(it) }.distinct()
        }

        val step = constraints.step.toDouble()
        val maxIndex = floor((max - constraints.min) / step).toInt().coerceAtLeast(0)
        val nearIndex = ((target - constraints.min) / step).roundToInt().coerceIn(0, maxIndex)
        return buildList {
            for (offset in -8..8) {
                val index = nearIndex + offset
                if (index in 0..maxIndex) {
                    val value = (constraints.min + index * constraints.step).roundToInt()
                    if (value in min..max && valid(value)) add(value)
                }
            }
            if (valid(min)) add(min)
            if (valid(max)) add(max)
        }.distinct()
    }
}
