package com.pixvault.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pixvault.data.db.entity.ImageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class TimelineMonth(
    val key: String,
    val title: String,
    val days: List<Pair<String, List<ImageEntity>>>
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineImageGrid(
    images: List<ImageEntity>,
    columns: Int,
    selectedIds: Set<Long> = emptySet(),
    onImageClick: (ImageEntity) -> Unit,
    contentPadding: PaddingValues = PaddingValues(bottom = 12.dp)
) {
    val months = timelineMonths(images)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        months.forEach { month ->
            stickyHeader(key = "month-${month.key}") {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        month.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
            month.days.forEach { (dayTitle, dayImages) ->
                item(key = "day-${month.key}-$dayTitle") {
                    Text(
                        dayTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp)
                    )
                }
                items(
                    items = dayImages.chunked(columns),
                    key = { row -> row.joinToString("-") { it.id.toString() } }
                ) { rowImages ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        rowImages.forEach { image ->
                            TimelineCell(
                                image = image,
                                selected = image.id in selectedIds,
                                onClick = { onImageClick(image) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(columns - rowImages.size) {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineCell(
    image: ImageEntity,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .padding(bottom = 4.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
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
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            )
        }
    }
}

private fun timelineMonths(images: List<ImageEntity>): List<TimelineMonth> {
    val locale = Locale.SIMPLIFIED_CHINESE
    val monthKey = SimpleDateFormat("yyyy-MM", locale)
    val monthTitle = SimpleDateFormat("yyyy年 M月", locale)
    val dayTitle = SimpleDateFormat("M月d日 EEEE", locale)
    return images
        .groupBy { monthKey.format(Date(it.dateTaken ?: it.modifiedTime)) }
        .map { (key, monthImages) ->
            val representative = Date(monthImages.first().dateTaken ?: monthImages.first().modifiedTime)
            TimelineMonth(
                key = key,
                title = monthTitle.format(representative),
                days = monthImages.groupBy {
                    dayTitle.format(Date(it.dateTaken ?: it.modifiedTime))
                }.toList()
            )
        }
}
