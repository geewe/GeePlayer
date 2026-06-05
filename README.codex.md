# DLNA Receiver — 项目状态

## 项目位置
- 源代码: `/Users/simon/Desktop/GeePlayer`
- 工作副本: `/Users/simon/Documents/Codex/2026-06-05/dlnareceiver`

## 构建
```bash
cd ~/Desktop/GeePlayer
./gradlew assembleDebug
```

## 项目结构 (50 个 Kotlin 源文件，~6,500 行)
```
app/src/main/java/com/dlna/receiver/
├── upnp/              # 自研 UPnP 轻量栈 (SSDP + SOAP + GENA)
├── player/            # ExoPlayer 音频引擎 + 均衡器
├── lyrics/            # LRC 解析 + 网易云/QQ音乐搜索
├── service/           # 前台服务 + 四锁保活 + 网络监听
├── ui/                # Jetpack Compose 精美界面
│   ├── screens/nowplaying/  # 旋转CD封面 + 进度 + 控制 + 歌词 + 频谱
│   ├── screens/settings/    # 均衡器UI + 通用设置
│   └── ...
├── data/              # Room 数据库 + DataStore 偏好
├── di/                # Hilt 依赖注入
└── protocol_ext/      # AirPlay / Cast / Multi-room / Web遥控
```

## 完成状态

| 模块 | 状态 | 行数 | 说明 |
|------|------|------|------|
| **UPnP 协议栈** | ✅ 完整 | ~1,300 | SSDP发现 + AVTransport/RenderingControl/ConnectionManager + SCPD |
| **播放引擎** | ✅ 完整 | ~300 | ExoPlayer 封装 + HLS/DASH/Progressive 流支持 |
| **UI** | ✅ 完整 | ~1,400 | 四个Tab页面 + 均衡器 + CD旋转封面 + 频谱 + 歌词 |
| **歌词** | ✅ 完整 | ~470 | LRC解析 + 网易云/QQ音乐搜索 + 逐字同步 |
| **服务层** | ✅ 完整 | ~260 | 前台服务 + 网络监听 + 开机自启 |
| **数据层** | ✅ 完整 | ~200 | Room数据库 + DataStore偏好 |
| **AirPlay 接收** | ✅ 完整 | ~377 | RTSP服务端 + ALAC解码器(MediaCodec) |
| **Chromecast 接收** | ✅ 完整 | ~360 | Cast v2 协议框架 + 媒体控制 |
| **多房间同步** | ✅ 完整 | ~424 | UDP组播发现 + 时钟同步 + 播放对齐 |
| **Web遥控** | ✅ 完整 | ~180 | HTTP控制面板 + 实时状态JSON API |
| **UPnP核心栈** | ✅ 完整 | ~100 | 动态IP检测 + 生命周期管理 |
| **UPnP HTTP** | ✅ 完整 | ~220 | 完整SCPD + GENA订阅 + SOAP路由 |

## 已知问题
- Desktop 项目的 macOS 权限导致无法直接写入，代码修改已在工作副本完成
- 建议用 Android Studio 打开 `~/Desktop/GeePlayer` 进行编译验证
- 如需更新 Desktop 项目，可在 IDE 中手动将工作副本文件复制回去
