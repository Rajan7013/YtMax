package com.rajandownloader.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rajandownloader.app.model.DownloadStage

enum class AppNavScreen {
    HOME, DOWNLOADS
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var currentNavScreen by rememberSaveable { mutableStateOf(AppNavScreen.HOME) }
    val currentProgress by viewModel.currentProgress.collectAsStateWithLifecycle()
    val downloadHistory by viewModel.downloadHistory.collectAsStateWithLifecycle()

    val isDownloading = currentProgress.stage in listOf(
        DownloadStage.DOWNLOADING,
        DownloadStage.DOWNLOADING_VIDEO,
        DownloadStage.DOWNLOADING_AUDIO,
        DownloadStage.MERGING,
        DownloadStage.SAVING
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentNavScreen == AppNavScreen.HOME,
                    onClick = { currentNavScreen = AppNavScreen.HOME },
                    icon = {
                        Icon(
                            imageVector = if (currentNavScreen == AppNavScreen.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = "Home"
                        )
                    },
                    label = { Text("Home") }
                )

                NavigationBarItem(
                    selected = currentNavScreen == AppNavScreen.DOWNLOADS,
                    onClick = { currentNavScreen = AppNavScreen.DOWNLOADS },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (isDownloading) {
                                    Badge { Text("1") }
                                } else if (downloadHistory.isNotEmpty()) {
                                    Badge { Text(downloadHistory.size.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (currentNavScreen == AppNavScreen.DOWNLOADS) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                                contentDescription = "Downloads"
                            )
                        }
                    },
                    label = { Text("Downloads") }
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (currentNavScreen) {
                AppNavScreen.HOME -> HomeScreen(viewModel = viewModel)
                AppNavScreen.DOWNLOADS -> DownloadsScreen(viewModel = viewModel)
            }
        }
    }
}
