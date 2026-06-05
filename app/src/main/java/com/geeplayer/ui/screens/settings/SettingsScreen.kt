package com.geeplayer.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geeplayer.data.preferences.AppPreferences
import com.geeplayer.service.ReceiverForegroundService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()

    // 从 DataStore 读取状态
    val dataStoreDeviceName by prefs.deviceName.collectAsState(initial = "GeePlayer")
    val dataStoreDarkMode by prefs.darkMode.collectAsState(initial = true)
        val dataStoreFont by prefs.fontFamily.collectAsState(initial = "serif")
    val dataStoreVolumeNorm by prefs.volumeNormalization.collectAsState(initial = false)
    val dataStoreCrossfade by prefs.crossfadeEnabled.collectAsState(initial = false)
    val dataStoreBootStart by prefs.bootStart.collectAsState(initial = false)

    var deviceName by remember { mutableStateOf(dataStoreDeviceName) }
    var isDarkMode by remember { mutableStateOf(dataStoreDarkMode) }
        var currentFont by remember { mutableStateOf(dataStoreFont) }
    var volumeNormalization by remember { mutableStateOf(dataStoreVolumeNorm) }
    var crossfadeEnabled by remember { mutableStateOf(dataStoreCrossfade) }
    var bootStart by remember { mutableStateOf(dataStoreBootStart) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showFontPicker by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showNetworkDialog by remember { mutableStateOf(false) }
    var availableInterfaces by remember { mutableStateOf<List<com.geeplayer.util.NetworkInterfaceInfo>>(emptyList()) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 同步 DataStore → UI
    LaunchedEffect(dataStoreDeviceName) { deviceName = dataStoreDeviceName }
    LaunchedEffect(dataStoreDarkMode) { isDarkMode = dataStoreDarkMode }
        LaunchedEffect(dataStoreFont) { currentFont = dataStoreFont }
    LaunchedEffect(dataStoreVolumeNorm) { volumeNormalization = dataStoreVolumeNorm }
    LaunchedEffect(dataStoreCrossfade) { crossfadeEnabled = dataStoreCrossfade }
    LaunchedEffect(dataStoreBootStart) { bootStart = dataStoreBootStart }

    // 横屏字体选择对话框
    val fontOptions = listOf(
        "pf_hu_tu_ti" to "频凡胡涂体",
        "wen_kai_bold" to "文楷 Bold",
        "yang_ren_dong_zhu_shi_ti" to "杨仁东注诗体",
        "jiang_cheng_lyric_song" to "江城律动宋"
    )
    if (showFontPicker) {
        AlertDialog(
            onDismissRequest = { showFontPicker = false },
            title = { Text("横屏歌词字体") },
            text = {
                Column {
                    fontOptions.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        prefs.setFontFamily(key)
                                        currentFont = key
                                        showFontPicker = false
                                        snackbarHostState.showSnackbar("字体已切换: $label")
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentFont == key,
                                onClick = {
                                    scope.launch {
                                        prefs.setFontFamily(key)
                                        currentFont = key
                                        showFontPicker = false
                                        snackbarHostState.showSnackbar("字体已切换: $label")
                                    }
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFontPicker = false }) { Text("关闭") }
            }
        )
    }

    // 设备名称编辑对话框
    if (showNameDialog) {
        var editName by remember { mutableStateOf(deviceName) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("设备名称") },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it.take(32) },
                    singleLine = true,
                    label = { Text("名称") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        prefs.setDeviceName(editName)
                        deviceName = editName
                        // 运行时更新 DLNA 设备名称
                        try {
                            ReceiverForegroundService.currentService?.upnpStack?.updateDeviceName(editName)
                        } catch (e: Exception) { }
                        showNameDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar("设备名称已更新: $editName")
                        }
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("取消") }
            }
        )
    }

    // 关于对话框
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于") },
            text = {
                Column {
                    Text("GeePlayer", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("版本: 1.1.0")
                    Text("GeePlayer - DLNA 媒体渲染器")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("确定") }
            }
        )
    }

    // 均衡器页面 (内嵌)
