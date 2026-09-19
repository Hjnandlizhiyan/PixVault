package com.pixvault.ui.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pixvault.data.db.entity.ImageEntity
import com.pixvault.data.processor.ImageProcessor
import com.pixvault.data.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

private enum class EditMode { VIEW, CROP, DRAW, TEXT }

private enum class CropHandle {
    NONE, MOVE, LEFT, TOP, RIGHT, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

private data class AspectChoice(val label: String, val ratio: Float?)

private data class EditSnapshot(
    val strokes: List<ImageProcessor.Stroke>,
    val texts: List<ImageProcessor.TextItem>,
    val cropRect: RectF,
    val aspectChoice: AspectChoice
)

private data class FontChoice(val label: String, val family: FontFamily, val typefaceName: String?)

@Composable
fun ImageEditorScreen(
    image: ImageEntity,
    imageProcessor: ImageProcessor,
    repository: ImageRepository,
    onCancel: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sourcePath = image.path ?: image.uri

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    var mode by remember { mutableStateOf(EditMode.VIEW) }
    var cropRect by remember { mutableStateOf(RectF(0f, 0f, 1f, 1f)) }
    var cropHandle by remember { mutableStateOf(CropHandle.NONE) }
    var aspectChoice by remember { mutableStateOf(AspectChoice("自由", null)) }

    var strokes by remember { mutableStateOf<List<ImageProcessor.Stroke>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<List<PointF>?>(null) }
    var brushColor by remember { mutableStateOf(Color.Red) }
    var brushWidthRatio by remember { mutableStateOf(0.01f) }

    var selectedFormat by remember { mutableStateOf<ImageProcessor.OutputFormat?>(null) }
    var quality by remember { mutableStateOf(90f) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    var undoStack by remember { mutableStateOf<List<EditSnapshot>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<EditSnapshot>>(emptyList()) }

    var texts by remember { mutableStateOf<List<ImageProcessor.TextItem>>(emptyList()) }
    var selectedTextIndex by remember { mutableStateOf<Int?>(null) }
    var showTextDialog by remember { mutableStateOf(false) }
    var textDraftPos by remember { mutableStateOf(PointF(0.5f, 0.5f)) }
    var textDragTarget by remember { mutableStateOf<Int?>(null) }

    val brushColors = listOf(Color.White, Color.Red, Color(0xFF4D6BFE), Color.Yellow, Color.Black)
    val brushWidthOptions = listOf(0.004f to 4.dp, 0.01f to 9.dp, 0.02f to 15.dp)
    val aspects = listOf(
        AspectChoice("自由", null),
        AspectChoice("1:1", 1f),
        AspectChoice("4:3", 4f / 3f),
        AspectChoice("16:9", 16f / 9f),
        AspectChoice("3:4", 3f / 4f),
        AspectChoice("9:16", 9f / 16f)
    )
    val fontChoices = listOf(
        FontChoice("默认", FontFamily.SansSerif, "sans-serif"),
        FontChoice("衬线", FontFamily.Serif, "serif"),
        FontChoice("等宽", FontFamily.Monospace, "monospace"),
        FontChoice("手写", FontFamily.Cursive, "cursive")
    )
    val textColors = listOf(
        Color.White, Color.Black, Color.Red, Color(0xFF4D6BFE),
        Color.Yellow, Color.Green, Color.Cyan, Color.Magenta
    )
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    LaunchedEffect(sourcePath) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                decodeSampled(sourcePath)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toNormalized(p: Offset): PointF {
        val bmp = bitmap
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        if (bmp == null || w <= 0f || h <= 0f) return PointF(0f, 0f)
        val s0 = min(w / bmp.width, h / bmp.height)
        val dw = bmp.width * s0
        val dh = bmp.height * s0
        val ox = (w - dw) / 2f + pan.x
        val oy = (h - dh) / 2f + pan.y
        return PointF((p.x - ox) / (dw * scale), (p.y - oy) / (dh * scale))
    }

    fun currentSnapshot() = EditSnapshot(strokes, texts, cropRect, aspectChoice)

    fun pushSnapshot() {
        undoStack = undoStack + currentSnapshot()
        redoStack = emptyList()
    }

    fun applySnapshot(s: EditSnapshot) {
        strokes = s.strokes
        texts = s.texts
        cropRect = s.cropRect
        aspectChoice = s.aspectChoice
    }

    fun undo() {
        val last = undoStack.lastOrNull() ?: return
        redoStack = redoStack + currentSnapshot()
        applySnapshot(last)
        undoStack = undoStack.dropLast(1)
    }

    fun redo() {
        val next = redoStack.lastOrNull() ?: return
        undoStack = undoStack + currentSnapshot()
        applySnapshot(next)
        redoStack = redoStack.dropLast(1)
    }

    fun applyAspect(choice: AspectChoice) {
        if (choice == aspectChoice) return
        pushSnapshot()
        aspectChoice = choice
        val bmp = bitmap
        cropRect = if (bmp == null || choice.ratio == null) {
            RectF(0f, 0f, 1f, 1f)
        } else {
            rectForAspect(bmp.width, bmp.height, choice.ratio)
        }
    }

    fun moveCropBy(dx: Float, dy: Float) {
        val cw = cropRect.width()
        val ch = cropRect.height()
        val nl = (cropRect.left + dx).coerceIn(0f, 1f - cw)
        val nt = (cropRect.top + dy).coerceIn(0f, 1f - ch)
        cropRect = RectF(nl, nt, nl + cw, nt + ch)
    }

    fun hitTestCropHandle(pos: Offset): CropHandle {
        val bmp = bitmap ?: return CropHandle.NONE
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        if (w <= 0f || h <= 0f) return CropHandle.NONE
        val s0 = min(w / bmp.width, h / bmp.height)
        val dw = bmp.width * s0
        val dh = bmp.height * s0
        val norm = toNormalized(pos)
        val touch = with(density) { 28.dp.toPx() }
        val tolX = touch / (dw * scale)
        val tolY = touch / (dh * scale)
        val r = cropRect
        val nearL = abs(norm.x - r.left) <= tolX
        val nearR = abs(norm.x - r.right) <= tolX
        val nearT = abs(norm.y - r.top) <= tolY
        val nearB = abs(norm.y - r.bottom) <= tolY
        val inX = norm.x >= r.left && norm.x <= r.right
        val inY = norm.y >= r.top && norm.y <= r.bottom
        return when {
            nearL && nearT -> CropHandle.TOP_LEFT
            nearR && nearT -> CropHandle.TOP_RIGHT
            nearL && nearB -> CropHandle.BOTTOM_LEFT
            nearR && nearB -> CropHandle.BOTTOM_RIGHT
            nearL && inY -> CropHandle.LEFT
            nearR && inY -> CropHandle.RIGHT
            nearT && inX -> CropHandle.TOP
            nearB && inX -> CropHandle.BOTTOM
            inX && inY -> CropHandle.MOVE
            else -> CropHandle.NONE
        }
    }

    fun resizeCropFree(handle: CropHandle, norm: PointF) {
        val minSize = 0.06f
        var l = cropRect.left
        var t = cropRect.top
        var r = cropRect.right
        var b = cropRect.bottom
        when (handle) {
            CropHandle.LEFT -> l = norm.x.coerceIn(0f, r - minSize)
            CropHandle.RIGHT -> r = norm.x.coerceIn(l + minSize, 1f)
            CropHandle.TOP -> t = norm.y.coerceIn(0f, b - minSize)
            CropHandle.BOTTOM -> b = norm.y.coerceIn(t + minSize, 1f)
            CropHandle.TOP_LEFT -> {
                l = norm.x.coerceIn(0f, r - minSize)
                t = norm.y.coerceIn(0f, b - minSize)
            }
            CropHandle.TOP_RIGHT -> {
                r = norm.x.coerceIn(l + minSize, 1f)
                t = norm.y.coerceIn(0f, b - minSize)
            }
            CropHandle.BOTTOM_LEFT -> {
                l = norm.x.coerceIn(0f, r - minSize)
                b = norm.y.coerceIn(t + minSize, 1f)
            }
            CropHandle.BOTTOM_RIGHT -> {
                r = norm.x.coerceIn(l + minSize, 1f)
                b = norm.y.coerceIn(t + minSize, 1f)
            }
            else -> return
        }
        cropRect = RectF(l, t, r, b)
    }

    fun resizeCropKeepRatio(handle: CropHandle, norm: PointF, k: Float) {
        if (k <= 0f) return
        val minSize = 0.06f
        val l0 = cropRect.left
        val t0 = cropRect.top
        val r0 = cropRect.right
        val b0 = cropRect.bottom
        var wN = r0 - l0
        var hN = b0 - t0
        when (handle) {
            CropHandle.LEFT -> wN = r0 - norm.x
            CropHandle.RIGHT -> wN = norm.x - l0
            CropHandle.TOP -> hN = b0 - norm.y
            CropHandle.BOTTOM -> hN = norm.y - t0
            CropHandle.TOP_LEFT, CropHandle.BOTTOM_LEFT -> wN = r0 - norm.x
            CropHandle.TOP_RIGHT, CropHandle.BOTTOM_RIGHT -> wN = norm.x - l0
            else -> return
        }
        when (handle) {
            CropHandle.TOP, CropHandle.BOTTOM -> wN = hN * k
            CropHandle.LEFT, CropHandle.RIGHT -> hN = wN / k
            else -> {
                val rawW = wN
                val rawH = if (handle == CropHandle.TOP_LEFT || handle == CropHandle.TOP_RIGHT) {
                    b0 - norm.y
                } else {
                    norm.y - t0
                }
                wN = min(rawW, rawH * k)
                hN = wN / k
            }
        }
        wN = wN.coerceIn(minSize, 1f)
        hN = (wN / k).coerceIn(minSize, 1f)
        wN = hN * k
        if (wN > 1f) {
            wN = 1f
            hN = 1f / k
        }
        val cx = (l0 + r0) / 2f
        val cy = (t0 + b0) / 2f
        var l = cx - wN / 2f
        var r = cx + wN / 2f
        var t = cy - hN / 2f
        var b = cy + hN / 2f
        if (l < 0f) {
            r -= l
            l = 0f
        }
        if (t < 0f) {
            b -= t
            t = 0f
        }
        if (r > 1f) {
            l -= r - 1f
            r = 1f
        }
        if (b > 1f) {
            t -= b - 1f
            b = 1f
        }
        cropRect = RectF(
            l.coerceIn(0f, 1f),
            t.coerceIn(0f, 1f),
            r.coerceIn(0f, 1f),
            b.coerceIn(0f, 1f)
        )
    }

    fun resizeCrop(handle: CropHandle, pos: Offset) {
        if (handle == CropHandle.NONE || handle == CropHandle.MOVE) return
        val norm = toNormalized(pos)
        val ratio = aspectChoice.ratio
        val bmp = bitmap
        if (ratio != null && bmp != null && bmp.width > 0 && bmp.height > 0) {
            resizeCropKeepRatio(handle, norm, ratio * bmp.height / bmp.width)
        } else {
            resizeCropFree(handle, norm)
        }
    }

    fun commitStroke() {
        val pts = currentStroke
        if (pts != null && pts.size >= 2) {
            pushSnapshot()
            strokes = strokes + ImageProcessor.Stroke(pts.toList(), brushColor.toArgb(), brushWidthRatio)
        }
        currentStroke = null
    }

    fun hitTestText(norm: PointF): Int? {
        var best: Int? = null
        var bestDist = Float.MAX_VALUE
        texts.forEachIndexed { i, t ->
            val dx = t.x - norm.x
            val dy = t.y - norm.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist <= t.size * 1.6f && dist < bestDist) {
                best = i
                bestDist = dist
            }
        }
        return best
    }

    fun moveTextBy(idx: Int, dx: Float, dy: Float) {
        if (idx !in texts.indices) return
        val t = texts[idx]
        texts = texts.toMutableList().also {
            it[idx] = t.copy(
                x = (t.x + dx).coerceIn(0f, 1f),
                y = (t.y + dy).coerceIn(0f, 1f)
            )
        }
    }

    fun openTextDialogForCreate(pos: PointF) {
        textDraftPos = pos
        selectedTextIndex = null
        showTextDialog = true
    }

    fun openTextDialogForEdit(idx: Int) {
        selectedTextIndex = idx
        showTextDialog = true
    }

    fun deleteSelectedText() {
        val idx = selectedTextIndex
        if (idx != null && idx in texts.indices) {
            pushSnapshot()
            texts = texts.filterIndexed { i, _ -> i != idx }
            selectedTextIndex = null
        }
    }

    fun isFullCrop(r: RectF): Boolean =
        r.left <= 0.001f && r.top <= 0.001f && r.right >= 0.999f && r.bottom >= 0.999f

    fun doSave(overwrite: Boolean, name: String?) {
        scope.launch {
            saving = true
            saveMessage = null
            try {
                val crop = if (isFullCrop(cropRect)) null else cropRect
                val request = ImageProcessor.Request(
                    sourcePath = sourcePath,
                    cropRect = crop,
                    format = if (overwrite) null else selectedFormat,
                    strokes = strokes,
                    texts = texts,
                    outputPath = if (overwrite) sourcePath else null
                )
                val result = imageProcessor.process(request, quality.toInt())
                if (overwrite) {
                    repository.updateAfterOverwrite(image.id, result)
                } else {
                    repository.createProcessedImage(image, result, name)
                }
                onSaved()
            } catch (e: Exception) {
                saveMessage = "保存失败：${e.message}"
            }
            saving = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) { Text("取消", color = Color.White) }
                Text(
                    "图片编辑",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = ::undo,
                    enabled = undoStack.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        "上一步",
                        color = if (undoStack.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.4f)
                    )
                }
                TextButton(
                    onClick = ::redo,
                    enabled = redoStack.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        "下一步",
                        color = if (redoStack.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.4f)
                    )
                }
                TextButton(
                    onClick = {
                        pushSnapshot()
                        strokes = emptyList()
                        currentStroke = null
                        texts = emptyList()
                        selectedTextIndex = null
                        cropRect = RectF(0f, 0f, 1f, 1f)
                        aspectChoice = AspectChoice("自由", null)
                        scale = 1f
                        pan = Offset.Zero
                        mode = EditMode.VIEW
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text("重置", color = Color.White) }
            }

