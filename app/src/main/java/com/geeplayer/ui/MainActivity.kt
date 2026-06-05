package com.geeplayer.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.geeplayer.service.ReceiverForegroundService
import com.geeplayer.ui.navigation.Screen
import com.geeplayer.ui.screens.nowplaying.NowPlayingScreen
import com.geeplayer.ui.screens.settings.SettingsScreen
import com.geeplayer.ui.theme.DlnaReceiverTheme
import com.geeplayer.ui.viewmodel.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startService()
        setContent {
            DlnaReceiverTheme(darkTheme = true) {
                DlnaReceiverMainScreen()
            }
        }
    }

    private fun startService() {
        val intent = Intent(this, ReceiverForegroundService::class.java).apply {
            action = ReceiverForegroundService.ACTION_START
        }
        startForegroundService(intent)
    }
}

@Composable
fun DlnaReceiverMainScreen() {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val showMiniPlayer by remember { mutableStateOf(true) }

    // 绑定播放器 — 等待前台服务初始化后绑定
    LaunchedEffect(Unit) {
        // 轮询等待服务初始化完成
        while (ReceiverForegroundService.currentPlayer == null) {
            delay(500)
        }
        ReceiverForegroundService.currentPlayer?.let { player ->
            playerViewModel.bindPlayer(player)
        }
    }

    // 获取播放控制函数
    val playPause: () -> Unit = {
        val player = ReceiverForegroundService.currentPlayer
        if (playerViewModel.isPlaying) player?.pause() else player?.play()
    }
    val next: () -> Unit = { ReceiverForegroundService.currentPlayer?.next() }
    val previous: () -> Unit = { ReceiverForegroundService.currentPlayer?.previous() }
    val seek: (Float) -> Unit = { ratio ->
        val player = ReceiverForegroundService.currentPlayer
        val duration = playerViewModel.durationMs
        if (duration > 0L && player != null) {
            player.seekTo((ratio * duration).toLong())
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.NowPlaying.route
            ) {
                composable(Screen.NowPlaying.route) {
                    NowPlayingScreen(
                        viewModel = playerViewModel,
                        onPlayPause = playPause,
                        onNext = next,
                        onPrevious = previous,
                        onSeek = seek,
                        onSettings = {
                            navController.navigate(Screen.Settings.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable(Screen.Settings.route) { SettingsScreen() }
            }


        }
    }
}