// --- 网络接口对话框 ---
    if (showNetworkDialog) {
        val networkUtils = com.geeplayer.util.NetworkUtils
        AlertDialog(
            onDismissRequest = { showNetworkDialog = false },
            title = { Text("选择网络接口") },
            text = {
                Column {
                    if (availableInterfaces.isEmpty()) {
                        Text("未检测到可用网络接口", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    } else {
                        availableInterfaces.forEach { iface ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("已选择: ${iface.displayName} (${iface.ip})")
                                            showNetworkDialog = false
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Wifi, contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(iface.displayName, style = MaterialTheme.typography.bodyLarge)
                                    Text(iface.ip, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNetworkDialog = false }) { Text("关闭") }
            }
        )
    }

    if (showEqualizer) {
        EqualizerScreen(onBack = { showEqualizer = false })
        return
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(it)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection("通用") {
                SettingsCard(Icons.Rounded.Devices, "设备名称", deviceName, onClick = { showNameDialog = true })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SettingsCard(Icons.Rounded.DarkMode, "深色模式",
                    if (isDarkMode) "已开启" else "已关闭",
                    onClick = {
                        isDarkMode = !isDarkMode
                        scope.launch {
                            prefs.setDarkMode(isDarkMode)
                            snackbarHostState.showSnackbar("深色模式: ${if (isDarkMode) "开" else "关"}")
                        }
                    })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SettingsCard(Icons.Rounded.TextFields, "横屏歌词字体",
                    fontOptions.firstOrNull { it.first == currentFont }?.second ?: "衬线体",
                    onClick = { showFontPicker = true })
            }

            Spacer(Modifier.height(16.dp))

            SettingsSection("音频") {
                SettingsCard(Icons.Rounded.Equalizer, "均衡器", "自定义音效", onClick = { showEqualizer = true })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SettingsCard(Icons.Rounded.VolumeUp, "音量归一化",
                    if (volumeNormalization) "已开启" else "已关闭",
                    onClick = {
                        scope.launch {
                            prefs.setVolumeNormalization(!volumeNormalization)
                            snackbarHostState.showSnackbar(
                                "音量归一化: ${if (!volumeNormalization) "开" else "关"}")
                        }
                    })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SettingsCard(Icons.Rounded.Shuffle, "淡入淡出",
                    if (crossfadeEnabled) "已开启" else "已关闭",
                    onClick = {
                        scope.launch {
                            prefs.setCrossfadeEnabled(!crossfadeEnabled)
                            snackbarHostState.showSnackbar(
                                "淡入淡出: ${if (!crossfadeEnabled) "开" else "关"}")
                        }
                    })
            }

            Spacer(Modifier.height(16.dp))

            SettingsSection("网络") {
                SettingsCard(Icons.Rounded.Wifi, "网络接口",
                    com.geeplayer.util.NetworkUtils.getLocalIpAddress(),
                    onClick = {
                        availableInterfaces = com.geeplayer.util.NetworkUtils.getAvailableInterfaces()
                        showNetworkDialog = true
                    })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SettingsCard(Icons.Rounded.Dns, "HTTP 端口", "49820", onClick = { })
            }

            Spacer(Modifier.height(16.dp))

            SettingsSection("其他") {
                SettingsCard(Icons.Rounded.PowerSettingsNew, "开机自启",
                    if (bootStart) "已开启" else "已关闭",
                    onClick = {
                        scope.launch {
                            val newVal = !bootStart
                            prefs.setBootStart(newVal)
                            com.geeplayer.service.BootReceiver.setEnabled(context, newVal)
                            snackbarHostState.showSnackbar(
                                "开机自启: ${if (newVal) "开" else "关"}")
                        }
                    })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SettingsCard(Icons.Rounded.Info, "关于", "GeePlayer v1.1.0", onClick = { showAbout = true })
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) { Column(content = content) }
    }
}

@Composable
fun SettingsCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
    }
}
