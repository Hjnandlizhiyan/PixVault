package com.pixvault.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pixvault.R
import com.pixvault.data.db.entity.ImageEntity
import com.pixvault.data.repository.ImageRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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
        PhotoDateCalendarDialog(
            images = images,
            selectedDateMillis = selectedDateMillis,
            onDateSelected = {
                selectedDateMillis = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
private fun PhotoDateCalendarDialog(
    images: List<ImageEntity>,
    selectedDateMillis: Long?,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var visibleMonthMillis by rememberSaveable {
        mutableStateOf(monthAnchorMillis(selectedDateMillis))
    }
    val month = remember(visibleMonthMillis) {
        Calendar.getInstance().apply {
            timeInMillis = visibleMonthMillis
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val year = month.get(Calendar.YEAR)
    val monthIndex = month.get(Calendar.MONTH)
    val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOffset = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val photoCounts = remember(images) {
        images.groupingBy { image ->
            localDateKey(image.dateTaken ?: image.modifiedTime)
        }.eachCount()
    }
    val selectedKey = selectedDateMillis?.let(::utcDateKey)
    val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("按日期查找") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = { visibleMonthMillis = shiftMonth(visibleMonthMillis, -1) }
                    ) {
                        Text("‹ 上月")
                    }
                    Text(
                        "${year}年${monthIndex + 1}月",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = { visibleMonthMillis = shiftMonth(visibleMonthMillis, 1) }
                    ) {
                        Text("下月 ›")
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekLabels.forEach { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                repeat(6) { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        repeat(7) { weekday ->
                            val cellIndex = week * 7 + weekday
                            val day = cellIndex - firstDayOffset + 1
                            if (day in 1..daysInMonth) {
                                val key = dateKey(year, monthIndex, day)
                                val count = photoCounts[key] ?: 0
                                val isSelected = key == selectedKey
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clickable {
                                            onDateSelected(toUtcDateMillis(year, monthIndex, day))
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            day.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            fontWeight = if (count > 0) {
                                                FontWeight.SemiBold
                                            } else {
                                                FontWeight.Normal
                                            }
                                        )
                                        if (count > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 2.dp)
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            } else {
                                Spacer(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        "  有照片的日期",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
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

private fun monthAnchorMillis(selectedUtcMillis: Long?): Long {
    val local = Calendar.getInstance()
    if (selectedUtcMillis != null) {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = selectedUtcMillis
        }
        local.set(Calendar.YEAR, utc.get(Calendar.YEAR))
        local.set(Calendar.MONTH, utc.get(Calendar.MONTH))
    }
    local.set(Calendar.DAY_OF_MONTH, 1)
    local.set(Calendar.HOUR_OF_DAY, 12)
    local.set(Calendar.MINUTE, 0)
    local.set(Calendar.SECOND, 0)
    local.set(Calendar.MILLISECOND, 0)
    return local.timeInMillis
}

private fun shiftMonth(monthMillis: Long, amount: Int): Long {
    return Calendar.getInstance().apply {
        timeInMillis = monthMillis
        add(Calendar.MONTH, amount)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 12)
    }.timeInMillis
}

private fun localDateKey(millis: Long): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    return dateKey(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
}

private fun utcDateKey(millis: Long): Int {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = millis
    }
    return dateKey(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
}

private fun dateKey(year: Int, monthIndex: Int, day: Int): Int {
    return year * 10_000 + (monthIndex + 1) * 100 + day
}

private fun toUtcDateMillis(year: Int, monthIndex: Int, day: Int): Long {
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year, monthIndex, day, 0, 0, 0)
    }.timeInMillis
}

private fun formatSelectedDate(selectedUtcMillis: Long): String {
    val formatter = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return formatter.format(selectedUtcMillis)
}
