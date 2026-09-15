package com.pixvault.data.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object VectorUtils {

    fun toBytes(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (v in vector) buffer.putFloat(v)
        return buffer.array()
    }

    fun toFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val result = FloatArray(bytes.size / Float.SIZE_BYTES)
        for (i in result.indices) result[i] = buffer.float
        return result
    }

    fun dot(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }

    fun normalize(vector: FloatArray): FloatArray {
        var sum = 0.0
        for (x in vector) sum += x.toDouble() * x.toDouble()
        val norm = sqrt(sum)
        return if (norm > 0.0) FloatArray(vector.size) { (vector[it] / norm).toFloat() } else vector
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        val dot = dot(a, b)
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0f) dot / denominator else 0f
    }

    fun mean(vectors: List<FloatArray>): FloatArray {
        require(vectors.isNotEmpty()) { "cannot average empty vector list" }
        val dim = vectors[0].size
        val result = FloatArray(dim)
        for (v in vectors) {
            for (i in 0 until dim) result[i] += v[i]
        }
        for (i in 0 until dim) result[i] /= vectors.size
        return normalize(result)
    }
}