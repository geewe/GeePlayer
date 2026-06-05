package com.geeplayer.ui.screens.nowplaying

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geeplayer.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

/**
 * 正在播放页面 — CD封面 + 进度 + 控制 + 歌词 + 频谱
 */
@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    // 横屏隐藏系统状态栏，实现绝对全屏
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(isLandscape) {
        val window = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
        if (isLandscape) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val fontPref = remember { com.geeplayer.data.preferences.AppPreferences(ctx) }
    val currentFontFamily by fontPref.fontFamily.collectAsState(initial = "jiang_cheng_lyric_song")

    if (isLandscape) {
        // 横屏：全屏动态歌词页面
        DynamicLyricsView(
            lyricsState = viewModel.lyricsState,
            coverUrl = viewModel.coverUrl,
            isLandscape = true,
            fontFamily = currentFontFamily,
            modifier = Modifier
                .fillMaxSize()
                .then(modifier)
        )
        return
    }
    // CD 旋转动画
    val infiniteTransition = rememberInfiniteTransition(label = "cd")
    val cdRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "cd_rotation"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // 封面模糊背景
        if (viewModel.coverUrl != null && viewModel.coverUrl!!.isNotEmpty()) {
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(viewModel.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 80.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
        // 深色遮罩
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Spacer(modifier = Modifier.height(24.dp))

        // 专辑封面 CD (旋转)
        AlbumArtSection(
            isPlaying = viewModel.isPlaying,
            coverUrl = viewModel.coverUrl,
            title = viewModel.currentTitle,
            rotation = cdRotation
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 歌曲信息
        Text(
            viewModel.currentTitle,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            viewModel.currentArtist.ifEmpty { "等待推送..." },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 进度条
        ProgressSection(
            currentMs = viewModel.positionMs,
            totalMs = viewModel.durationMs,
            bufferedMs = viewModel.bufferedMs,
            onSeek = onSeek
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 播放控制
        PlaybackControls(
            isPlaying = viewModel.isPlaying,
            isLoading = viewModel.isLoading,
            onPlayPause = onPlayPause
        )

        Spacer(modifier = Modifier.height(8.dp))

        Spacer(modifier = Modifier.height(16.dp))

        // 歌词显示
        if (viewModel.lyricsState.isLoaded && viewModel.lyricsState.lrcResult?.lines?.isNotEmpty() == true) {
            LyricsDisplayView(lyricsState = viewModel.lyricsState, modifier = Modifier.fillMaxSize())
        } else if (viewModel.lyricsState.isSearching) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("搜索歌词中...", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("暂无歌词", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        }

        // 设置按钮（右下角）
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        }
    }
}

@Composable
fun AlbumArtSection(
    isPlaying: Boolean,
    coverUrl: String?,
    title: String,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(250.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (coverUrl != null && coverUrl.isNotBlank()) {
            // Show cover image from URL
            coil.compose.AsyncImage(
                model = coverUrl,
                contentDescription = "Album Art",
                modifier = Modifier
                    .size(220.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .graphicsLayer {
                        rotationZ = if (isPlaying) rotation else 0f
                    },
                contentScale = ContentScale.Crop
            )
        } else {
            // CD animation fallback
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 2.dp.toPx()
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(Color(0xFFBB86FC), Color(0xFF6750A4), Color(0xFFBB86FC))
                    ),
                    style = Stroke(width = strokeWidth)
                )
            }
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "\u266A",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ProgressSection(
    currentMs: Long,
    totalMs: Long,
    bufferedMs: Long,
    onSeek: (Float) -> Unit, // 0.0 ~ 1.0 进度比例
    modifier: Modifier = Modifier
) {
    val progress = if (totalMs > 0) currentMs.toFloat() / totalMs else 0f
    val sliderValue = remember(progress) { mutableFloatStateOf(progress) }

    Column(modifier = modifier.fillMaxWidth()) {
        // 可拖拽进度条
        Slider(
            value = sliderValue.floatValue,
            onValueChange = { sliderValue.floatValue = it },
            onValueChangeFinished = { onSeek(sliderValue.floatValue) },
            modifier = Modifier.fillMaxWidth().height(32.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFBB86FC),
                activeTrackColor = Color(0xFFBB86FC),
                inactiveTrackColor = Color(0xFF3D3D5C),
                disabledThumbColor = Color(0xFF3D3D5C),
                disabledActiveTrackColor = Color(0xFF5D5D7C),
                disabledInactiveTrackColor = Color(0xFF3D3D5C),
            ),
            enabled = totalMs > 0
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 时间标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatTime(currentMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                formatTime(totalMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFBB86FC), Color(0xFF9C6ADE))
                    )
                )
                .clickable(enabled = !isLoading, onClick = onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (isPlaying) "暂停" else "播放",
                Modifier.size(40.dp),
                tint = Color.White
            )
        }
    }
}

@Composable
fun LyricsDisplayView(lyricsState: com.geeplayer.lyrics.LyricsSyncEngine.LyricsState, modifier: Modifier = Modifier) {
    val lines = lyricsState.lrcResult?.lines ?: return
    val currentIndex = lyricsState.currentLineIndex

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 上一行
            if (currentIndex > 0) {
                val prevLine = lines[currentIndex - 1]
                val prevAlpha = lyricsState.progressInLine * 0.5f
                Text(
                    prevLine.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = (0.3f - prevAlpha * 0.3f).coerceIn(0f, 0.3f)),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 当前行
            if (currentIndex in lines.indices) {
                val currentLine = lines[currentIndex]
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    currentLine.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFBB86FC),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 下一行
            if (currentIndex >= 0 && currentIndex + 1 < lines.size) {
                val nextLine = lines[currentIndex + 1]
                Text(
                    nextLine.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.3f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (currentIndex < 0 && lines.isNotEmpty()) {
                Text(
                    lines.first().text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.3f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FrequencySpectrumView(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val barCount = 32
    val amplitudes = remember { mutableStateListOf(*Array(barCount) { 0f }) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                delay(100)
                for (i in 0 until barCount) {
                    amplitudes[i] = (sin(i * 0.5 + System.currentTimeMillis() * 0.003) * 0.5 + 0.5)
                        .toFloat().coerceIn(0.1f, 1f) * (0.3f + Math.random().toFloat() * 0.7f)
                }
            }
        } else {
            amplitudes.fill(0f)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width / barCount
            val gap = 2.dp.toPx()
            amplitudes.forEachIndexed { index, amplitude ->
                val barHeight = size.height * amplitude
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFFBB86FC), Color(0xFF6750A4)),
                        startY = size.height - barHeight,
                        endY = size.height
                    ),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(w - gap, barHeight),
                    topLeft = Offset(index * w, size.height - barHeight)
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
