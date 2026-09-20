package com.pixvault.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pixvault.data.db.entity.TagWithCount
import com.pixvault.data.repository.ImageRepository
import com.pixvault.data.repository.TagRepository
import kotlinx.coroutines.launch

@Composable
fun FolderListScreen(
    tagRepository: TagRepository,
    imageRepository: ImageRepository,
    onBack: () -> Unit,
    onOpenFolder: (TagWithCount) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenUntagged: () -> Unit,
    onOpenTrash: () -> Unit
) {
    val tags by tagRepository.observeTagsWithCount().collectAsState(initial = emptyList())
    val favorites by imageRepository.observeFavorites().collectAsState(initial = emptyList())
    val untagged by imageRepository.observeUntaggedImages().collectAsState(initial = emptyList())
    val trashed by imageRepository.observeTrash().collectAsState(initial = emptyList())
    var pendingDelete by remember { mutableStateOf<TagWithCount?>(null) }
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
                "文件夹",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "favorites") {
                NavRow(
                    icon = "★",
                    label = "收藏",
                    count = favorites.size,
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onOpenFavorites
                )
                HorizontalDivider()
            }
            item(key = "untagged") {
                NavRow(
                    icon = "◇",
                    label = "未分类",
                    count = untagged.size,
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = onOpenUntagged
                )
                HorizontalDivider()
            }
            item(key = "trash") {
                NavRow(
                    icon = "♻",
                    label = "回收站",
                    count = trashed.size,
                    tint = Color(0xFFE53935),
                    onClick = onOpenTrash
                )
                HorizontalDivider()
            }
            if (tags.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "暂无标签文件夹，请先在「标签」中创建标签",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(tags, key = { it.id }) { tag ->
                    FolderRow(
                        tag = tag,
                        onClick = { onOpenFolder(tag) },
                        onDelete = { pendingDelete = tag }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除文件夹") },
            text = {
                Text("确定删除文件夹「${target.name}」吗？文件夹内的图片不会被删除，仅移除该标签。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = target.id
                        pendingDelete = null
                        scope.launch { tagRepository.deleteTag(id) }
                    }
                ) {
                    Text("删除", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun NavRow(
    icon: String,
    label: String,
    count: Int,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = tint.copy(alpha = 0.15f)
        ) {
            Text(
                icon,
                style = MaterialTheme.typography.titleMedium,
                color = tint,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        )
        Text(
            "${count} 张",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FolderRow(tag: TagWithCount, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        ) {
            Text(
                tag.name.firstOrNull()?.toString() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        Text(
            tag.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        )
        Text(
            "${tag.count} 张",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onDelete) {
            Text("删除", color = Color(0xFFE53935))
        }
    }
}
