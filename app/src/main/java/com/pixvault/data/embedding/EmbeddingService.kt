package com.pixvault.data.embedding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pixvault.ml.ClipImageEncoder
import com.pixvault.ml.ClipTextEncoder
import com.pixvault.ml.ClipTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EmbeddingService(private val context: Context) {

    @Volatile
    private var encoder: ClipImageEncoder? = null

    @Volatile
    private var textEncoder: ClipTextEncoder? = null

    @Volatile
    private var tokenizer: ClipTokenizer? = null

    private val textLoadMutex = Mutex()

    fun isLoaded(): Boolean = encoder != null

    fun isTextLoaded(): Boolean = textEncoder != null

    suspend fun ensureLoaded(onProgress: (String) -> Unit) {
        if (encoder != null) return
        withContext(Dispatchers.IO) {
            encoder = ClipImageEncoder.fromAssets(context, "image-encoder.onnx", onProgress)
        }
    }

    suspend fun ensureTextLoaded(onProgress: (String) -> Unit = {}) {
        if (textEncoder != null) return
        textLoadMutex.withLock {
            if (textEncoder != null) return
            withContext(Dispatchers.IO) {
                onProgress("加载词表...")
                val tk = tokenizer ?: ClipTokenizer.fromAssets(context).also { tokenizer = it }
                textEncoder = ClipTextEncoder.fromAssets(context, "text-encoder.onnx", tk, onProgress)
            }
        }
    }

    suspend fun embedText(text: String): FloatArray = withContext(Dispatchers.IO) {
        val encoder = textEncoder ?: error("文本编码器尚未加载")
        encoder.encodeNormalized(text)
    }

    suspend fun embed(path: String): FloatArray = withContext(Dispatchers.IO) {
        val encoder = encoder ?: error("编码器尚未加载")
        val bitmap = decodeSampled(path)
        try {
            encoder.encodeNormalized(bitmap)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun decodeSampled(path: String): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxDim / sample > 1024) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, opts) ?: error("无法解码图片: $path")
    }
}