            val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                val newScale = (scale * zoomChange).coerceIn(1f, 6f)
                if (newScale <= 1.001f) {
                    scale = 1f
                    pan = Offset.Zero
                } else {
                    scale = newScale
                    pan += panChange
                }
            }

            val gestureModifier = when (mode) {
                EditMode.VIEW -> Modifier.transformable(transformState)
                EditMode.CROP -> Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            cropHandle = hitTestCropHandle(pos)
                            if (cropHandle != CropHandle.NONE) pushSnapshot()
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            when (cropHandle) {
                                CropHandle.MOVE -> {
                                    val bmp = bitmap
                                    val w = canvasSize.width.toFloat()
                                    val h = canvasSize.height.toFloat()
                                    if (bmp != null && w > 0f && h > 0f) {
                                        val s0 = min(w / bmp.width, h / bmp.height)
                                        val dw = bmp.width * s0
                                        val dh = bmp.height * s0
                                        moveCropBy(drag.x / (dw * scale), drag.y / (dh * scale))
                                    }
                                }
                                CropHandle.NONE -> Unit
                                else -> resizeCrop(cropHandle, change.position)
                            }
                        },
                        onDragEnd = { cropHandle = CropHandle.NONE },
                        onDragCancel = { cropHandle = CropHandle.NONE }
                    )
                }
                EditMode.DRAW -> Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos -> currentStroke = listOf(toNormalized(pos)) },
                        onDragEnd = { commitStroke() },
                        onDragCancel = { currentStroke = null },
                        onDrag = { change, _ ->
                            change.consume()
                            currentStroke = currentStroke.orEmpty() + toNormalized(change.position)
                        }
                    )
                }
                EditMode.TEXT -> Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { pos ->
                            val norm = toNormalized(pos)
                            val idx = hitTestText(norm)
                            if (idx != null) openTextDialogForEdit(idx)
                            else openTextDialogForCreate(norm)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { pos -> textDragTarget = hitTestText(toNormalized(pos)) },
                            onDrag = { change, drag ->
                                change.consume()
                                val idx = textDragTarget
                                if (idx != null) {
                                    val bmp = bitmap
                                    val w = canvasSize.width.toFloat()
                                    val h = canvasSize.height.toFloat()
                                    if (bmp != null && w > 0f && h > 0f) {
                                        val s0 = min(w / bmp.width, h / bmp.height)
                                        val dw = bmp.width * s0
                                        val dh = bmp.height * s0
                                        moveTextBy(idx, drag.x / (dw * scale), drag.y / (dh * scale))
                                    }
                                }
                            },
                            onDragEnd = {
                                val idx = textDragTarget
                                if (idx != null) selectedTextIndex = idx
                                textDragTarget = null
                            },
                            onDragCancel = { textDragTarget = null }
                        )
                    }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)

                    .padding(horizontal = if (mode == EditMode.CROP) 32.dp else 0.dp)
                    .onSizeChanged { canvasSize = it }
                    .then(gestureModifier)
            ) {
                val bmp = bitmap ?: return@Canvas
                val w = size.width
                val h = size.height
                val s0 = min(w / bmp.width, h / bmp.height)
                val dw = bmp.width * s0
                val dh = bmp.height * s0
                val ox = (w - dw) / 2f
                val oy = (h - dh) / 2f

                withTransform({
                    translate(ox + pan.x, oy + pan.y)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    drawImage(
                        image = bmp.asImageBitmap(),
                        dstSize = IntSize(dw.toInt(), dh.toInt()),
                        filterQuality = FilterQuality.Medium
                    )
                    strokes.forEach { s ->
                        drawStrokeNormalized(s.points, Color(s.color), s.width, dw, dh)
                    }
                    currentStroke?.let { pts ->
                        drawStrokeNormalized(pts, brushColor, brushWidthRatio, dw, dh)
                    }
                    texts.forEachIndexed { i, t ->
                        drawTextItem(t, textMeasurer, dw, dh, i == selectedTextIndex && mode == EditMode.TEXT)
                    }
                    if (mode == EditMode.CROP) {
                        drawCropOverlay(cropRect, dw, dh, scale)
                    }
                }
            }

            Text(
                when {
                    mode == EditMode.VIEW -> "双指缩放 · 拖动浏览"
                    mode == EditMode.CROP -> "拖动边缘调整大小 · 拖动内部移动"
                    mode == EditMode.DRAW -> "在图片上绘制涂鸦"
                    else -> "点击添加文字 · 拖动移动 · 点击文字编辑"
                },
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            )

            saveMessage?.let {
                Text(
                    it,
                    color = Color(0xFFFFB4AB),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }

            if (mode == EditMode.CROP) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    aspects.forEach { choice ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 3.dp)
                                .background(
                                    if (aspectChoice == choice) Color(0xFF4D6BFE)
                                    else Color.White.copy(alpha = 0.15f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { applyAspect(choice) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                choice.label,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            if (mode == EditMode.DRAW) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    brushColors.forEach { c ->
                        val selected = brushColor == c
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(if (selected) 32.dp else 26.dp)
                                .background(c, CircleShape)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) Color.White else Color.White.copy(alpha = 0.35f),
                                    shape = CircleShape
                                )
                                .clickable { brushColor = c }
                        )
                    }
                    CustomColorButton(
                        initialColor = brushColor,
                        selected = brushColor !in brushColors
                    ) { brushColor = it }
                    Spacer(modifier = Modifier.weight(1f))
                    brushWidthOptions.forEach { (rto, dot) ->
                        val selected = brushWidthRatio == rto
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(36.dp)
                                .background(
                                    if (selected) Color.White.copy(alpha = 0.25f)
                                    else Color.Transparent,
                                    CircleShape
                                )
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                                .clickable { brushWidthRatio = rto },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(dot)
                                    .background(Color.White, CircleShape)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                EditorTool("裁剪", mode == EditMode.CROP, Modifier.width(76.dp)) {
                    mode = if (mode == EditMode.CROP) EditMode.VIEW else EditMode.CROP
                }
                EditorTool("涂鸦", mode == EditMode.DRAW, Modifier.width(76.dp)) {
                    mode = if (mode == EditMode.DRAW) EditMode.VIEW else EditMode.DRAW
                }
                EditorTool("文字", mode == EditMode.TEXT, Modifier.width(76.dp)) {
                    mode = if (mode == EditMode.TEXT) EditMode.VIEW else EditMode.TEXT
                }
                EditorTool("格式", false, Modifier.width(76.dp)) { showFormatDialog = true }
                EditorTool("保存", false, Modifier.width(76.dp)) { showSaveDialog = true }
            }
        }

        if (saving) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text("处理中...", color = Color.White)
            }
        }
    }

    if (showFormatDialog) {
        FormatDialog(
            currentFormat = selectedFormat,
            currentQuality = quality,
            onDismiss = { showFormatDialog = false },
            onConfirm = { fmt, q ->
                selectedFormat = fmt
                quality = q
                showFormatDialog = false
            }
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存图片") },
            text = { Text("选择保存方式") },
            confirmButton = {
                TextButton(onClick = {
                    showSaveDialog = false
                    showNameDialog = true
                }) { Text("保存为新图片") }
            },
            dismissButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        showSaveDialog = false
                        doSave(overwrite = true, name = null)
                    }) { Text("覆盖原图") }
                    TextButton(onClick = { showSaveDialog = false }) { Text("取消") }
                }
            }
        )
    }

    if (showNameDialog) {
        var name by remember { mutableStateOf(baseName(image.fileName)) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("新图片名称") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("文件名（不含扩展名）") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val n = name.trim()
                        if (n.isNotEmpty()) {
                            showNameDialog = false
                            doSave(overwrite = false, name = n)
                        }
                    },
                    enabled = name.trim().isNotEmpty() && !saving
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("取消") }
            }
        )
    }

    if (showTextDialog) {
        val editing = selectedTextIndex?.let { texts.getOrNull(it) }
        TextDialog(
            initialText = editing?.text ?: "",
            initialColor = editing?.let { Color(it.color) } ?: Color.White,
            initialSize = editing?.size ?: 0.08f,
            initialBold = editing?.bold ?: false,
            initialItalic = editing?.italic ?: false,
            initialUnderline = editing?.underline ?: false,
            initialStrike = editing?.strikethrough ?: false,
            initialFontName = editing?.fontFamily,
            fontChoices = fontChoices,
            colors = textColors,
            isEdit = selectedTextIndex != null,
            onDismiss = { showTextDialog = false },
            onDelete = {
                deleteSelectedText()
                showTextDialog = false
            },
            onConfirm = { text, color, size, bold, italic, underline, strike, fontName ->
                val content = text.trim()
                if (content.isNotEmpty()) {
                    val idx = selectedTextIndex
                    val item = ImageProcessor.TextItem(
                        text = content,
                        x = editing?.x ?: textDraftPos.x,
                        y = editing?.y ?: textDraftPos.y,
                        color = color.toArgb(),
                        size = size,
                        bold = bold,
                        italic = italic,
                        underline = underline,
                        strikethrough = strike,
                        fontFamily = fontName
                    )
                    if (idx != null && idx in texts.indices) {
                        pushSnapshot()
                        texts = texts.toMutableList().also { it[idx] = item }
                    } else {
                        pushSnapshot()
                        texts = texts + item
                    }
                }
                showTextDialog = false
            }
        )
    }
}

