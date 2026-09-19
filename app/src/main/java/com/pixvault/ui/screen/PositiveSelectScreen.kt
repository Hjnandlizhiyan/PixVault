package com.pixvault.ui.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pixvault.data.db.entity.ImageEntity
import com.pixvault.data.db.entity.TagWithCount
import com.pixvault.data.repository.ImageRepository
import com.pixvault.data.repository.TagRepository
import kotlinx.coroutines.launch

@Composable
fun PositiveSelectScreen(
    tag: TagWithCount,
    imageRepository: ImageRepository,
    tagRepository: TagRepository,
    onBack: () -> Unit
) {
    val images by imageRepository.observeImages().collectAsState(initial = emptyList())
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var threshold by remember { mutableStateOf(tag.threshold) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tag.id) {
        selectedIds = tagRepository.getPositiveImageIds(tag.id).toSet()
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onClick = onBack)
            Text(
                "正例 · ${tag.name}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text("已选 ${selectedIds.size} 张", style = MaterialTheme.typography.bodyMedium)
        }

        if (message.isNotEmpty()) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (images.isEmpty()) {
            MascotEmptyState(message = "还没有可选图片，请先回到首页导入")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp)
            ) {
                items(images, key = { it.id }) { image ->
                    SelectableImageCell(
                        image = image,
                        selected = image.id in selectedIds,
                        onToggle = {
                            selectedIds = if (image.id in selectedIds) {
                                selectedIds - image.id
                            } else {
                                selectedIds + image.id
                            }
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("阈值", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = threshold,
                onValueChange = { threshold = it },
                valueRange = 0.1f..0.95f,
                modifier = Modifier.weight(1f)
            )
            Text(
                "%.2f".format(threshold),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Button(
            onClick = {
                scope.launch {
                    working = true
                    val posIds = selectedIds.toList()
                    val withEmbedding = tagRepository.countWithEmbedding(posIds)
                    if (withEmbedding == 0) {
                        message = "正例图片还没有特征向量，请回到首页等待导入完成后再试"
                        working = false
                        return@launch
                    }
                    message = "保存正例..."
                    tagRepository.updateThreshold(tag.id, threshold)
                    tagRepository.setPositiveExamples(tag.id, posIds)
                    message = "计算原型..."
                    tagRepository.computePrototype(tag.id)
                    message = "自动匹配..."
                    val count = tagRepository.autoMatch(tag.id)
                    message = "完成：命中 $count 张（阈值 ${"%.2f".format(threshold)}）"
                    working = false
                }
            },
            enabled = !working && selectedIds.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(if (working) "处理中..." else "保存并匹配")
        }
    }
}

@Composable
private fun SelectableImageCell(image: ImageEntity, selected: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onToggle)
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
