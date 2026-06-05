package com.geeplayer.ui.screens.nowplaying

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.geeplayer.lyrics.LyricsSyncEngine
import kotlin.math.*
import kotlin.random.Random

// ======================== 星火余烬（封面上方飘动） ========================

// 极光参数 — 每条极光是一个流动的光帘
data class AuroraBand(
    var phase: Float,         // 波动相位
    var speed: Float,         // 波动速度
    var hue: Float,           // 颜色色调
    var heightRatio: Float,   // 垂直位置 0~1
    var amplitude: Float,     // 波动幅度
    var frequency: Float,     // 波动频率
    var alpha: Float          // 透明度
)

// ======================== 逐句显示参数 ========================

data class LineDisplayParams(
    val text: String,
    val startX: Float,
    val startY: Float,
    val driftX: Float,
    val driftY: Float,
    val scale: Float,
    val rotation: Float
)

/**
 * 逐句动态歌词 — 封面模糊背景 + 余烬粒子 + 随机位置漂移
 */
@Composable
fun DynamicLyricsView(
    lyricsState: LyricsSyncEngine.LyricsState,
    coverUrl: String? = null,
    isLandscape: Boolean = false,
    fontFamily: String = "jiang_cheng_lyric_song",
    onTapBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lines = lyricsState.lrcResult?.lines ?: return
    val currentIndex = lyricsState.currentLineIndex

    // ======================== 星火余烬粒子 ========================

    var auroraBands by remember { mutableStateOf(emptyList<AuroraBand>()) }

    // 初始化极光（4条光帘，不同颜色和波动参数）
    LaunchedEffect(Unit) {
        auroraBands = listOf(
            AuroraBand(phase = 0f, speed = 0.6f, hue = 150f, heightRatio = 0.35f, amplitude = 0.12f, frequency = 2.5f, alpha = 0.4f),
            AuroraBand(phase = 2f, speed = 0.4f, hue = 190f, heightRatio = 0.50f, amplitude = 0.15f, frequency = 2.0f, alpha = 0.35f),
            AuroraBand(phase = 4f, speed = 0.5f, hue = 280f, heightRatio = 0.40f, amplitude = 0.10f, frequency = 3.0f, alpha = 0.3f),
            AuroraBand(phase = 1.5f, speed = 0.35f, hue = 220f, heightRatio = 0.60f, amplitude = 0.13f, frequency = 1.8f, alpha = 0.25f),
        )
    }

    val frameTick by rememberInfiniteTransition().animateFloat(
        0f, 1000f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "ft"
    )

    LaunchedEffect(frameTick) {
        auroraBands = auroraBands.map { a ->
            a.apply {
                phase += speed * 0.04f
                // 缓慢漂移色调
                hue += 0.08f
                if (hue > 360f) hue -= 360f
                // 透明度微微呼吸
                alpha = (0.2f + 0.2f * sin(phase * 0.5f)).coerceIn(0.15f, 0.5f)
            }
        }
    }

    // ======================== 逐句歌词 ========================

    val currentText = if (currentIndex in lines.indices) lines[currentIndex].text else ""
    val nextText = if (currentIndex >= 0 && currentIndex + 1 < lines.size) lines[currentIndex + 1].text else ""

    // 当前句 — 随机位置 + 漂移参数
    val lineParams = remember(currentIndex) {
        LineDisplayParams(
            text = currentText,
            startX = 0.05f + Random.nextFloat() * 0.50f,
            startY = 0.05f + Random.nextFloat() * 0.8f,
            driftX = (Random.nextFloat() - 0.5f) * 80f,
            driftY = (Random.nextFloat() - 0.5f) * 60f,
            scale = 0.85f + Random.nextFloat() * 0.3f,
            rotation = (Random.nextFloat() - 0.5f) * 6f
        )
    }

    // 当前句淡入
    val appearProgress = remember { Animatable(0f) }
    LaunchedEffect(currentIndex) {
        appearProgress.snapTo(0f)
        appearProgress.animateTo(1f,
            spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium))
    }

    // 当前句漂移 (正弦缓动，0..1 循环)
    val driftPhase by rememberInfiniteTransition().animateFloat(
        0f, 1f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "drift"
    )

    // driftX/driftY 直接是 dp 幅度，用正弦波产生平滑往返
    val driftOffsetX = lineParams.driftX * sin(driftPhase * 2f * PI.toFloat()).toFloat()
    val driftOffsetY = lineParams.driftY * sin((driftPhase * 2f * PI + 0.5f).toFloat()).toFloat()



    // 下一句淡入预览
    val nextAlpha = remember { Animatable(0f) }
    LaunchedEffect(currentIndex) {
        if (nextText.isNotEmpty()) {
            nextAlpha.snapTo(0f)
            nextAlpha.animateTo(0.2f, tween(600, easing = EaseOutCubic))
        }
    }

    // ======================== 渲染 ========================

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .then(if (isLandscape) Modifier.clickable(
                indication = null, interactionSource = remember { MutableInteractionSource() }
            ) { onTapBack() } else Modifier)
    ) {
        // 封面模糊背景（满屏）
        if (coverUrl != null && coverUrl.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 80.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                contentScale = ContentScale.Crop
            )
        }

        // 深色遮罩（保证文字可读）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
        )

        // 星火余烬粒子（封面上方飘动）
        Canvas(Modifier.fillMaxSize()) {
            drawAurora(auroraBands)
        }

        // 歌词层
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {



            // 当前句
            if (currentText.isNotEmpty()) {
                DriftingLyricText(
                    text = currentText,
                    alpha = appearProgress.value.coerceIn(0f, 1f),
                    normalizedX = lineParams.startX,
                    normalizedY = lineParams.startY,
                    driftX = driftOffsetX,
                    driftY = driftOffsetY,
                    rotation = lineParams.rotation,
                    scale = lineParams.scale * (0.5f + 0.5f * appearProgress.value),
                    fontSize = if (isLandscape) 52.sp else 44.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    bold = true,
                    fontFamily = fontFamily
                )
            }

            // 下一句预览（独立随机位置）
            val nextParams = remember(nextText) {
                if (nextText.isNotEmpty()) LineDisplayParams(
                    text = nextText, startX = 0.05f + Random.nextFloat() * 0.50f,
                    startY = 0.05f + Random.nextFloat() * 0.8f,
                    driftX = (Random.nextFloat() - 0.5f) * 80f,
                    driftY = (Random.nextFloat() - 0.5f) * 60f,
                    rotation = (Random.nextFloat() - 0.5f) * 8f,
                    scale = 0.6f + Random.nextFloat() * 0.25f
                ) else null
            }
            if (nextText.isNotEmpty() && nextParams != null) {
                val nextDriftX = nextParams.driftX * sin((driftPhase * 2f * PI + 1f).toFloat()).toFloat()
                val nextDriftY = nextParams.driftY * sin((driftPhase * 2f * PI + 1.5f).toFloat()).toFloat()
                DriftingLyricText(
                    text = nextText,
                    alpha = nextAlpha.value.coerceIn(0f, 1f),
                    normalizedX = nextParams.startX,
                    normalizedY = nextParams.startY,
                    driftX = nextDriftX,
                    driftY = nextDriftY,
                    rotation = nextParams.rotation, scale = nextParams.scale,
                    fontSize = if (isLandscape) 28.sp else 24.sp,
                    color = Color.White.copy(alpha = 0.35f),
                    fontFamily = fontFamily
                )
            }

            // Fallback
            if (currentText.isEmpty()) {
                Text(
                    "♪ 等待歌词...",
                    fontSize = if (isLandscape) 20.sp else 16.sp,
                    color = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }


    }
}

