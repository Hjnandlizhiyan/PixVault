package com.pixvault

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.core.content.edit
import com.pixvault.data.db.entity.ImageEntity
import com.pixvault.data.db.entity.TagWithCount
import com.pixvault.data.importer.ImageImporter
import com.pixvault.data.repository.ImageRepository
import com.pixvault.data.repository.TagRepository
import com.pixvault.data.storage.StoredImageFileManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.pixvault.ui.screen.DetailScreen
import com.pixvault.ui.screen.FavoritesScreen
import com.pixvault.ui.screen.FolderImagesScreen
import com.pixvault.ui.screen.FolderListScreen
import com.pixvault.ui.screen.GroupBuyScreen
import com.pixvault.ui.screen.HomeScreen
import com.pixvault.ui.screen.ImageEditorScreen
import com.pixvault.ui.screen.ImageViewerScreen
import com.pixvault.ui.screen.OnboardingScreen
import com.pixvault.ui.screen.PhotoMapScreen
import com.pixvault.ui.screen.PositiveSelectScreen
import com.pixvault.ui.screen.PrivateSpaceScreen
import com.pixvault.ui.screen.SettingsScreen
import com.pixvault.ui.screen.SimilarCleanupScreen
import com.pixvault.ui.screen.SimilarImagesScreen
import com.pixvault.ui.screen.TagManagerScreen
import com.pixvault.ui.screen.TimelineScreen
import com.pixvault.ui.screen.TrashScreen
import com.pixvault.ui.screen.UntaggedImagesScreen
import com.pixvault.ui.theme.PixVaultTheme
import com.pixvault.ui.theme.ThemeMode

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        var themeMode by mutableStateOf(ThemeMode.fromPref(prefs.getString("themeMode", null)))
        var showOnboarding by mutableStateOf(!prefs.getBoolean("onboardingCompleted", false))
        setContent {
            val darkTheme = when (themeMode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> isSystemInDarkTheme()
            }
            PixVaultTheme(darkTheme = darkTheme) {
                Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    PixVaultRoot(
                        themeMode = themeMode,
                        onSetThemeMode = { mode ->
                            themeMode = mode
                            prefs.edit { putString("themeMode", mode.prefValue) }
                        },
                        onOpenOnboarding = { showOnboarding = true }
                    )
                    if (showOnboarding) {
                        OnboardingScreen(
                            onComplete = {
                                prefs.edit { putBoolean("onboardingCompleted", true) }
                                showOnboarding = false
                            }
                        )
                    }
                }
            }
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Timeline : Screen
    data object Private : Screen
    data class Detail(val images: List<ImageEntity>, val index: Int) : Screen
    data class Viewer(val images: List<ImageEntity>, val index: Int) : Screen
    data class Editor(val images: List<ImageEntity>, val index: Int) : Screen
    data object TagManager : Screen
    data class PositiveSelect(val tag: TagWithCount) : Screen
    data class Similar(val image: ImageEntity) : Screen
    data object FolderList : Screen
    data class FolderImages(val tag: TagWithCount) : Screen
    data object Favorites : Screen
    data object Untagged : Screen
    data object Trash : Screen
    data object Settings : Screen
    data object PhotoMap : Screen
    data object SimilarCleanup : Screen
    data object GroupBuy : Screen
}

