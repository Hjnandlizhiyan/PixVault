package com.pixvault.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
fun SimilarImagesScreen(
    sourceImage: ImageEntity,
    imageRepository: ImageRepository,
    onBack: () -> Unit,
    onImageClick: (List<ImageEntity>, Int) -> Unit
) {
    var results by remember { mutableStateOf<List<Pair<ImageEntity, Float>>?>(null) }

    LaunchedEffect(sourceImage.id) {
        results = imageRepository.findSimilar(sourceImage.id)
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
                "相似图片",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }

        val list = results
        when {
            list == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MascotLoading(modifier = Modifier.size(180.dp))
                }
            }
            list.isEmpty() -> {
                MascotEmptyState(message = "暂时没有相似图片，特征计算完成后再来看看")
            }
            else -> {
                val imageList = list.map { it.first }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    itemsIndexed(list, key = { _, it -> it.first.id }) { index, item ->
                        SimilarImageCell(
                            image = item.first,
                            similarity = item.second,
                            onClick = { onImageClick(imageList, index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimilarImageCell(image: ImageEntity, similarity: Float, onClick: () -> Unit) {
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
        Text(
            "%.0f%%".format(similarity * 100),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color(0x99000000), RoundedCornerShape(topStart = 4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