private fun decodeSampled(path: String): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, opts) ?: error("无法解码图片")
}

private fun rectForAspect(bw: Int, bh: Int, rto: Float): RectF {
    val srcRto = bw.toFloat() / bh
    val cw: Float
    val ch: Float
    if (srcRto > rto) {
        ch = 1f
        cw = rto / srcRto
    } else {
        cw = 1f
        ch = srcRto / rto
    }
    val left = (1f - cw) / 2f
    val top = (1f - ch) / 2f
    return RectF(left, top, left + cw, top + ch)
}

private fun baseName(fileName: String): String {
    val dot = fileName.lastIndexOf('.')
    val base = if (dot > 0) fileName.substring(0, dot) else fileName
    return "${base}_编辑"
}

@Composable
private fun EditorTool(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .background(
                if (selected) Color(0xFF4D6BFE) else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Text(
            label,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        )
    }
}

@Composable
private fun FormatDialog(
    currentFormat: ImageProcessor.OutputFormat?,
    currentQuality: Float,
    onDismiss: () -> Unit,
    onConfirm: (ImageProcessor.OutputFormat?, Float) -> Unit
) {
    var format by remember { mutableStateOf(currentFormat) }
    var quality by remember { mutableStateOf(currentQuality) }
    val options = listOf<Pair<ImageProcessor.OutputFormat?, String>>(
        null to "保持原格式",
        ImageProcessor.OutputFormat.PNG to "PNG",
        ImageProcessor.OutputFormat.JPEG to "JPEG",
        ImageProcessor.OutputFormat.WEBP to "WebP"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输出格式") },
        text = {
            Column {
                options.forEach { (fmt, label) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(
                                if (format == fmt) Color(0xFF4D6BFE)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { format = fmt }
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (format == fmt) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text("质量", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = quality,
                    onValueChange = { quality = it },
                    valueRange = 0f..100f
                )
                Text("${quality.toInt()}", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(format, quality) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun DrawScope.drawStrokeNormalized(
    points: List<PointF>,
    color: Color,
    widthRto: Float,
    dw: Float,
    dh: Float
) {
    if (points.size < 2) return
    val path = Path()
    path.moveTo(points[0].x * dw, points[0].y * dh)
    for (i in 1 until points.size) {
        path.lineTo(points[i].x * dw, points[i].y * dh)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = (widthRto * dw).coerceAtLeast(1f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun DrawScope.drawCropOverlay(r: RectF, dw: Float, dh: Float, scale: Float) {
    val l = r.left * dw
    val t = r.top * dh
    val rr = r.right * dw
    val b = r.bottom * dh
    val mask = Color.Black.copy(alpha = 0.55f)
    drawRect(mask, topLeft = Offset(0f, 0f), size = Size(dw, t))
    drawRect(mask, topLeft = Offset(0f, b), size = Size(dw, dh - b))
    drawRect(mask, topLeft = Offset(0f, t), size = Size(l, b - t))
    drawRect(mask, topLeft = Offset(rr, t), size = Size(dw - rr, b - t))
    val border = 2.dp.toPx() / scale
    drawRect(
        color = Color.White,
        topLeft = Offset(l, t),
        size = Size(rr - l, b - t),
        style = Stroke(width = border)
    )
    val grid = 1.dp.toPx() / scale
    val tw = (rr - l) / 3f
    val th = (b - t) / 3f
    val gridColor = Color.White.copy(alpha = 0.7f)
    drawLine(gridColor, Offset(l + tw, t), Offset(l + tw, b), grid)
    drawLine(gridColor, Offset(l + 2 * tw, t), Offset(l + 2 * tw, b), grid)
    drawLine(gridColor, Offset(l, t + th), Offset(rr, t + th), grid)
    drawLine(gridColor, Offset(l, t + 2 * th), Offset(rr, t + 2 * th), grid)

    val handleLen = 20.dp.toPx() / scale
    val handleThick = 4.dp.toPx() / scale
    val handleColor = Color.White
    val half = handleLen / 2f
    val midX = (l + rr) / 2f
    val midY = (t + b) / 2f
    drawLine(handleColor, Offset(l, t), Offset(l + handleLen, t), handleThick, cap = StrokeCap.Round)
    drawLine(handleColor, Offset(l, t), Offset(l, t + handleLen), handleThick, cap = StrokeCap.Round)
    drawLine(handleColor, Offset(rr, t), Offset(rr - handleLen, t), handleThick, cap = StrokeCap.Round)
    drawLine(handleColor, Offset(rr, t), Offset(rr, t + handleLen), handleThick, cap = StrokeCap.Round)
    drawLine(handleColor, Offset(l, b), Offset(l + handleLen, b), handleThick, cap = StrokeCap.Round)
    drawLine(handleColor, Offset(l, b), Offset(l, b - handleLen), handleThick, cap = StrokeCap.Round)
    drawLine(handleColor, Offset(rr, b), Offset(rr - handleLen, b), handleThick, cap = StrokeCap.Round)
    drawLine(handleColor, Offset(rr, b), Offset(rr, b - handleLen), handleThick, cap = StrokeCap.Round)
    drawLine(handleColor, Offset(midX - half, t), Offset(midX + half, t), handleThick, cap = StrokeCap.Round)
    drawLine(handleColor, Offset(midX - half, b), Offset(midX + half, b), handleThick, cap = StrokeCap.Round)
    drawLine(handleColor, Offset(l, midY - half), Offset(l, midY + half), handleThick, cap = StrokeCap.Round)
    drawLine(handleColor, Offset(rr, midY - half), Offset(rr, midY + half), handleThick, cap = StrokeCap.Round)
}

@Composable
private fun TextDialog(
    initialText: String,
    initialColor: Color,
    initialSize: Float,
    initialBold: Boolean,
    initialItalic: Boolean,
    initialUnderline: Boolean,
    initialStrike: Boolean,
    initialFontName: String?,
    fontChoices: List<FontChoice>,
    colors: List<Color>,
    isEdit: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: (String, Color, Float, Boolean, Boolean, Boolean, Boolean, String?) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var color by remember { mutableStateOf(initialColor) }
    var size by remember { mutableStateOf(initialSize) }
    var bold by remember { mutableStateOf(initialBold) }
    var italic by remember { mutableStateOf(initialItalic) }
    var underline by remember { mutableStateOf(initialUnderline) }
    var strike by remember { mutableStateOf(initialStrike) }
    var fontName by remember { mutableStateOf(initialFontName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "编辑文字" else "添加文字") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("文字内容") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.size(12.dp))
                Text("颜色", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colors.forEach { c ->
                        val selected = color == c
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 5.dp)
                                .size(if (selected) 32.dp else 26.dp)
                                .background(c, CircleShape)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) Color.Black else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { color = c }
                        )
                    }
                    CustomColorButton(
                        initialColor = color,
                        selected = color !in colors
                    ) { color = it }
                }
                Spacer(modifier = Modifier.size(12.dp))
                Text("字号：${(size * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = size,
                    onValueChange = { size = it },
                    valueRange = 0.02f..0.5f
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("字体", style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    fontChoices.forEach { fc ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .background(
                                    if (fontName == fc.typefaceName) Color(0xFF4D6BFE)
                                    else Color.Gray.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { fontName = fc.typefaceName }
                        ) {
                            Text(
                                fc.label,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text("样式", style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    TextStyleToggle("加粗", bold) { bold = it }
                    TextStyleToggle("倾斜", italic) { italic = it }
                    TextStyleToggle("下划线", underline) { underline = it }
                    TextStyleToggle("删除线", strike) { strike = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text, color, size, bold, italic, underline, strike, fontName) },
                enabled = text.trim().isNotEmpty()
            ) { Text("确定") }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (isEdit) {
                    TextButton(onClick = onDelete) { Text("删除", color = Color(0xFFE53935)) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun TextStyleToggle(label: String, selected: Boolean, onToggle: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .background(
                if (selected) Color(0xFF4D6BFE) else Color.Gray.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .clickable { onToggle(!selected) }
    ) {
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

private fun DrawScope.drawTextItem(
    t: ImageProcessor.TextItem,
    measurer: TextMeasurer,
    dw: Float,
    dh: Float,
    selected: Boolean
) {
    if (t.text.isEmpty()) return
    val style = TextStyle(
        color = Color(t.color),
        fontSize = (t.size * dw).toSp(),
        fontWeight = if (t.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (t.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = when {
            t.underline && t.strikethrough -> TextDecoration.Underline + TextDecoration.LineThrough
            t.underline -> TextDecoration.Underline
            t.strikethrough -> TextDecoration.LineThrough
            else -> TextDecoration.None
        },
        fontFamily = fontFamilyByName(t.fontFamily)
    )
    val layout = measurer.measure(AnnotatedString(t.text), style)
    val cx = t.x * dw
    val cy = t.y * dh
    val topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f)
    if (selected) {
        drawRect(
            color = Color(0xFF4D6BFE),
            topLeft = topLeft - Offset(4f, 4f),
            size = Size(layout.size.width + 8f, layout.size.height + 8f),
            style = Stroke(width = 2f)
        )
    }
    drawText(layout, topLeft = topLeft)
}

private fun fontFamilyByName(name: String?): FontFamily = when (name) {
    "serif" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    else -> FontFamily.SansSerif
}

@Composable
private fun CustomColorButton(
    initialColor: Color,
    selected: Boolean,
    onPicked: (Color) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .padding(horizontal = 5.dp)
            .size(if (selected) 32.dp else 26.dp)
            .background(
                Brush.sweepGradient(
                    listOf(
                        Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                        Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000)
                    )
                ),
                CircleShape
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable { showPicker = true }
    )
    if (showPicker) {
        ColorPickerDialog(
            initialColor = initialColor,
            onDismiss = { showPicker = false },
            onConfirm = { c ->
                onPicked(c)
                showPicker = false
            }
        )
    }
}

@Composable
private fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor.toArgb(), it) }
    }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var value by remember { mutableStateOf(initialHsv[2]) }
    val currentColor = Color.hsv(hue, saturation, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义颜色") },
        text = {
            Column {
                SvGradientBox(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onSelect = { s, v ->
                        saturation = s
                        value = v
                    }
                )
                Spacer(modifier = Modifier.size(12.dp))
                HueBar(hue = hue, onSelect = { hue = it })
                Spacer(modifier = Modifier.size(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(currentColor, RoundedCornerShape(6.dp))
                            .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "#${(currentColor.toArgb() and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentColor) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun SvGradientBox(
    hue: Float,
    saturation: Float,
    value: Float,
    onSelect: (Float, Float) -> Unit
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures { pos -> svSelect(pos, boxSize, onSelect) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos -> svSelect(pos, boxSize, onSelect) },
                    onDrag = { change, _ ->
                        change.consume()
                        svSelect(change.position, boxSize, onSelect)
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.hsv(hue, 0f, 1f), Color.hsv(hue, 1f, 1f))
            ),
            size = size
        )
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black)
            ),
            size = size
        )
        val ix = saturation * w
        val iy = (1f - value) * h
        drawCircle(
            color = Color.White,
            radius = 8f,
            center = Offset(ix, iy),
            style = Stroke(width = 3f)
        )
    }
}

@Composable
private fun HueBar(hue: Float, onSelect: (Float) -> Unit) {
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .onSizeChanged { barSize = it }
            .pointerInput(Unit) {
                detectTapGestures { pos -> hueSelect(pos, barSize, onSelect) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos -> hueSelect(pos, barSize, onSelect) },
                    onDrag = { change, _ ->
                        change.consume()
                        hueSelect(change.position, barSize, onSelect)
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(
                    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                    Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000)
                )
            ),
            size = size
        )
        val ix = hue / 360f * w
        drawLine(
            color = Color.Black,
            start = Offset(ix, 0f),
            end = Offset(ix, h),
            strokeWidth = 4f
        )
    }
}

private fun svSelect(pos: Offset, boxSize: IntSize, onSelect: (Float, Float) -> Unit) {
    if (boxSize.width <= 0 || boxSize.height <= 0) return
    val s = (pos.x / boxSize.width).coerceIn(0f, 1f)
    val v = (1f - pos.y / boxSize.height).coerceIn(0f, 1f)
    onSelect(s, v)
}

private fun hueSelect(pos: Offset, barSize: IntSize, onSelect: (Float) -> Unit) {
    if (barSize.width <= 0) return
    onSelect((pos.x / barSize.width * 360f).coerceIn(0f, 360f))
}
