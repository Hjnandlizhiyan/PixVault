package com.pixvault.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pixvault.data.db.entity.ImageEntity
import com.pixvault.data.repository.ImageRepository
import kotlinx.coroutines.launch

@Composable
fun TrashScreen(
    imageRepository: ImageRepository,
    onBack: () -> Unit
) {
    val images by imageRepository.observeTrash().collectAsState(initial = emptyList())
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showEmptyDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onClick = onBack)
            Text(
                "回收站",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text("${images.size} 张", style = MaterialTheme.typography.bodyMedium)
            if (images.isNotEmpty()) {
                TextButton(onClick = { showEmptyDialog = true }) {
                    Text("清空", color = Color(0xFFE53935))
                }
            }
        }

        if (images.isEmpty()) {
            MascotMoodState(
                mood = MascotMood.Sleepy,
                title = "暂时不用我工作啦",
                message = "回收站空空的，照片都被好好收藏着"
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp)
            ) {
                items(images, key = { it.id }) { image ->
                    TrashCell(
                        image = image,
                        selected = image.id in selectedIds,
                        onClick = {
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

        if (selectedIds.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val ids = selectedIds.toList()
                        scope.launch {
                            imageRepository.restoreAll(ids)
                            message = "已恢复 ${ids.size} 张"
                            selectedIds = emptySet()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("恢复")
                }
                Button(
                    onClick = {
                        val ids = selectedIds.toList()
                        scope.launch {
                            imageRepository.permanentlyDeleteAll(ids)
                            message = "已彻底删除 ${ids.size} 张"
                            selectedIds = emptySet()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("彻底删除", color = Color.White)
                }
            }
        }

        if (message.isNotEmpty()) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }

    if (showEmptyDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyDialog = false },
            title = { Text("清空回收站") },
            text = { Text("将彻底删除回收站中的 ${images.size} 张图片，此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEmptyDialog = false
                        scope.launch {
                            imageRepository.emptyTrash()
                            message = "回收站已清空"
                            selectedIds = emptySet()
                        }
                    }
                ) {
                    Text("清空", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TrashCell(image: ImageEntity, selected: Boolean, onClick: () -> Unit) {
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
