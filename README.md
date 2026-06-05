# GeePlayer 🎵

> **DLNA Media Renderer + AirPlay Receiver** — 把你的 Android 设备变成无线音箱

GeePlayer 是一款 Android 平台的 DLNA/UPnP 媒体渲染器，支持从网易云音乐等 App 无线推送音乐到手机播放。支持 AirPlay 接收、动态歌词、多种播放控制功能。

[![GitHub release](https://img.shields.io/github/v/release/geewe/GeePlayer)](https://github.com/geewe/GeePlayer/releases)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

---

## ✨ 功能特性

### 📡 无线接收
- **DLNA/UPnP Media Renderer** — 标准 UPnP AV 架构，支持 AVTransport、RenderingControl、ConnectionManager 三大服务
- **AirPlay 接收** — 支持 iOS 设备通过 AirPlay 推送音乐（实验性）
- **多协议兼容** — 同时支持 SSDP 发现和 mDNS 广播

### 🎨 播放界面
- **旋转 CD 封面** — 正在播放页面展示旋转黑胶唱片效果
- **频谱可视化** — 动态音频频谱柱状图
- **歌词同步** — 自动从网易云搜索并同步歌词
- **横屏全屏歌词** — 手机横屏自动切换全屏动态歌词页面
  - 歌词随机位置逐句出现
  - 封面模糊背景 + 极光光影特效
  - 支持 4 种自定义歌词字体切换

### 🎛 播放控制
- 播放/暂停、上一曲/下一曲、进度拖拽
- 音量控制、静音切换
- 播放队列管理
- 均衡器（EQ）调节

### ⚙️ 系统功能
- 前台服务保活
- WiFi 多播锁 + WiFi 锁 + 唤醒锁（四锁保活）
- 网络状态监听，断网自动重连 SSDP
- 开机自启
- 暗色模式
- 自定义设备名称

---

## 📱 快速开始

### 下载安装

从 [Releases](https://github.com/geewe/GeePlayer/releases) 下载最新 APK，直接安装到 Android 手机。

> **注意**: 需要 Android 8.0 (API 26) 及以上版本

### 使用方法

1. 打开 GeePlayer，点击「启动服务」
2. 确保手机和推送设备（另一台手机/电脑）在**同一个 WiFi 网络**
3. 在网易云音乐等 App 中点击「投放」或「DLNA」按钮
4. 选择「GeePlayer」即可推送音乐

### 调试访问

手机启动 GeePlayer 后，在同一局域网用浏览器访问：

```
http://<手机IP>:49820/
```

- `http://<手机IP>:49820/device.xml` — 设备描述 XML
- `http://<手机IP>:49820/api/debug` — 调试信息页（含请求历史）
- `http://<手机IP>:49820/api/player-state` — 播放器状态

---

## 🏗 项目架构

```
app/src/main/java/com/geeplayer/
├── upnp/                    # 自研 UPnP 协议栈
│   ├── core/                # UPnP 核心引擎 + 常量
│   ├── http/                # NanoHTTPD HTTP 服务器
│   │   ├── DeviceDescriptionBuilder  # 设备描述 XML 构建
│   │   ├── SoapActionMapping         # SOAP 动作映射
│   │   ├── SoapErrorBuilder          # SOAP 错误响应
│   │   └── UpnpHttpServer            # HTTP 路由 + SOAP 处理
│   ├── services/
│   │   ├── avt/             # AVTransport:1 服务
│   │   ├── rc/              # RenderingControl:1 服务
│   │   └── cmgr/            # ConnectionManager:1 服务
│   ├── ssdp/                # SSDP 发现协议 (M-SEARCH + NOTIFY)
│   ├── eventing/            # GENA 事件订阅管理
│   └── compatibility/       # 协议兼容性修正
├── player/                  # ExoPlayer 音频引擎
│   ├── DlnaPlayer           # 播放器封装（加载、播放、进度）
│   └── EqualizerManager     # 均衡器管理
├── lyrics/                  # 歌词引擎
│   ├── LrcParser            # LRC 歌词解析
│   ├── LyricsSearchApi      # 歌词搜索（网易云）
│   └── LyricsSyncEngine     # 歌词逐字同步引擎
├── protocol_ext/            # 扩展协议
│   ├── airplay/             # AirPlay 接收器 (mDNS + RTSP + ALAC)
│   ├── cast/                # Chromecast 接收框架
│   ├── multiroom/           # 多房间同步
│   └── webserver/           # Web 远程控制
├── service/                 # 后台服务
│   ├── ReceiverForegroundService  # 前台播放服务
│   ├── NetworkStateObserver       # 网络状态监听
│   └── BootReceiver               # 开机自启
├── ui/                      # Jetpack Compose 界面
│   ├── MainActivity         # 主入口
│   ├── components/          # 共用组件
│   ├── navigation/          # 导航路由
│   ├── screens/
│   │   ├── nowplaying/      # 正在播放（CD封面 + 频谱 + 歌词 + 动态横屏）
│   │   ├── settings/        # 设置 + 均衡器
│   │   ├── discover/        # 发现页面
│   │   └── playlist/        # 播放列表
│   ├── viewmodel/           # PlayerViewModel
│   └── theme/               # Material3 主题
├── data/                    # 数据层
│   ├── preferences/         # DataStore 偏好设置
│   └── db/                  # Room 数据库（播放历史）
├── di/                      # Hilt 依赖注入
└── util/                    # 工具类（网络、图片）
```

---

## 🔧 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material3 |
| 播放引擎 | ExoPlayer (Media3) |
| HTTP 服务器 | NanoHTTPD 2.3.1 |
| 图片加载 | Coil 2.7 |
| 依赖注入 | Hilt + KSP |
| 本地存储 | DataStore Preferences + Room |
| 网络 | OkHttp + Coroutines |
| mDNS | JmDNS 3.5.9 |
| 最低 SDK | 26 (Android 8.0) |
| 目标 SDK | 35 (Android 15) |

---

## 🚀 构建

```bash
# 克隆仓库
git clone https://github.com/geewe/GeePlayer.git
cd GeePlayer

# Debug 构建
./gradlew assembleDebug

# APK 路径
# app/build/outputs/apk/debug/app-debug.apk
```

> 需要 Android SDK 35、JDK 17、Gradle 8.x

---

## 📋 协议支持

### UPnP/DLNA

| 服务 | 状态 | 说明 |
|------|------|------|
| SSDP 发现 | ✅ | M-SEARCH 响应 + 定时 NOTIFY alive |
| AVTransport:1 | ✅ | SetURI、Play、Pause、Stop、Seek、Next、Previous、GetMediaInfo |
| RenderingControl:1 | ✅ | 音量、静音、预设 |
| ConnectionManager:1 | ✅ | GetProtocolInfo、PrepareForConnection、GetCurrentConnectionInfo |
| GENA 事件 | ✅ | 订阅/退订 |

### AirPlay

| 功能 | 状态 | 说明 |
|------|------|------|
| mDNS 广播 | ✅ | _raop._tcp + _airplay._tcp |
| RTSP 控制 | ✅ | 基本控制流程 |
| ALAC 解码 | ✅ | MediaCodec 解码 |

---

## 📄 License

[MIT License](LICENSE)

---

## 🙏 致谢

- [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) — 轻量嵌入式 HTTP 服务器
- [ExoPlayer](https://github.com/google/ExoPlayer) — Android 媒体播放框架
- [JmDNS](https://github.com/jmdns/jmdns) — Java mDNS 实现
- [Coil](https://github.com/coil-kt/coil) — Kotlin 图片加载库
