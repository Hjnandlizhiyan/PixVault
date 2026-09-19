package com.pixvault.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pixvault.R

private data class OnboardingPage(
    val eyebrow: String,
    val title: String,
    val description: String
)

private val onboardingPages = listOf(
    OnboardingPage(
        eyebrow = "欢迎使用 PixVault",
        title = "把喜欢的瞬间安心收好",
        description = "导入照片后，PixVault 会建立一份应用内副本，方便你独立整理、编辑和查找。"
    ),
    OnboardingPage(
        eyebrow = "隐私优先",
        title = "AI 整理全程在本机完成",
        description = "语义搜索、相似图片和标签学习都在设备上运行，不需要把照片上传到云端。"
    ),
    OnboardingPage(
        eyebrow = "开始整理",
        title = "用描述、标签和收藏快速找到它",
        description = "你可以搜索画面内容、建立标签文件夹，也可以在回收站中恢复误删的照片。"
    )
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val page = onboardingPages[pageIndex]
    BackHandler(onBack = onComplete)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onComplete) {
                Text("跳过")
            }
        }

        Spacer(Modifier.weight(0.35f))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (pageIndex == 0) {
                    MascotExpression(
                        mood = MascotMood.Welcome,
                        modifier = Modifier.size(220.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.onboarding_local_ai),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            page.eyebrow,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            onboardingPages.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == pageIndex) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pageIndex) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                if (pageIndex == onboardingPages.lastIndex) {
                    onComplete()
                } else {
                    pageIndex++
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(if (pageIndex == onboardingPages.lastIndex) "开始使用" else "下一步")
        }
    }
}
