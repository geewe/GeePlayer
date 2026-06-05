package com.geeplayer.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geeplayer.player.EqualizerManager

/**
 * 均衡器设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 模拟均衡器数据 (10段)
    val bandLabels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    val bandLevels = remember { mutableStateListOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f) }
    var bassBoost by remember { mutableFloatStateOf(0f) }
    var virtualizer by remember { mutableFloatStateOf(0f) }
    var reverbPreset by remember { mutableIntStateOf(0) }
    var selectedPreset by remember { mutableIntStateOf(0) }
    var eqEnabled by remember { mutableStateOf(true) }

    val presets = listOf(
        "Normal", "Classical", "Dance", "Flat",
        "Folk", "Metal", "HipHop", "Jazz",
        "Pop", "Rock", "Vocal", "Custom"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 顶部栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("均衡器", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Switch(checked = eqEnabled, onCheckedChange = { eqEnabled = it })
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== EQ 频段图形 =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("图形均衡器", style = MaterialTheme.typography.titleMedium)

                Spacer(modifier = Modifier.height(16.dp))

                // 频段可视化
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val w = size.width
                        val h = size.height
                        val barWidth = (w - 40f) / bandLevels.size
                        val centerY = h / 2f
                        val maxBarHeight = h / 2f - 10f

                        // 网格线
                        val gridColor = Color(0x33FFFFFF)
                        for (i in 0..4) {
                            val y = centerY - maxBarHeight + (maxBarHeight * i / 2f)
                            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                        }
                        drawLine(Color(0x55FFFFFF), Offset(0f, centerY), Offset(w, centerY), strokeWidth = 1f)

                        // 频段条
                        bandLevels.forEachIndexed { index, level ->
                            val barHeight = (level / 15f) * maxBarHeight
                            val x = 20f + index * barWidth + 4f
                            val barW = barWidth - 8f

                            // 填充
                            val barColor = if (level >= 0)
                                Color(0xFFBB86FC).copy(alpha = 0.7f + Math.abs(level) / 30f * 0.3f)
                            else
                                Color(0xFF6A5ACD).copy(alpha = 0.7f + Math.abs(level) / 30f * 0.3f)

                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(x, centerY - if (level >= 0) barHeight else 0f),
                                size = androidx.compose.ui.geometry.Size(barW, kotlin.math.abs(barHeight).coerceAtLeast(2f)),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                            )

                            // 频段标签
                            drawContext.canvas.nativeCanvas.drawText(
                                bandLabels[index],
                                x + barW / 2f,
                                h - 5f,
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.argb(128, 255, 255, 255)
                                    textSize = 24f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                            )
                        }

                        // 频率响应曲线
                        val path = Path()
                        bandLevels.forEachIndexed { index, level ->
                            val x = 20f + index * barWidth + barWidth / 2f
                            val y = centerY - (level / 15f) * maxBarHeight
                            if (index == 0) path.moveTo(x, y)
                            else path.lineTo(x, y)
                        }
                        drawPath(path, Color(0xFFFFD700), style = Stroke(2f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 频段滑块
                bandLevels.forEachIndexed { index, level ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            bandLabels[index],
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(32.dp),
                            textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = level,
                            onValueChange = { bandLevels[index] = it },
                            valueRange = -12f..12f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        Text(
                            "%.0f".format(level),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(28.dp),
                            textAlign = TextAlign.End,
                            color = if (level > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 预设选择 =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("预设", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                // 预设网格 4x3
                Column {
                    presets.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { presetName ->
                                val idx = presets.indexOf(presetName)
                                val isSelected = selectedPreset == idx
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedPreset = idx },
                                    label = { Text(presetName, fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 音效增强 =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("音效增强", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))

                // 低音增强
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Vibration, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("低音增强", modifier = Modifier.width(80.dp), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = bassBoost,
                        onValueChange = { bassBoost = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    Text("%.0f%%".format(bassBoost * 100), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp))
                }

                // 虚拟环绕
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SurroundSound, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("虚拟环绕", modifier = Modifier.width(80.dp), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = virtualizer,
                        onValueChange = { virtualizer = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    Text("%.0f%%".format(virtualizer * 100), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp))
                }

                // 混响
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("混响", modifier = Modifier.width(80.dp), style = MaterialTheme.typography.bodyMedium)
                    val reverbNames = listOf("无", "小房间", "大房间", "大厅", "浴室")
                    reverbPreset = reverbPreset.coerceIn(0, 4)
                    reverbNames.forEachIndexed { index, name ->
                        FilterChip(
                            selected = reverbPreset == index,
                            onClick = { reverbPreset = index },
                            label = { Text(name, fontSize = 10.sp) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
