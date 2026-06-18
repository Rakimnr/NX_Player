package com.nextgen.nxplayer.ui.screens.library

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nextgen.nxplayer.data.model.VideoItem
import com.nextgen.nxplayer.data.repository.VideoSortType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PULL_REFRESH_TRIGGER_DISTANCE = 180f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onVideoClick: (VideoItem, List<VideoItem>) -> Unit,
    onSettingsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val context = LocalContext.current
    val videos by viewModel.videos.collectAsState()
    val totalVideoCount by viewModel.totalVideoCount.collectAsState()
    val permissionGranted by viewModel.permissionGranted.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showSortMenu by remember { mutableStateOf(false) }
    var infoVideo by remember { mutableStateOf<VideoItem?>(null) }
    var deleteCandidate by remember { mutableStateOf<VideoItem?>(null) }

    LaunchedEffect(errorMessage, videos.isNotEmpty()) {
        val message = errorMessage
        if (!message.isNullOrBlank() && videos.isNotEmpty()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionResult(granted)
    }

    val singleVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val pickedVideo = createPickedVideoItem(context, it)
            onVideoClick(pickedVideo, listOf(pickedVideo))
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onSystemDeleteCompleted(result.resultCode == Activity.RESULT_OK)
    }

    fun requestVideoLibraryPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permission)
    }

    fun openSingleVideoWithoutLibraryPermission() {
        singleVideoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("NX Player") },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Sort,
                                contentDescription = "Sort"
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortMenuItem(
                                label = "Latest first",
                                selected = sortType == VideoSortType.LATEST,
                                onClick = {
                                    viewModel.changeSortType(VideoSortType.LATEST)
                                    showSortMenu = false
                                }
                            )
                            SortMenuItem(
                                label = "Name A-Z",
                                selected = sortType == VideoSortType.NAME,
                                onClick = {
                                    viewModel.changeSortType(VideoSortType.NAME)
                                    showSortMenu = false
                                }
                            )
                            SortMenuItem(
                                label = "Largest size",
                                selected = sortType == VideoSortType.SIZE,
                                onClick = {
                                    viewModel.changeSortType(VideoSortType.SIZE)
                                    showSortMenu = false
                                }
                            )
                            SortMenuItem(
                                label = "Longest duration",
                                selected = sortType == VideoSortType.DURATION,
                                onClick = {
                                    viewModel.changeSortType(VideoSortType.DURATION)
                                    showSortMenu = false
                                }
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.refreshVideos() }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh"
                        )
                    }

                    IconButton(onClick = onPrivacyClick) {
                        Icon(
                            imageVector = Icons.Rounded.PrivacyTip,
                            contentDescription = "Privacy"
                        )
                    }

                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            !permissionGranted -> {
                PermissionRequiredScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    onGrantPermission = { requestVideoLibraryPermission() },
                    onPickSingleVideo = { openSingleVideoWithoutLibraryPermission() },
                    onOpenAppSettings = { openAppSettings(context) }
                )
            }

            isLoading && videos.isEmpty() && errorMessage.isNullOrBlank() -> {
                LoadingSkeletonGrid(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            !errorMessage.isNullOrBlank() && videos.isEmpty() -> {
                ErrorLibraryScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    message = errorMessage.orEmpty(),
                    onRetry = { viewModel.refreshVideos() },
                    onPickSingleVideo = { openSingleVideoWithoutLibraryPermission() }
                )
            }

            else -> {
                LibraryContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    videos = videos,
                    totalVideoCount = totalVideoCount,
                    searchQuery = searchQuery,
                    isLoading = isLoading,
                    onSearchChange = viewModel::updateSearchQuery,
                    onRefresh = viewModel::refreshVideos,
                    onVideoClick = { video -> onVideoClick(video, videos) },
                    onVideoInfo = { infoVideo = it },
                    onShareVideo = { shareVideo(context, it) },
                    onDeleteVideo = { deleteCandidate = it },
                    onPickSingleVideo = { openSingleVideoWithoutLibraryPermission() }
                )
            }
        }
    }

    infoVideo?.let { video ->
        FileInfoBottomSheet(
            video = video,
            onDismiss = { infoVideo = null },
            onPlay = {
                infoVideo = null
                onVideoClick(video, videos)
            },
            onShare = { shareVideo(context, video) }
        )
    }

    deleteCandidate?.let { video ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete video?") },
            text = {
                Text(
                    text = "This will remove \"${video.name}\" from your device. This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCandidate = null
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val pendingIntent = MediaStore.createDeleteRequest(
                                    context.contentResolver,
                                    listOf(video.uri)
                                )
                                val request = IntentSenderRequest.Builder(
                                    pendingIntent.intentSender
                                ).build()
                                deleteLauncher.launch(request)
                            } catch (e: Exception) {
                                viewModel.showError(e.message ?: "Could not open delete confirmation.")
                            }
                        } else {
                            viewModel.deleteVideo(video)
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SortMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(if (selected) "✓ $label" else label) },
        onClick = onClick,
        enabled = !selected
    )
}

