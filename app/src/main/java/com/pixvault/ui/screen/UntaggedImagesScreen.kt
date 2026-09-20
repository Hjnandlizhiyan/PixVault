package com.pixvault.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pixvault.data.db.entity.ImageEntity
import com.pixvault.data.repository.ImageRepository

@Composable
fun UntaggedImagesScreen(
    imageRepository: ImageRepository,
    onBack: () -> Unit,
    onImageClick: (List<ImageEntity>, Int) -> Unit
) {
    val images by imageRepository.observeUntaggedImages().collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onClick = onBack)
            Column(modifier = Modifier.weight(1f)) {
                Text("未分类", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${images.size} 张没有标签的图片",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (images.isEmpty()) {
            MascotEmptyState(message = "所有图片都已整理好，没有未分类图片")
        } else {
            TimelineImageGrid(
                images = images,
                columns = 3,
                onImageClick = { image ->
                    onImageClick(images, images.indexOfFirst { it.id == image.id })
                }
            )
        }
    }
}
