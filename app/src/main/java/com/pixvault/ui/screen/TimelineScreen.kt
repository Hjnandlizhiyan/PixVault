package com.pixvault.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixvault.R
import com.pixvault.data.db.entity.ImageEntity
import com.pixvault.data.repository.ImageRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    repository: ImageRepository,
    onImageClick: (List<ImageEntity>, Int) -> Unit
) {
    val images by repository.observeImages().collectAsState(initial = emptyList())
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    val filteredImages = remember(images, selectedDateMillis) {
        val selected = selectedDateMillis
        if (selected == null) images else images.filter { image ->
            sameCalendarDate(image.dateTaken ?: image.modifiedTime, selected)
        }
    }
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(150.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable { showDatePicker = true }
        ) {
            Image(
                painter = painterResource(R.drawable.timeline_scene_anime),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = 0.82f)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xDB171A3E), Color.Transparent)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(20.dp)
            ) {
                Text(
                    "时间线",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "按拍摄日期重温每个瞬间",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Text(
                    "点击选择日期",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        val selected = selectedDateMillis
        if (selected != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "已筛选：${formatSelectedDate(selected)} · ${filteredImages.size} 张",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { selectedDateMillis = null }) {
                    Text("清除")
                }
            }
        }
        if (images.isEmpty()) {
            MascotEmptyState(message = "导入照片后，回忆会自动排成时间线")
        } else if (filteredImages.isEmpty()) {
            MascotEmptyState(message = "这一天没有照片，点击上方卡片换个日期")
        } else {
            TimelineImageGrid(
                images = filteredImages,
                columns = 3,
                onImageClick = { image ->
                    onImageClick(
                        filteredImages,
                        filteredImages.indexOfFirst { it.id == image.id }
                    )
                }
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDateMillis = datePickerState.selectedDateMillis
                        showDatePicker = false
                    },
                    enabled = datePickerState.selectedDateMillis != null
                ) {
                    Text("查看这一天")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun sameCalendarDate(photoMillis: Long, selectedUtcMillis: Long): Boolean {
    val selected = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = selectedUtcMillis
    }
    val photo = Calendar.getInstance().apply { timeInMillis = photoMillis }
    return photo.get(Calendar.YEAR) == selected.get(Calendar.YEAR) &&
        photo.get(Calendar.MONTH) == selected.get(Calendar.MONTH) &&
        photo.get(Calendar.DAY_OF_MONTH) == selected.get(Calendar.DAY_OF_MONTH)
}

private fun formatSelectedDate(selectedUtcMillis: Long): String {
    val formatter = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return formatter.format(selectedUtcMillis)
}
