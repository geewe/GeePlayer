package com.geeplayer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 导航路由定义
 */
sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object NowPlaying : Screen("now_playing", "正在播放", Icons.Rounded.MusicNote)
    data object Settings : Screen("settings", "设置", Icons.Rounded.Settings)

    companion object {
        val items = listOf(NowPlaying)
    }
}