@Composable
fun PixVaultRoot(
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    onOpenOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as PixVaultApp
    val repository = remember {
        ImageRepository(
            app.database,
            StoredImageFileManager(File(context.filesDir, "images"))
        )
    }
    val tagRepository = remember { TagRepository(app.database) }
    val importer = remember { ImageImporter(context) }

    LaunchedEffect(repository) {
        withContext(Dispatchers.IO) { repository.backfillPhotoMetadata() }
    }

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var privateUnlocked by remember { mutableStateOf(false) }

    Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
        Box(modifier = androidx.compose.ui.Modifier.weight(1f)) {
    when (val s = screen) {
        Screen.Home -> HomeScreen(
            repository = repository,
            importer = importer,
            embeddingService = app.embeddingService,
            tagRepository = tagRepository,
            onImageClick = { images, index -> screen = Screen.Detail(images, index) },
            onManageTags = { screen = Screen.TagManager },
            onOpenFolders = { screen = Screen.FolderList }
        )
        Screen.Timeline -> TimelineScreen(
            repository = repository,
            onImageClick = { images, index -> screen = Screen.Detail(images, index) }
        )
        Screen.Private -> PrivateSpaceScreen(
            repository = repository,
            unlocked = privateUnlocked,
            onUnlocked = { privateUnlocked = true }
        )
        is Screen.Detail -> DetailScreen(
            image = s.images[s.index],
            repository = repository,
            tagRepository = tagRepository,
            embeddingService = app.embeddingService,
            imageProcessor = app.imageProcessor,
            onBack = { screen = Screen.Home },
            onDeleted = { screen = Screen.Home },
            onFindSimilar = { screen = Screen.Similar(s.images[s.index]) },
            onOpenViewer = { screen = Screen.Viewer(s.images, s.index) },
            canGoPrev = s.index > 0,
            canGoNext = s.index < s.images.lastIndex,
            onPrev = { screen = s.copy(index = s.index - 1) },
            onNext = { screen = s.copy(index = s.index + 1) },
            onOpenEditor = { screen = Screen.Editor(s.images, s.index) }
        )
        is Screen.Viewer -> ImageViewerScreen(
            image = s.images[s.index],
            onBack = { screen = Screen.Detail(s.images, s.index) }
        )
        is Screen.Editor -> ImageEditorScreen(
            image = s.images[s.index],
            imageProcessor = app.imageProcessor,
            repository = repository,
            onCancel = { screen = Screen.Detail(s.images, s.index) },
            onSaved = { screen = Screen.Home }
        )
        Screen.TagManager -> TagManagerScreen(
            tagRepository = tagRepository,
            onBack = { screen = Screen.Home },
            onManagePositive = { tag -> screen = Screen.PositiveSelect(tag) }
        )
        is Screen.PositiveSelect -> PositiveSelectScreen(
            tag = s.tag,
            imageRepository = repository,
            tagRepository = tagRepository,
            onBack = { screen = Screen.TagManager }
        )
        is Screen.Similar -> SimilarImagesScreen(
            sourceImage = s.image,
            imageRepository = repository,
            onBack = { screen = Screen.Detail(listOf(s.image), 0) },
            onImageClick = { images, index -> screen = Screen.Detail(images, index) }
        )
        Screen.FolderList -> FolderListScreen(
            tagRepository = tagRepository,
            imageRepository = repository,
            onBack = { screen = Screen.Home },
            onOpenFolder = { tag -> screen = Screen.FolderImages(tag) },
            onOpenFavorites = { screen = Screen.Favorites },
            onOpenUntagged = { screen = Screen.Untagged },
            onOpenTrash = { screen = Screen.Trash }
        )
        is Screen.FolderImages -> FolderImagesScreen(
            tag = s.tag,
            imageRepository = repository,
            onBack = { screen = Screen.FolderList },
            onImageClick = { images, index -> screen = Screen.Detail(images, index) }
        )
        Screen.Favorites -> FavoritesScreen(
            imageRepository = repository,
            onBack = { screen = Screen.FolderList },
            onImageClick = { images, index -> screen = Screen.Detail(images, index) }
        )
        Screen.Untagged -> UntaggedImagesScreen(
            imageRepository = repository,
            onBack = { screen = Screen.FolderList },
            onImageClick = { images, index -> screen = Screen.Detail(images, index) }
        )
        Screen.Trash -> TrashScreen(
            imageRepository = repository,
            onBack = { screen = Screen.FolderList }
        )
        Screen.Settings -> SettingsScreen(
            themeMode = themeMode,
            onSetThemeMode = onSetThemeMode,
            onOpenOnboarding = onOpenOnboarding,
            onOpenPrivate = { screen = Screen.Private },
            onOpenPhotoMap = { screen = Screen.PhotoMap },
            onOpenSimilarCleanup = { screen = Screen.SimilarCleanup },
            onOpenTrash = { screen = Screen.Trash },
            onOpenGroupBuy = { screen = Screen.GroupBuy }
        )
        Screen.PhotoMap -> PhotoMapScreen(
            repository = repository,
            onBack = { screen = Screen.Settings }
        )
        Screen.SimilarCleanup -> SimilarCleanupScreen(
            repository = repository,
            onBack = { screen = Screen.Settings }
        )
        Screen.GroupBuy -> GroupBuyScreen(
            onBack = { screen = Screen.Settings }
        )
    }
        }
        if (screen == Screen.Home ||
            screen == Screen.Timeline ||
            screen == Screen.Private ||
            screen == Screen.Settings
        ) {
            RootNavigationBar(
                current = screen,
                onSelect = { screen = it }
            )
        }
    }
}

@Composable
private fun RootNavigationBar(current: Screen, onSelect: (Screen) -> Unit) {
    val items = listOf(
        Triple(Screen.Home, R.drawable.ic_nav_gallery, "图库"),
        Triple(Screen.Timeline, R.drawable.ic_nav_timeline, "时间线"),
        Triple(Screen.Private, R.drawable.ic_nav_private, "私密"),
        Triple(Screen.Settings, R.drawable.ic_nav_settings, "设置")
    )
    NavigationBar {
        items.forEach { (destination, icon, label) ->
            val selected = current == destination
            val iconScale by animateFloatAsState(
                targetValue = if (selected) 1.12f else 1f,
                label = "navIcon"
            )
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        modifier = androidx.compose.ui.Modifier.scale(iconScale)
                    )
                },
                label = { Text(label) },
                alwaysShowLabel = true
            )
        }
    }
}
