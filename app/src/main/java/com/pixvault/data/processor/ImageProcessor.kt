package com.pixvault.data.processor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ImageProcessor(private val context: Context) {

    private val imagesDir: File by lazy {
        File(context.filesDir, "images").apply { mkdirs() }
    }

    enum class OutputFormat(
        val mimeType: String,
        val extension: String,
        val compressFormat: Bitmap.CompressFormat
    ) {
        PNG("image/png", ".png", Bitmap.CompressFormat.PNG),
        JPEG("image/jpeg", ".jpg", Bitmap.CompressFormat.JPEG),
        WEBP("image/webp", ".webp", Bitmap.CompressFormat.WEBP)
    }

    data class Stroke(
        val points: List<PointF>,
        val color: Int,
        val width: Float
    )

    data class TextItem(
        val text: String,
        val x: Float,
        val y: Float,
        val color: Int,
        val size: Float,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strikethrough: Boolean = false,
        val fontFamily: String? = null
    )

    data class Request(
        val sourcePath: String,
        val cropRect: RectF? = null,
        val format: OutputFormat? = null,
        val strokes: List<Stroke> = emptyList(),
        val texts: List<TextItem> = emptyList(),
        val outputPath: String? = null
    )

    data class Result(
        val path: String,
        val mimeType: String,
        val width: Int,
        val height: Int,
        val fileSize: Long
    )

    suspend fun process(request: Request, quality: Int): Result = withContext(Dispatchers.IO) {
        // Editing previews are sampled in the UI, but final rendering must use the
        // original pixels. Otherwise overwriting a modern photo silently shrinks it.
        var bitmap = decodeOriginal(request.sourcePath) ?: error("无法解码图片")

        request.cropRect?.let { rect ->
            val left = (rect.left * bitmap.width).toInt().coerceIn(0, bitmap.width)
            val top = (rect.top * bitmap.height).toInt().coerceIn(0, bitmap.height)
            val right = (rect.right * bitmap.width).toInt().coerceIn(left, bitmap.width)
            val bottom = (rect.bottom * bitmap.height).toInt().coerceIn(top, bitmap.height)
            if (right > left && bottom > top) {
                val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                bitmap.recycle()
                bitmap = cropped
            }
        }

        if (request.strokes.isNotEmpty()) {
            val drawn = applyStrokes(bitmap, request.strokes)
            bitmap.recycle()
            bitmap = drawn
        }

        if (request.texts.isNotEmpty()) {
            val drawn = applyTexts(bitmap, request.texts)
            bitmap.recycle()
            bitmap = drawn
        }

        val compressFormat = request.format?.compressFormat ?: detectFormat(request.sourcePath)
        val mimeType = request.format?.mimeType ?: mimeFor(compressFormat)
        val extension = request.format?.extension ?: extensionFor(compressFormat)
        val dest = request.outputPath?.let { File(it) }
            ?: File(imagesDir, "${UUID.randomUUID()}$extension")
        val q = if (compressFormat == Bitmap.CompressFormat.PNG) 100 else quality.coerceIn(0, 100)

        dest.outputStream().use { out ->
            bitmap.compress(compressFormat, q, out)
        }

        val result = Result(
            path = dest.absolutePath,
            mimeType = mimeType,
            width = bitmap.width,
            height = bitmap.height,
            fileSize = dest.length()
        )
        bitmap.recycle()
        result
    }

    suspend fun export(sourcePath: String, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                File(sourcePath).inputStream().use { input -> input.copyTo(out) }
            } != null
        } catch (_: Exception) {
            false
        }
    }

    private fun applyStrokes(src: Bitmap, strokes: List<Stroke>): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (stroke in strokes) {
            if (stroke.points.size < 2) continue
            paint.color = stroke.color
            paint.strokeWidth = (stroke.width * out.width).coerceAtLeast(1f)
            val path = Path()
            val first = stroke.points.first()
            path.moveTo(first.x * out.width, first.y * out.height)
            for (i in 1 until stroke.points.size) {
                val p = stroke.points[i]
                path.lineTo(p.x * out.width, p.y * out.height)
            }
            canvas.drawPath(path, paint)
        }
        return out
    }

    private fun applyTexts(src: Bitmap, texts: List<TextItem>): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        for (t in texts) {
            if (t.text.isEmpty()) continue
            val paint = Paint().apply {
                isAntiAlias = true
                color = t.color
                textSize = t.size * out.width
                typeface = buildTypeface(t)
                var flags = 0
                if (t.underline) flags = flags or Paint.UNDERLINE_TEXT_FLAG
                if (t.strikethrough) flags = flags or Paint.STRIKE_THRU_TEXT_FLAG
                this.flags = flags
            }
            val textWidth = paint.measureText(t.text)
            val cx = t.x * out.width
            val cy = t.y * out.height
            val fm = paint.fontMetrics
            val baseline = cy - (fm.ascent + fm.descent) / 2f
            canvas.drawText(t.text, cx - textWidth / 2f, baseline, paint)
        }
        return out
    }

    private fun buildTypeface(t: TextItem): Typeface {
        val style = when {
            t.bold && t.italic -> Typeface.BOLD_ITALIC
            t.bold -> Typeface.BOLD
            t.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val family = t.fontFamily ?: return Typeface.DEFAULT
        return try {
            Typeface.create(family, style)
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
    }

    private fun decodeOriginal(path: String): Bitmap? = try {
        BitmapFactory.decodeFile(path)
    } catch (_: OutOfMemoryError) {
        throw IllegalStateException("原图分辨率过高，设备内存不足，已取消保存以保护原图")
    }

    private fun detectFormat(path: String): Bitmap.CompressFormat {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".png") -> Bitmap.CompressFormat.PNG
            lower.endsWith(".webp") -> Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
    }

    private fun mimeFor(format: Bitmap.CompressFormat): String = when (format) {
        Bitmap.CompressFormat.PNG -> "image/png"
        Bitmap.CompressFormat.WEBP -> "image/webp"
        else -> "image/jpeg"
    }

    private fun extensionFor(format: Bitmap.CompressFormat): String = when (format) {
        Bitmap.CompressFormat.PNG -> ".png"
        Bitmap.CompressFormat.WEBP -> ".webp"
        else -> ".jpg"
    }
}