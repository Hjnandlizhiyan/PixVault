package com.pixvault.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.pixvault.data.repository.TagRepository
import kotlinx.coroutines.launch

@Composable
fun TagManagerScreen(
    tagRepository: TagRepository,
    onBack: () -> Unit,
    onManagePositive: (TagWithCount) -> Unit
) {
    val tags by tagRepository.observeTagsWithCount().collectAsState(initial = emptyList())
    var newTagName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<TagWithCount?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                "标签管理",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newTagName,
                onValueChange = { newTagName = it },
                singleLine = true,
                label = { Text("新标签名") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val name = newTagName.trim()
                    if (name.isNotEmpty()) {
                        scope.launch { tagRepository.createTag(name) }
                        newTagName = ""
                    }
                },
                enabled = newTagName.trim().isNotEmpty(),
                modifier = Modifier.padding(start = 8.dp)
            ) { Text("创建") }
        }

        if (tags.isEmpty()) {
            Text(
                "暂无标签，在上方输入名称创建",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(tags, key = { it.id }) { tag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tag.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${tag.count} 张图片",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onManagePositive(tag) }) { Text("正例") }
                        TextButton(onClick = { pendingDelete = tag }) {
                            Text("删除", color = Color(0xFFE53935))
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    pendingDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除标签") },
            text = { Text("确定删除标签「${tag.name}」吗？将同时解除与 ${tag.count} 张图片的关联。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { tagRepository.deleteTag(tag.id) }
                    pendingDelete = null
                }) { Text("删除", color = Color(0xFFE53935)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}
