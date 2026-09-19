package com.pixvault.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pixvault.data.db.entity.ImageEntity
import com.pixvault.data.repository.ImageRepository
import kotlinx.coroutines.launch

@Composable
fun SimilarCleanupScreen(repository: ImageRepository, onBack: () -> Unit) {
    var groups by remember { mutableStateOf<List<List<Pair<ImageEntity, Float>>>?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { groups = repository.findSimilarGroups() }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            BackButton(onClick = onBack)
            Column(modifier = Modifier.weight(1f)) {
                Text("相似照片整理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("默认保留每组第一张，可点选准备清理", style = MaterialTheme.typography.bodySmall)
            }
            Button(
                enabled = selectedIds.isNotEmpty(),
                onClick = {
                    scope.launch {
                        repository.deleteAll(selectedIds.toList())
                        groups = repository.findSimilarGroups()
                        selectedIds = emptySet()
                    }
                }
            ) { Text("清理 ${selectedIds.size}") }
        }
        when (val value = groups) {
            null -> MascotLoading(modifier = Modifier.fillMaxWidth())
            emptyList<List<Pair<ImageEntity, Float>>>() ->
                MascotEmptyState(message = "没有发现需要整理的相似照片")
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(value.size) { groupIndex ->
                    val group = value[groupIndex]
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "相似组 ${groupIndex + 1} · ${group.size} 张",
                                fontWeight = FontWeight.SemiBold
                            )
                            LazyRow(modifier = Modifier.padding(top = 8.dp)) {
                                items(group, key = { it.first.id }) { (image, score) ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .clickable {
                                                if (image == group.first().first) return@clickable
                                                selectedIds = if (image.id in selectedIds) {
                                                    selectedIds - image.id
                                                } else {
                                                    selectedIds + image.id
                                                }
                                            }
                                    ) {
                                        AsyncImage(
                                            model = image.uri,
                                            contentDescription = image.fileName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(104.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                        Text(
                                            if (image == group.first().first) "建议保留" else
                                                if (image.id in selectedIds) "已选择" else "${(score * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (image.id in selectedIds) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
