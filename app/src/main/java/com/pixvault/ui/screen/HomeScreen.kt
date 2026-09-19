package com.pixvault.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pixvault.R
import com.pixvault.data.db.entity.ImageEntity
import com.pixvault.data.embedding.EmbeddingService
import com.pixvault.data.importer.ImageImporter
import com.pixvault.data.repository.ImageRepository
import com.pixvault.data.repository.TagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class GallerySort(val label: String) {
    Newest("最新"), Oldest("最早"), Name("名称"), Size("大小")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    repository: ImageRepository,
    importer: ImageImporter,
    embeddingService: EmbeddingService,
    tagRepository: TagRepository,
    onImageClick: (List<ImageEntity>, Int) -> Unit,
    onManageTags: () -> Unit,
    onOpenFolders: () -> Unit
) {
    val images by repository.observeImages().collectAsState(initial = emptyList())
    val imageTagNames by tagRepository.observeImageTagNames().collectAsState(initial = emptyList())
    var importing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var pendingUris by remember { mutableStateOf<List<Uri>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showBatchTagDialog by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var semanticMode by remember { mutableStateOf(false) }
    var semanticResults by remember { mutableStateOf<List<ImageEntity>?>(null) }
    var semanticLoading by remember { mutableStateOf(false) }
    var semanticMessage by remember { mutableStateOf("") }
    var sortModeName by rememberSaveable { mutableStateOf(GallerySort.Newest.name) }
    var gridColumns by rememberSaveable { mutableIntStateOf(3) }
    var controlsExpanded by rememberSaveable { mutableStateOf(true) }
    val sortMode = GallerySort.valueOf(sortModeName)
    val scope = rememberCoroutineScope()

    val tagNamesByImage = remember(imageTagNames) {
        imageTagNames.groupBy { it.imageId }.mapValues { e -> e.value.map { it.tagName }.toSet() }
    }

    val displayedImages = remember(
        images,
        tagNamesByImage,
        searchQuery,
        semanticMode,
        semanticResults,
        sortMode
    ) {
        val filtered = if (semanticMode) {
            semanticResults ?: images
        } else {
            filterImages(images, tagNamesByImage, searchQuery)
        }
        if (semanticMode && semanticResults != null) {
            filtered // Keep relevance order for semantic results.
        } else {
            when (sortMode) {
                GallerySort.Newest -> filtered.sortedByDescending { it.modifiedTime }
                GallerySort.Oldest -> filtered.sortedBy { it.modifiedTime }
                GallerySort.Name -> filtered.sortedBy { it.fileName.lowercase() }
                GallerySort.Size -> filtered.sortedByDescending { it.fileSize }
            }
        }
    }

    val runSemanticSearch: () -> Unit = {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            semanticMessage = "请输入要搜索的画面描述"
        } else {
            scope.launch {
                semanticLoading = true
                try {
                    embeddingService.ensureTextLoaded { msg -> semanticMessage = msg }
                    semanticMessage = "正在语义检索..."
                    val vector = embeddingService.embedText(query)
                    val results = withContext(Dispatchers.IO) {
                        repository.searchByText(vector, 60)
                    }
                    semanticResults = results.map { it.first }
                    semanticMessage = if (results.isEmpty()) {
                        "没有找到相关图片，请先为图片计算特征"
                    } else {
                        "按相似度找到 ${results.size} 张图片"
                    }
                } catch (e: Exception) {
                    semanticResults = emptyList()
                    semanticMessage = "语义搜索失败：${e.message}"
                } finally {
                    semanticLoading = false
                }
            }
        }
    }

    val runImport: (List<Uri>, List<Long>) -> Unit = { uris, tagIds ->
        scope.launch {
            importing = true
            message = "正在导入..."
            val pairs = withContext(Dispatchers.IO) {
                val entities = importer.import(uris)
                val ids = repository.insertAll(entities)
                val insertedIds = ids.filter { it > 0 }
                for (tagId in tagIds) {
                    tagRepository.addTagToImages(insertedIds, tagId)
                }
                entities.zip(ids).mapNotNull { (entity, id) ->
                    if (id <= 0) null else entity.path?.let { path -> id to path }
                }
            }
            if (pairs.isNotEmpty()) {
                embeddingService.ensureLoaded { msg -> message = msg }
                var done = 0
                for ((id, path) in pairs) {
                    try {
                        val vector = embeddingService.embed(path)
                        repository.updateEmbedding(id, vector)
                    } catch (_: Exception) {
                    }
                    done++
                    message = "计算特征 $done/${pairs.size}"
                }
            }
            val skipped = (uris.size - pairs.size).coerceAtLeast(0)
            message = if (skipped == 0) {
                "已导入 ${pairs.size} 张"
            } else {
                "已导入 ${pairs.size} 张，跳过 $skipped 张重复或无效图片"
            }
            importing = false
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingUris = uris
        }
    }

    val exitSelection = {
        selectionMode = false
        selectedIds = emptySet()
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (selectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = exitSelection) { Text("取消") }
                Text(
                    "已选 ${selectedIds.size} 张",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        val all = displayedImages.map { it.id }.toSet()
                        selectedIds = if (all.isNotEmpty() && selectedIds.containsAll(all)) {
                            emptySet()
                        } else {
                            all
                        }
                    }
                ) {
                    Text(
                        if (displayedImages.isNotEmpty() && selectedIds.containsAll(displayedImages.map { it.id })) {
                            "取消全选"
                        } else {
                            "全选"
                        }
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.pixvault_workshop_badge),
                        contentDescription = "PixVault 素材工坊",
                        modifier = Modifier.size(44.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "素材工坊",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = BrandBlue
                        )
                        Text(
                            "${images.size} 张",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { controlsExpanded = !controlsExpanded }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_expand_more),
                            contentDescription = if (controlsExpanded) "收起首页控制区" else "展开首页控制区",
                            modifier = Modifier.rotate(if (controlsExpanded) 180f else 0f)
                        )
                    }
                    Button(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !importing,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_import),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("导入")
                    }
                }
                if (controlsExpanded) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(22.dp)),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = { selectionMode = true }) {
                                Icon(
                                    painterResource(R.drawable.ic_nav_select),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(" 多选")
                            }
                            TextButton(onClick = onOpenFolders) {
                                Icon(
                                    painterResource(R.drawable.ic_nav_folder),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(" 文件夹")
                            }
                            TextButton(onClick = onManageTags) {
                                Icon(
                                    painterResource(R.drawable.ic_nav_tag),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(" 标签")
                            }
                        }
                    }
                }
            }
        }

        if (controlsExpanded && !selectionMode) {
            if (images.isNotEmpty()) {
                AiMascotBanner(
                    imageCount = images.size,
                    semanticMode = semanticMode,
                    onClick = {
                        semanticMode = !semanticMode
                        semanticResults = null
                        semanticMessage = ""
                    }
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = {
                    Text(if (semanticMode) "描述画面，如：海边的日落" else "搜索文件名或 #标签")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !semanticMode,
                    onClick = {
                        semanticMode = false
                        semanticResults = null
                        semanticMessage = ""
                    },
                    label = { Text("文件名/标签") }
                )
                FilterChip(
                    selected = semanticMode,
                    onClick = {
                        if (!semanticMode) {
                            semanticMode = true
                            semanticResults = null
                            semanticMessage = ""
                        }
                    },
                    label = { Text("语义搜图") }
                )
                if (semanticMode) {
                    Button(
                        onClick = runSemanticSearch,
                        enabled = !semanticLoading && searchQuery.isNotBlank(),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Text("搜索")
                    }
                }
            }
        }

        if (!semanticMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        val values = GallerySort.entries
                        sortModeName = values[(sortMode.ordinal + 1) % values.size].name
                    }
                ) {
                    Text("排序：${sortMode.label}")
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { gridColumns = (gridColumns - 1).coerceAtLeast(2) },
                    enabled = gridColumns > 2
                ) { Text("放大") }
                Text("${gridColumns} 列", style = MaterialTheme.typography.bodySmall)
                TextButton(
                    onClick = { gridColumns = (gridColumns + 1).coerceAtMost(5) },
                    enabled = gridColumns < 5
                ) { Text("缩小") }
            }
        }

        if (controlsExpanded && semanticLoading) {
            InlineMascotStatus("正在理解你的描述并匹配图片")
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (controlsExpanded && semanticMode && semanticMessage.isNotEmpty()) {
            Text(
                semanticMessage,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (selectionMode) {
            val actionsEnabled = selectedIds.isNotEmpty()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    SelectionActionItem(
                        label = "收藏",
                        icon = R.drawable.ic_action_favorite,
                        enabled = actionsEnabled,
                        onClick = {
                            val ids = selectedIds.toList()
                            scope.launch {
                                repository.updateFavoriteAll(ids, true)
                                message = "已收藏 ${ids.size} 张"
                                exitSelection()
                            }
                        }
                    )
                    SelectionActionItem(
                        label = "加标签",
                        icon = R.drawable.ic_nav_tag,
                        enabled = actionsEnabled,
                        onClick = { showBatchTagDialog = true }
                    )
                    SelectionActionItem(
                        label = "私密",
                        icon = R.drawable.ic_nav_private,
                        enabled = actionsEnabled,
                        onClick = {
                            val ids = selectedIds.toList()
                            scope.launch {
                                repository.updatePrivateAll(ids, true)
                                message = "已将 ${ids.size} 张移入私密空间"
                                exitSelection()
                            }
                        }
                    )
                    SelectionActionItem(
                        label = "删除",
                        icon = R.drawable.ic_action_delete,
                        enabled = actionsEnabled,
                        destructive = true,
                        onClick = { showBatchDeleteDialog = true }
                    )
                }
            }
        }

        if (importing) {
            InlineMascotStatus("正在导入并建立本地图片特征")
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (message.isNotEmpty()) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        when {
            images.isEmpty() && !importing -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.empty_gallery_anime),
                            contentDescription = null,
                            modifier = Modifier.width(220.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "把喜欢的瞬间收进 PixVault",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "点击「导入」选择照片，整理与搜索都在本机完成",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            displayedImages.isEmpty() -> {
                MascotEmptyState(message = "没有找到匹配的图片，换个关键词试试吧")
            }
            else -> {
                TimelineImageGrid(
                    images = displayedImages,
                    columns = gridColumns,
                    selectedIds = if (selectionMode) selectedIds else emptySet(),
                    onImageClick = { image ->
                        if (selectionMode) {
                            selectedIds = if (image.id in selectedIds) {
                                selectedIds - image.id
                            } else {
                                selectedIds + image.id
                            }
                        } else {
                            onImageClick(
                                displayedImages,
                                displayedImages.indexOfFirst { it.id == image.id }
                            )
                        }
                    }
                )
            }
        }
    }

    val uris = pendingUris
    if (uris != null) {
        ImportTagDialog(
            tagRepository = tagRepository,
            onCancel = { pendingUris = null },
            onSkip = {
                pendingUris = null
                runImport(uris, emptyList())
            },
            onConfirm = { tagIds ->
                pendingUris = null
                runImport(uris, tagIds)
            }
        )
    }

    if (showBatchTagDialog) {
        BatchTagDialog(
            tagRepository = tagRepository,
            onDismiss = { showBatchTagDialog = false },
            onConfirm = { tagIds ->
                showBatchTagDialog = false
                val ids = selectedIds.toList()
                scope.launch {
                    for (tagId in tagIds) {
                        tagRepository.addTagToImages(ids, tagId)
                    }
                    message = "已为 ${ids.size} 张添加标签"
                    exitSelection()
                }
            }
        )
    }

    if (showBatchDeleteDialog) {
        BatchDeleteDialog(
            count = selectedIds.size,
            onDismiss = { showBatchDeleteDialog = false },
            onConfirm = {
                showBatchDeleteDialog = false
                val ids = selectedIds.toList()
                scope.launch {
                    repository.deleteAll(ids)
                    message = "已将 ${ids.size} 张移入回收站"
                    exitSelection()
                }
            }
        )
    }
}