@Composable
private fun LibraryContent(
    modifier: Modifier = Modifier,
    videos: List<VideoItem>,
    totalVideoCount: Int,
    searchQuery: String,
    isLoading: Boolean,
    onSearchChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onVideoInfo: (VideoItem) -> Unit,
    onShareVideo: (VideoItem) -> Unit,
    onDeleteVideo: (VideoItem) -> Unit,
    onPickSingleVideo: () -> Unit
) {
    Column(modifier = modifier) {
        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )

        when {
            videos.isEmpty() && searchQuery.isNotBlank() -> {
                EmptySearchScreen(
                    modifier = Modifier.fillMaxSize(),
                    query = searchQuery,
                    onClearSearch = { onSearchChange("") }
                )
            }

            videos.isEmpty() && totalVideoCount == 0 -> {
                PullRefreshArea(
                    modifier = Modifier.fillMaxSize(),
                    enabled = !isLoading,
                    isRefreshing = isLoading,
                    onRefresh = onRefresh
                ) {
                    EmptyLibraryScreen(
                        modifier = Modifier.fillMaxSize(),
                        onRefresh = onRefresh,
                        onPickSingleVideo = onPickSingleVideo
                    )
                }
            }

            else -> {
                VideoGrid(
                    videos = videos,
                    isLoading = isLoading,
                    onRefresh = onRefresh,
                    onVideoClick = onVideoClick,
                    onVideoInfo = onVideoInfo,
                    onShareVideo = onShareVideo,
                    onDeleteVideo = onDeleteVideo
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear search"
                    )
                }
            }
        },
        placeholder = { Text("Search videos") }
    )
}

@Composable
private fun VideoGrid(
    videos: List<VideoItem>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onVideoInfo: (VideoItem) -> Unit,
    onShareVideo: (VideoItem) -> Unit,
    onDeleteVideo: (VideoItem) -> Unit
) {
    val gridState = rememberLazyGridState()
    val canPullRefresh by remember {
        derivedStateOf { gridState.isAtTop() }
    }

    PullRefreshArea(
        modifier = Modifier.fillMaxSize(),
        enabled = canPullRefresh && !isLoading,
        isRefreshing = isLoading,
        onRefresh = onRefresh
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = videos,
                    key = { video -> video.id }
                ) { video ->
                    VideoItemCard(
                        video = video,
                        onClick = { onVideoClick(video) },
                        onInfo = { onVideoInfo(video) },
                        onShare = { onShareVideo(video) },
                        onDelete = { onDeleteVideo(video) }
                    )
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }
}

private fun LazyGridState.isAtTop(): Boolean {
    return firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
}

@Composable
private fun PullRefreshArea(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    var pullDistance by remember { mutableStateOf(0f) }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullDistance = 0f
        }
    }

    Box(
        modifier = modifier.pointerInput(enabled, isRefreshing) {
            detectVerticalDragGestures(
                onVerticalDrag = { _, dragAmount ->
                    if (enabled && !isRefreshing && dragAmount > 0f) {
                        pullDistance = (pullDistance + dragAmount).coerceAtMost(
                            PULL_REFRESH_TRIGGER_DISTANCE + 80f
                        )
                    }
                },
                onDragEnd = {
                    if (enabled && !isRefreshing && pullDistance >= PULL_REFRESH_TRIGGER_DISTANCE) {
                        onRefresh()
                    }
                    pullDistance = 0f
                },
                onDragCancel = {
                    pullDistance = 0f
                }
            )
        }
    ) {
        content()

        if (pullDistance > 0f || isRefreshing) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = if (isRefreshing) {
                            "Refreshing"
                        } else if (pullDistance >= PULL_REFRESH_TRIGGER_DISTANCE) {
                            "Release to refresh"
                        } else {
                            "Pull to refresh"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRequiredScreen(
    modifier: Modifier = Modifier,
    onGrantPermission: () -> Unit,
    onPickSingleVideo: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Choose how to open videos",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Grant video access to show your full local library, or open one video using the system picker without full library access.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(onClick = onGrantPermission) {
                Text("Show Full Library")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(onClick = onPickSingleVideo) {
                Text("Open One Video")
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(onClick = onOpenAppSettings) {
                Text("Open App Settings")
            }
        }
    }
}

@Composable
private fun EmptyLibraryScreen(
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onPickSingleVideo: () -> Unit
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No videos found",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tap refresh after adding videos, or open a single video without scanning the full library.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRefresh) {
                    Text("Refresh")
                }

                Button(onClick = onPickSingleVideo) {
                    Text("Open Video")
                }
            }
        }
    }
}

