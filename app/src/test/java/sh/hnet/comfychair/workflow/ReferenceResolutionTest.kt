package sh.hnet.comfychair.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReferenceResolutionTest {
    private val dimensions = WorkflowConstraintsProvider.NumericConstraints(16f, 4096f, 8f)

    @Test fun preservesAspectAndApproximateMegapixelAreaOnGrid() {
        val result = ReferenceResolution.calculateCanvas(1080, 1785, 1f, dimensions, dimensions)!!
        assertEquals(0, result.width % 8)
        assertEquals(0, result.height % 8)
        assertEquals(1080.0 / 1785.0, result.width.toDouble() / result.height, 0.02)
        assertEquals(1.0, result.width.toDouble() * result.height / 1_000_000.0, 0.02)
    }

    @Test fun clampsToWorkflowBoundsWhileKeepingValidStep() {
        val bounded = WorkflowConstraintsProvider.NumericConstraints(256f, 1024f, 32f)
        val result = ReferenceResolution.calculateCanvas(3000, 1000, 1f, bounded, bounded)!!
        assertEquals(1024, result.width)
        assertEquals(352, result.height)
    }

    @Test fun appliesQuarterTurnOrientationAndRejectsMissingMetadata() {
        assertEquals(ReferenceResolution.Dimensions(320, 512), ReferenceResolution.orientedDimensions(512, 320, 90))
        assertNull(ReferenceResolution.orientedDimensions(0, 320, 0))
        assertNull(ReferenceResolution.calculateCanvas(0, 320, 1f, dimensions, dimensions))
    }

    @Test fun editedDimensionKeepsPixelBudgetAndNodeGrid() {
        assertEquals(976, ReferenceResolution.complementaryDimension(1024, 1f, dimensions))
        val videoGrid = WorkflowConstraintsProvider.NumericConstraints(32f, 16384f, 32f)
        assertEquals(640, ReferenceResolution.complementaryDimension(640, 0.4f, videoGrid))
        assertNull(ReferenceResolution.complementaryDimension(0, 1f, dimensions))
        assertNull(ReferenceResolution.complementaryDimension(1024, Float.NaN, dimensions))
    }

    @Test fun allPresetRatiosScaleWithMegapixels() {
        for ((width, height) in listOf(2 to 3, 1 to 1, 3 to 2)) {
            val small = ReferenceResolution.calculateCanvas(width, height, 0.4f, dimensions, dimensions)!!
            val large = ReferenceResolution.calculateCanvas(width, height, 1f, dimensions, dimensions)!!
            assertEquals(width.toDouble() / height, small.width.toDouble() / small.height, 0.02)
            assertEquals(0.4, small.width.toDouble() * small.height / 1_000_000, 0.02)
            assertEquals(1.0, large.width.toDouble() * large.height / 1_000_000, 0.02)
        }
    }
}