@Composable
private fun AiMascotBanner(
    imageCount: Int,
    semanticMode: Boolean,
    onClick: () -> Unit
) {
    val dailyExamples = listOf(
        "海边的日落",
        "穿蓝色衣服的人",
        "雨天窗边的猫",
        "夜晚城市灯光",
        "樱花下的合照",
        "桌上的咖啡与书",
        "紫色天空和云"
    )
    val dayNumber = System.currentTimeMillis() / 86_400_000L
    val dailyExample = dailyExamples[
        Math.floorMod(dayNumber, dailyExamples.size.toLong()).toInt()
    ]
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(104.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp, end = 124.dp)
            ) {
                Text(
                    "今日图库 · $imageCount 张",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    if (semanticMode) {
                        "语义搜图已开启，再点一次回到文件名/标签搜索"
                    } else {
                        "今日搜索灵感：“$dailyExample” · 点击开启"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (semanticMode) {
                MascotExpression(
                    mood = MascotMood.Thinking,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(100.dp)
                        .padding(end = 4.dp)
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.empty_gallery_anime),
                    contentDescription = "今日图库助手",
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(120.dp)
                        .padding(end = 2.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun InlineMascotStatus(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MascotExpression(
            mood = MascotMood.Working,
            modifier = Modifier.size(54.dp)
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SelectionActionItem(
    label: String,
    icon: Int,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val activeColor = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val contentColor = if (enabled) {
        activeColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .height(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private val BrandBlue = Color(0xFF4D6BFE)

private fun filterImages(
    images: List<ImageEntity>,
    tagNamesByImage: Map<Long, Set<String>>,
    query: String
): List<ImageEntity> {
    val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return images
    val tagNames = tokens.filter { it.startsWith("#") }
        .map { it.removePrefix("#").lowercase() }
    val keywords = tokens.filter { !it.startsWith("#") }
    return images.filter { img ->
        val nameOk = keywords.all { kw -> img.fileName.contains(kw, ignoreCase = true) }
        val tagOk = tagNames.all { tag ->
            tagNamesByImage[img.id]?.any { it.lowercase() == tag } == true
        }
        nameOk && tagOk
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportTagDialog(
    tagRepository: TagRepository,
    onCancel: () -> Unit,
    onSkip: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    val tags by tagRepository.observeTags().collectAsState(initial = emptyList())
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var newTagName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("导入照片") },
        text = {
            Column {
                Text(
                    "选择要应用的标签（可多选），或直接导入不设标签",
                    style = MaterialTheme.typography.bodySmall
                )
                if (tags.isEmpty()) {
                    Text(
                        "暂无标签，可在下方创建",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        tags.forEach { tag ->
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
            TextButton(onClick = { onConfirm(selectedIds.toList()) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("直接导入") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchTagDialog(
    tagRepository: TagRepository,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    val tags by tagRepository.observeTags().collectAsState(initial = emptyList())
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var newTagName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量添加标签") },
        text = {
            Column {
                Text("选择要添加的标签（可多选）", style = MaterialTheme.typography.bodySmall)
                if (tags.isEmpty()) {
                    Text(
                        "暂无标签，可在下方创建",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        tags.forEach { tag ->
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
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun BatchDeleteDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移入回收站") },
        text = { Text("确定将选中的 $count 张图片移入回收站吗？可在「文件夹 → 回收站」中恢复。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("移入回收站", color = Color(0xFFE53935)) }
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
private fun ImageCell(image: ImageEntity, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = image.uri,
            contentDescription = image.fileName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
            )
        }
    }
}