@Composable
private fun EmptySearchScreen(
    modifier: Modifier = Modifier,
    query: String,
    onClearSearch: () -> Unit
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No matching videos",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Nothing matched \"$query\".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(onClick = onClearSearch) {
                Text("Clear Search")
            }
        }
    }
}

@Composable
private fun ErrorLibraryScreen(
    modifier: Modifier = Modifier,
    message: String,
    onRetry: () -> Unit,
    onPickSingleVideo: () -> Unit
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Library error",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRetry) {
                    Text("Retry")
                }

                Button(onClick = onPickSingleVideo) {
                    Text("Open Video")
                }
            }
        }
    }
}

@Composable
private fun LoadingSkeletonGrid(modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier.padding(horizontal = 10.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(8) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(14.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .height(12.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoItemCard(
    video: VideoItem,
    onClick: () -> Unit,
    onInfo: () -> Unit = {},
    onShare: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = video.uri,
                    contentDescription = video.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Text(
                    text = formatDuration(video.duration),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(Color.Black.copy(alpha = 0.70f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )

                Text(
                    text = video.resolutionLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )

                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Video options",
                        tint = Color.White
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play") },
                        leadingIcon = {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Info") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Info, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onInfo()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Share, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Delete, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = video.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = formatFileSize(video.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileInfoBottomSheet(
    video: VideoItem,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "File info",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(14.dp))

            InfoRow(label = "Name", value = video.name)
            InfoRow(label = "Size", value = formatFileSize(video.size))
            InfoRow(label = "Duration", value = formatDuration(video.duration))
            InfoRow(label = "Resolution", value = formatResolution(video))
            InfoRow(label = "Date added", value = formatDateAdded(video.dateAdded))
            InfoRow(label = "Uri", value = video.uri.toString())

            Spacer(modifier = Modifier.height(16.dp))

            Divider()

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onPlay) {
                    Text("Play")
                }

                OutlinedButton(onClick = onShare) {
                    Text("Share")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Column(modifier = Modifier.padding(vertical = 7.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun createPickedVideoItem(context: Context, uri: Uri): VideoItem {
    val displayName = queryPickedVideoName(context, uri)
        ?: Uri.decode(uri.lastPathSegment ?: "Selected video")
            .substringAfterLast('/')
            .ifBlank { "Selected video" }

    return VideoItem(
        id = uri.toString().hashCode().toLong(),
        name = displayName,
        duration = 0L,
        uri = uri,
        size = 0L,
        dateAdded = 0L,
        width = 0,
        height = 0
    )
}

private fun queryPickedVideoName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }.getOrNull()
}

private fun shareVideo(context: Context, video: VideoItem) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "video/*"
        putExtra(Intent.EXTRA_STREAM, video.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(Intent.createChooser(shareIntent, "Share video"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to share this video.", Toast.LENGTH_SHORT).show()
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )
    context.startActivity(intent)
}

fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "--:--"

    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "Unknown size"

    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        kb >= 1.0 -> "%.0f KB".format(kb)
        else -> "$bytes B"
    }
}

private fun formatResolution(video: VideoItem): String {
    return if (video.width > 0 && video.height > 0) {
        "${video.width} × ${video.height} (${video.resolutionLabel})"
    } else {
        video.resolutionLabel
    }
}

private fun formatDateAdded(dateAddedSeconds: Long): String {
    if (dateAddedSeconds <= 0L) return "Unknown"

    return SimpleDateFormat(
        "dd MMM yyyy, h:mm a",
        Locale.getDefault()
    ).format(Date(dateAddedSeconds * 1000L))
}