// ======================== 漂移歌词组件 ========================

@Composable
fun DriftingLyricText(
    text: String,
    alpha: Float,
    normalizedX: Float,
    normalizedY: Float,
    driftX: Float,
    driftY: Float,
    rotation: Float,
    scale: Float,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    bold: Boolean = false,
    fontFamily: String = "jiang_cheng_lyric_song"
) {
    val xFrac by animateFloatAsState(normalizedX, tween(300), label = "x")
    val yFrac by animateFloatAsState(normalizedY, tween(300), label = "y")

    val textColor = if (bold) color else color
    
    // 字体映射（从 res/font 加载自定义字体）
    val fontFamilyValue = remember(fontFamily) {
        when (fontFamily) {
            "pf_hu_tu_ti" -> FontFamily(androidx.compose.ui.text.font.Font(com.geeplayer.R.font.pf_hu_tu_ti))
            "wen_kai_bold" -> FontFamily(androidx.compose.ui.text.font.Font(com.geeplayer.R.font.wen_kai_bold))
            "yang_ren_dong_zhu_shi_ti" -> FontFamily(androidx.compose.ui.text.font.Font(com.geeplayer.R.font.yang_ren_dong_zhu_shi_ti))
            "jiang_cheng_lyric_song" -> FontFamily(androidx.compose.ui.text.font.Font(com.geeplayer.R.font.jiang_cheng_lyric_song))
            else -> FontFamily(androidx.compose.ui.text.font.Font(com.geeplayer.R.font.jiang_cheng_lyric_song))
        }
    }

    // 使用全宽 Box，根据 xFrac 选择对齐方式，保证文字不超出屏幕
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenH = maxHeight
        val yOffset = screenH * yFrac + driftY.dp

        val horizontalAlign = when {
            xFrac < 0.25f -> Alignment.CenterStart
            xFrac < 0.50f -> Alignment.Center
            xFrac < 0.75f -> Alignment.Center
            else -> Alignment.CenterEnd
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = yOffset),
            contentAlignment = horizontalAlign
        ) {
            Text(
                text,
                fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
            fontFamily = fontFamilyValue,
                fontSize = fontSize,
                color = textColor.copy(alpha = (textColor.alpha * alpha).coerceIn(0f, 1f)),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .offset(x = driftX.dp)
                    .rotate(rotation)
                    .scale(scale.coerceIn(0.1f, 2f))
            )
        }
    }
}

