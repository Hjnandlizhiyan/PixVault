package com.pixvault.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.pixvault.R
import com.pixvault.data.db.entity.ImageEntity
import com.pixvault.data.db.entity.TagEntity
import com.pixvault.data.embedding.EmbeddingService
import com.pixvault.data.processor.ImageProcessor
import com.pixvault.data.repository.ImageRepository
import com.pixvault.data.repository.TagRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    image: ImageEntity,
    repository: ImageRepository,
    tagRepository: TagRepository,
    embeddingService: EmbeddingService,
    imageProcessor: ImageProcessor,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onFindSimilar: () -> Unit,
    onOpenViewer: () -> Unit,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenEditor: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var favorite by remember { mutableStateOf(image.isFavorite) }
    var showAddTag by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf(image.fileName) }
    var showRenameDialog by remember { mutableStateOf(false) }

    var processMessage by remember { mutableStateOf("") }
    val tags by tagRepository.observeTagsForImage(image.id).collectAsState(initial = emptyList())
    var similarTags by remember { mutableStateOf<List<Pair<TagEntity, Float>>>(emptyList()) }


    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(image.mimeType)
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = imageProcessor.export(image.path ?: image.uri, uri)
                processMessage = if (ok) "已导出到所选位置" else "导出失败"
            }
        }
    }

    LaunchedEffect(image.id) {
        similarTags = tagRepository.getSimilarTags(image.id)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onClick = onBack)
            Text(
                fileName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { showRenameDialog = true }) { Text("改名") }
            TextButton(onClick = {
                favorite = !favorite
                scope.launch { repository.updateFavorite(image.id, favorite) }
            }) {
                Text(if (favorite) "已收藏" else "收藏")
            }
        }

        SmartImagePreview(
            image = image,
            onOpenViewer = onOpenViewer,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(horizontal = 8.dp)
        ) {
            if (canGoPrev) {
                NavArrow("‹", onPrev, Modifier.align(Alignment.CenterStart))
            }
            if (canGoNext) {
                NavArrow("›", onNext, Modifier.align(Alignment.CenterEnd))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Button(
                onClick = onOpenEditor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("图片编辑")
            }
            Button(
                onClick = { exportLauncher.launch(fileName) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("导出")
            }
            if (processMessage.isNotEmpty()) {
                Text(
                    processMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    "标签",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showAddTag = true }) { Text("添加") }
            }
            if (tags.isEmpty()) {
                Text("暂无标签", style = MaterialTheme.typography.bodySmall)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        TagChip(tag.name) {
                            scope.launch { tagRepository.removeTag(image.id, tag.id) }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.empty_gallery_anime),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    contentScale = ContentScale.Fit
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        "AI 标签助手",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "根据本地模型推荐标签",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (similarTags.isEmpty()) {
                Text(
                    "暂无推荐结果：请先在「标签」页为标签设置正例样本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Text(
                    "点击可添加到当前图片",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    similarTags.forEach { (tag, sim) ->
                        val added = tags.any { it.id == tag.id }
                        RecommendTagChip(
                            name = tag.name,
                            similarity = sim,
                            added = added,
                            onClick = {
                                if (!added) {
                                    scope.launch { tagRepository.addTag(image.id, tag.id) }
                                }
                            }
                        )
                    }
                }
            }

            Button(
                onClick = onFindSimilar,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("找相似")
            }

            InfoRow("尺寸", "${image.width} × ${image.height}")
            InfoRow("大小", formatSize(image.fileSize))
            InfoRow("类型", image.mimeType)
            InfoRow("导入时间", formatTime(image.createdTime))
            image.dateTaken?.let { InfoRow("拍摄时间", formatTime(it)) }
            if (image.latitude != null && image.longitude != null) {
                InfoRow(
                    "位置",
                    String.format(Locale.US, "%.5f, %.5f", image.latitude, image.longitude)
                )
                TextButton(
                    onClick = {
                        val geo = Uri.parse(
                            "geo:${image.latitude},${image.longitude}" +
                                "?q=${image.latitude},${image.longitude}"
                        )
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, geo))
                        }.onFailure {
                            Toast.makeText(context, "未找到可用的地图应用", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("在地图中查看拍摄位置")
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        repository.delete(image.id)
                        onDeleted()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("移入回收站", color = Color.White)
            }
        }
    }

    if (showRenameDialog) {
        RenameDialog(
            currentName = fileName,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                fileName = newName
                scope.launch { repository.rename(image.id, newName) }
                showRenameDialog = false
            }
        )
    }


    if (showAddTag) {
        AddTagDialog(
            tagRepository = tagRepository,
            existingTagIds = tags.map { it.id }.toSet(),
            onDismiss = { showAddTag = false },
            onConfirm = { selectedIds ->
                scope.launch {
                    for (tagId in selectedIds) {
                        tagRepository.addTag(image.id, tagId)
                    }
                }
                showAddTag = false
            }
        )
    }
}

