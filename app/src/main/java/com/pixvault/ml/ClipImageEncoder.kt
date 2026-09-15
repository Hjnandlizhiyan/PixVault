package com.pixvault.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer

class ClipImageEncoder private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    val inputName: String,
    val inputHeight: Int,
    val inputWidth: Int,
    val outputName: String
) : AutoCloseable {

    fun encode(bitmap: Bitmap): FloatArray {
        val inputTensor = OnnxTensor.createTensor(
            env,
            preprocess(bitmap),
            longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
        )
        val output = session.run(mapOf(inputName to inputTensor)).use { result ->
            val tensor = result.get(outputName).get() as OnnxTensor
            val buffer = tensor.floatBuffer
            val arr = FloatArray(buffer.remaining())
            buffer.get(arr)
            arr
        }
        inputTensor.close()
        return output
    }

    fun encodeNormalized(bitmap: Bitmap): FloatArray {
        val v = encode(bitmap)
        var sum = 0.0
        for (x in v) sum += x.toDouble() * x.toDouble()
        val norm = kotlin.math.sqrt(sum)
        if (norm > 0.0) {
            for (i in v.indices) v[i] = (v[i] / norm).toFloat()
        }
        return v
    }

    private fun preprocess(bitmap: Bitmap): FloatBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val pixelCount = inputWidth * inputHeight
        val data = FloatArray(3 * pixelCount)
        val pixels = IntArray(pixelCount)
        resized.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        for (i in 0 until pixelCount) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            data[i] = (r - MEAN[0]) / STD[0]
            data[pixelCount + i] = (g - MEAN[1]) / STD[1]
            data[2 * pixelCount + i] = (b - MEAN[2]) / STD[2]
        }
        return FloatBuffer.wrap(data)
    }

    override fun close() {
        session.close()
    }

    companion object {
        private val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        private val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)

        fun fromAssets(
            context: Context,
            assetName: String,
            onProgress: ((String) -> Unit)? = null
        ): ClipImageEncoder {
            onProgress?.invoke("复制模型文件...")
            val env = OrtEnvironment.getEnvironment()
            val modelFile = copyAssetToCache(context, assetName)
            onProgress?.invoke("模型大小 ${modelFile.length() / 1024 / 1024} MB")
            onProgress?.invoke("创建推理会话...")
            val session = env.createSession(modelFile.absolutePath)
            onProgress?.invoke("读取模型配置...")
            val inputName = session.inputNames.first()
            val info = session.inputInfo[inputName]?.info as? TensorInfo
            val shape = info?.shape
            val size = shape?.size ?: 0
            val h = shape?.getOrNull(size - 2)?.takeIf { it > 0 }?.toInt() ?: 224
            val w = shape?.getOrNull(size - 1)?.takeIf { it > 0 }?.toInt() ?: 224
            val outNames = session.outputNames
            val outputName = outNames.firstOrNull {
                it.contains("pooler", ignoreCase = true) || it.contains("embed", ignoreCase = true)
            } ?: outNames.last()
            onProgress?.invoke("构造完成")
            return ClipImageEncoder(env, session, inputName, h, w, outputName)
        }

        private fun copyAssetToCache(context: Context, assetName: String): File {
            val target = File(context.filesDir, "$assetName.cache")
            if (target.exists()) target.delete()
            context.assets.open(assetName).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            return target
        }
    }
}
