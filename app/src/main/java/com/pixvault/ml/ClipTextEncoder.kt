package com.pixvault.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.LongBuffer

class ClipTextEncoder private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    private val tokenizer: ClipTokenizer,
    val inputName: String,
    val outputName: String
) : AutoCloseable {

    fun encode(text: String): FloatArray {
        val ids = tokenizer.encode(text)
        val inputTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(ids),
            longArrayOf(1, ids.size.toLong())
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

    fun encodeNormalized(text: String): FloatArray {
        val v = encode(text)
        var sum = 0.0
        for (x in v) sum += x.toDouble() * x.toDouble()
        val norm = kotlin.math.sqrt(sum)
        if (norm > 0.0) {
            for (i in v.indices) v[i] = (v[i] / norm).toFloat()
        }
        return v
    }

    override fun close() {
        session.close()
    }

    companion object {
        fun fromAssets(
            context: Context,
            assetName: String,
            tokenizer: ClipTokenizer,
            onProgress: ((String) -> Unit)? = null
        ): ClipTextEncoder {
            onProgress?.invoke("复制文本模型...")
            val env = OrtEnvironment.getEnvironment()
            val modelFile = copyAssetToCache(context, assetName)
            onProgress?.invoke("文本模型大小 ${modelFile.length() / 1024 / 1024} MB")
            onProgress?.invoke("创建文本推理会话...")
            val session = env.createSession(modelFile.absolutePath)
            val inputName = session.inputNames.first()
            val outNames = session.outputNames
            val outputName = outNames.firstOrNull {
                it.contains("text_embed", ignoreCase = true) || it.contains("embed", ignoreCase = true)
            } ?: outNames.last()
            onProgress?.invoke("文本编码器就绪")
            return ClipTextEncoder(env, session, tokenizer, inputName, outputName)
        }

        private fun copyAssetToCache(context: Context, assetName: String): File {
            val target = File(context.filesDir, "$assetName.cache")
            if (target.exists() && target.length() > 0) return target
            context.assets.open(assetName).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            return target
        }
    }
}