private enum class PreviewMode {
    Fit,
    Tall,
    Panorama
}

@Composable
private fun SmartImagePreview(
    image: ImageEntity,
    onOpenViewer: () -> Unit,
    modifier: Modifier = Modifier,
    controls: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    val width = image.width.coerceAtLeast(1)
    val height = image.height.coerceAtLeast(1)
    val aspect = width.toFloat() / height
    val mode = when {
        height.toFloat() / width >= 1.8f -> PreviewMode.Tall
        aspect >= 2f -> PreviewMode.Panorama
        else -> PreviewMode.Fit
    }
    val verticalState = rememberScrollState()
    val horizontalState = rememberScrollState()

    LaunchedEffect(image.id) {
        verticalState.scrollTo(0)
        horizontalState.scrollTo(0)
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center
    ) {
        val viewportWidth = maxWidth
        val viewportHeight = maxHeight
        when (mode) {
            PreviewMode.Tall -> {
                val renderedHeight = viewportWidth * (height.toFloat() / width)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalState)
                        .clickable(onClick = onOpenViewer)
                ) {
                    AsyncImage(
                        model = image.uri,
                        contentDescription = image.fileName,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .width(viewportWidth)
                            .height(renderedHeight)
                    )
                }
            }
            PreviewMode.Panorama -> {
                val renderedWidth = viewportHeight * aspect
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalState)
                        .clickable(onClick = onOpenViewer)
                ) {
                    AsyncImage(
                        model = image.uri,
                        contentDescription = image.fileName,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .width(renderedWidth)
                            .height(viewportHeight)
                    )
                }
            }
            PreviewMode.Fit -> {
                AsyncImage(
                    model = image.uri,
                    contentDescription = image.fileName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clickable(onClick = onOpenViewer)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = 0.58f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        ) {
            Text(
                text = when (mode) {
                    PreviewMode.Tall -> "长图 · 上下滑动"
                    PreviewMode.Panorama -> "全景 · 左右滑动"
                    PreviewMode.Fit -> "点击查看原图"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        controls()
    }
}

@Composable
private fun NavArrow(symbol: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("文件名") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.trim().isNotEmpty()
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun TagChip(name: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(
                "×",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 6.dp)
            )
        }
    }
}

@Composable
private fun RecommendTagChip(
    name: String,
    similarity: Float,
    added: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val background = if (added) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        primary.copy(alpha = 0.15f)
    }
    val textColor = if (added) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        primary
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = background,
        modifier = Modifier.clickable(enabled = !added, onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = textColor)
            Text(
                " ${"%.0f".format(similarity * 100)}%",
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                modifier = Modifier.padding(start = 6.dp)
            )
            if (added) {
                Text(
                    " 已添加",
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddTagDialog(
    tagRepository: TagRepository,
    existingTagIds: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    val tags by tagRepository.observeTags().collectAsState(initial = emptyList())
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var newTagName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val available = tags.filter { it.id !in existingTagIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加标签") },
        text = {
            Column {
                if (available.isEmpty()) {
                    Text("暂无其他标签，可在下方创建", style = MaterialTheme.typography.bodySmall)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        available.forEach { tag ->
                            SelectableTagChip(
                                name = tag.name,
                                selected = tag.id in selectedIds,
                                onToggle = {
                                    selectedIds = if (tag.id in selectedIds) {
                                        selectedIds - tag.id
                                    } else {
                                        selectedIds + tag.id
                                    }
                                }
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    OutlinedTextField(
                        value = newTagName,
                        onValueChange = { newTagName = it },
                        singleLine = true,
                        label = { Text("新标签名") },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            val name = newTagName.trim()
                            if (name.isNotEmpty()) {
                                scope.launch {
                                    val id = tagRepository.createTag(name)
                                    selectedIds = selectedIds + id
                                }
                                newTagName = ""
                            }
                        },
                        enabled = newTagName.trim().isNotEmpty()
                    ) { Text("创建") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedIds.toList()) },
                enabled = selectedIds.isNotEmpty()
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun SelectableTagChip(name: String, selected: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.clickable(onClick = onToggle)
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))


