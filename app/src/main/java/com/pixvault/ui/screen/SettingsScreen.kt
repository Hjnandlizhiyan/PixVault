package com.pixvault.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pixvault.R
import com.pixvault.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenPrivate: () -> Unit,
    onOpenPhotoMap: () -> Unit,
    onOpenSimilarCleanup: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenGroupBuy: () -> Unit
) {
    var showQr by remember { mutableStateOf(false) }
    var showMoods by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "设置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "PixVault",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        SettingsGroup(
            title = "外观",
            icon = R.drawable.ic_nav_settings
        ) {
            ThemeMode.values().forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSetThemeMode(mode) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(mode.label(), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            mode.description(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = { onSetThemeMode(mode) }
                    )
                }
            }
        }

        SettingsGroup("隐私", R.drawable.ic_nav_private) {
            SettingActionRow(
                title = "私密空间",
                subtitle = "系统验证、图库隐藏与防截图",
                onClick = onOpenPrivate
            )
            SettingActionRow(
                title = "本地处理说明",
                subtitle = "照片与 AI 特征默认只保存在设备内",
                onClick = onOpenOnboarding
            )
        }

        SettingsGroup("AI", R.drawable.ic_section_ai) {
            SettingActionRow(
                title = "相似照片整理",
                subtitle = "按本地图片特征发现相似组",
                onClick = onOpenSimilarCleanup
            )
            SettingActionRow(
                title = "搜索提示",
                subtitle = "首页助手每天轮换一条语义搜索示例",
                onClick = onOpenOnboarding
            )
        }

        SettingsGroup("存储", R.drawable.ic_section_map) {
            SettingActionRow(
                title = "照片地图",
                subtitle = "查看带 GPS 信息的照片并跳转地图",
                onClick = onOpenPhotoMap
            )
            SettingActionRow(
                title = "回收站",
                subtitle = "恢复或彻底删除已移除照片",
                onClick = onOpenTrash
            )
        }

        SettingsGroup("帮助", R.drawable.ic_nav_gallery) {
            SettingActionRow(
                title = "重新查看新手指引",
                subtitle = "导入、整理、本地 AI 与隐私说明",
                onClick = onOpenOnboarding
            )
            SettingActionRow(
                title = "角色表情套装",
                subtitle = "点击查看六种表情和对应使用场景",
                onClick = { showMoods = true }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showMoods = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MascotExpression(MascotMood.Welcome, Modifier.weight(1f))
                MascotExpression(MascotMood.Thinking, Modifier.weight(1f))
                MascotExpression(MascotMood.Private, Modifier.weight(1f))
            }
            SettingActionRow(
                title = "反馈与社群",
                subtitle = "QQ群 305402575 · 点此显示二维码",
                onClick = { showQr = true }
            )
            SettingActionRow(
                title = "玩偶团购",
                subtitle = "查看角色周边信息",
                onClick = onOpenGroupBuy
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(24.dp))
        ) {
            Image(
                painter = painterResource(R.drawable.onboarding_local_ai),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Text(
                "照片留在本机，回忆只属于你",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xB325233A))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }

    if (showQr) {
        Dialog(onDismissRequest = { showQr = false }) {
            Surface(shape = RoundedCornerShape(22.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("反馈与社群", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "QQ群 305402575",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_qq_group),
                        contentDescription = "官方QQ群二维码",
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .size(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showQr = false }
                    )
                }
            }
        }
    }
    if (showMoods) {
        ExpressionGalleryDialog(onDismiss = { showMoods = false })
    }
}

@Composable
private fun ExpressionGalleryDialog(onDismiss: () -> Unit) {
    val moods = listOf(
        MascotMood.Welcome to "欢迎",
        MascotMood.Thinking to "搜索",
        MascotMood.Working to "加载",
        MascotMood.Private to "守护",
        MascotMood.Surprised to "发现",
        MascotMood.Sleepy to "休息"
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "PixVault 角色表情",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "这些表情会分别出现在搜索、加载、私密空间和整理状态中。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                moods.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { (mood, label) ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = 8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    MascotExpression(
                                        mood = mood,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    "点击外部区域关闭",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    icon: Int,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.System -> "跟随系统"
    ThemeMode.Light -> "浅色"
    ThemeMode.Dark -> "深色"
}

private fun ThemeMode.description(): String = when (this) {
    ThemeMode.System -> "根据系统设置自动切换"
    ThemeMode.Light -> "明亮的薰衣草白与蓝紫强调色"
    ThemeMode.Dark -> "专门调校的靛蓝与柔紫夜间配色"
}
