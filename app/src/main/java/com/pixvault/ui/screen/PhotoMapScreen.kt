package com.pixvault.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pixvault.data.repository.ImageRepository
import java.util.Locale

@Composable
fun PhotoMapScreen(repository: ImageRepository, onBack: () -> Unit) {
    val images by repository.observeLocatedImages().collectAsState(initial = emptyList())
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            BackButton(onClick = onBack)
            Column {
                Text("照片地图", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "读取照片 EXIF，不上传位置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (images.isEmpty()) {
            MascotEmptyState(message = "还没有带定位信息的照片")
        } else {
            LazyColumn {
                items(images, key = { it.id }) { image ->
                    val lat = image.latitude ?: return@items
                    val lon = image.longitude ?: return@items
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                            .clickable {
                                val geo = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, geo))
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        "未找到可用的地图应用",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = image.uri,
                                contentDescription = image.fileName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(92.dp).clip(RoundedCornerShape(18.dp))
                            )
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(image.fileName, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                Text(
                                    String.format(Locale.US, "%.5f, %.5f", lat, lon),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "在地图应用中查看",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
