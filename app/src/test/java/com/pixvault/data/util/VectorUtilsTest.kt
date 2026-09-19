package com.pixvault.data.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VectorUtilsTest {
    @Test
    fun bytesRoundTripPreservesVector() {
        val vector = floatArrayOf(-1.5f, 0f, 2.25f)
        assertArrayEquals(vector, VectorUtils.toFloatArray(VectorUtils.toBytes(vector)), 0f)
    }

    @Test
    fun cosineHandlesOrthogonalAndEqualVectors() {
        assertEquals(1f, VectorUtils.cosine(floatArrayOf(1f, 2f), floatArrayOf(1f, 2f)), 0.0001f)
        assertEquals(0f, VectorUtils.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 0.0001f)
    }

    @Test
    fun mismatchedDimensionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            VectorUtils.cosine(floatArrayOf(1f), floatArrayOf(1f, 2f))
        }
    }

    @Test
    fun meanReturnsNormalizedPrototype() {
        val mean = VectorUtils.mean(listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)))
        assertEquals(1f, VectorUtils.cosine(mean, floatArrayOf(1f, 1f)), 0.0001f)
    }
}