// ======================== 极光绘制 ========================

private fun DrawScope.drawAurora(auroraBands: List<AuroraBand>) {
    val w = size.width
    val h = size.height
    val steps = 80  // 水平采样点数

    for (band in auroraBands) {
        val bandAlpha = band.alpha.coerceIn(0f, 1f)
        val centerY = band.heightRatio * h
        val amp = band.amplitude * h
        val freq = band.frequency

        // 每条极光由两条路径构成：主光帘 + 上方补光
        val path = Path()
        val path2 = Path()

        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = t * w

            // 多层正弦波叠加产生自然飘动感
            val wave1 = sin(t * freq * 4f * PI.toFloat() + band.phase) * amp
            val wave2 = sin(t * freq * 2f + band.phase * 0.7f) * amp * 0.5f
            val wave3 = sin(t * 3f + band.phase * 0.3f) * amp * 0.3f
            val offset = wave1 + wave2 + wave3

            // 主光帘 Y 位置（带厚度）
            val yBase = centerY + offset
            val thickness = 30f + 20f * sin(t * PI.toFloat() + band.phase * 0.5f).toFloat()
            val yTop = yBase - thickness
            val yBottom = yBase + thickness

            if (i == 0) {
                path.moveTo(x, yTop)
                path2.moveTo(x, yBottom)
            } else {
                path.lineTo(x, yTop)
                path2.lineTo(x, yBottom)
            }
        }

        // 闭合路径
        path.lineTo(w, 0f)
        path.lineTo(0f, 0f)
        path.close()
        path2.lineTo(w, h)
        path2.lineTo(0f, h)
        path2.close()

        // 主色调 — 从 hue 渐变到相邻色
        val mainColor = Color.hsl(band.hue, 0.7f, 0.5f)
        val fadeColor = Color.hsl((band.hue + 30f) % 360f, 0.6f, 0.4f)

        // 绘制上补光
        drawPath(
            path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    mainColor.copy(alpha = 0f),
                    mainColor.copy(alpha = bandAlpha * 0.3f),
                    mainColor.copy(alpha = 0f),
                ),
                startY = 0f,
                endY = h
            )
        )

        // 绘制下补光
        drawPath(
            path2,
            brush = Brush.verticalGradient(
                colors = listOf(
                    fadeColor.copy(alpha = 0f),
                    fadeColor.copy(alpha = bandAlpha * 0.25f),
                    fadeColor.copy(alpha = 0f),
                ),
                startY = 0f,
                endY = h
            )
        )
    }

    // 覆盖一层非常淡的垂直渐变，模拟极光在地平线的辉光
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.hsl((auroraBands.firstOrNull()?.hue ?: 150f), 0.5f, 0.15f, 0.06f),
                Color.Transparent
            ),
            startY = 0f,
            endY = h
        )
    )
}
