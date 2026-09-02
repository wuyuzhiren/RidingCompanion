# 骑行小智 (Riding Companion)

基于小智 AI「端云协同」思路改造的**骑行专属语音陪伴 App**，Android 端。
借鉴参考 `base.apk`（xiaozhi-android 官方版），重新实现为**自包含 + 可配置自有大模型**的应用。

## 已实现功能

| 模块 | 说明 |
|---|---|
| 语音对话 | 系统语音识别(STT) → 大模型流式对话(SSE) → 系统语音合成(TTS)，多轮上下文，流式文字实时上屏 |
| 自有大模型接入 | 设置页填写 OpenAI 兼容接口(Base URL / Key / 模型)，适配 DeepSeek、通义、GLM、Ollama 等 |
| 音乐点播 | ExoPlayer 播放；支持「添加网址」和「从本地选择」歌曲；播放列表本地持久化 |
| 全局媒体控制 | 系统音量滑块/加减；播放/暂停/上下曲：优先控制本 App → 其次系统活跃媒体会话（第三方音乐 APP）→ 兜底媒体按键 |
| 锁屏/通知控制 | Media3 MediaSessionService，锁屏控件 + 通知栏媒体控制 + 蓝牙线控 |
| 音频闪避 | TTS 说话时音乐自动降到 10%（可在设置改），说完恢复 |
| 骑行模式 | 前台服务保活常驻通知；本地指令直执（播放/暂停/切歌/音量）；编辑距离≥70% 模糊匹配抗风噪识别偏差；指令执行只播提示音不啰嗦；可设进入时自动提音量 |
| 版本自动迭代 | 每次构建自动 versionCode+1、版本号第三段+1 |
| 内置更新 | 设置页「检查更新」/ 启动自动检查 GitHub Releases，发现新版本下载覆盖安装（同签名） |

## 使用方法

### 安装
把 `release\riding_app_vX.Y.Z_buildN.apk` 传到手机安装（需允许"未知来源"）。
首次打开允许「录音」「通知」权限。

### 配置大模型（让语音对话真正"智能"）
打开 App → 「设置」页：
- 接口地址：如 `https://api.deepseek.com/v1`
- API Key：你的 key
- 模型名：如 `deepseek-chat`
- 保存后回到「对话」页点麦克风即可。

不配置也能用：骑行模式下的本地语音指令（播放/暂停/切歌/音量）完全离线可用。

### 加歌
「音乐」页 → 「添加网址」粘贴音频直链，或「从本地选择」手机里的音频文件。

## 重新打包 / 修改后打包（自动迭代版本号）

双击运行 `build.bat` 即可：

```
[Version] 2 -> 3   (1.0.1 -> 1.0.2)
...
BUILD OK
File: ...\release\riding_app_v1.0.2_build3.apk
```

- 每次运行自动 `versionCode+1`、`versionName` 第三段 +1（记录在 `version.properties`）
- 产物输出到 `release\` 目录，带版本号命名，可直接安装
- 如需改 JDK/SDK/Gradle 路径，编辑 `build.bat` 顶部三行

### 内置更新（GitHub 自动发版）

仓库：`github.com/wuyuzhiren/RidingCompanion`

- **App 内置更新**：设置页「检查更新」或启动后自动检查 GitHub 最新 Release，发现新版本会提示下载并覆盖安装（同签名）。
- **GitHub Actions 自动发版**：`.github/workflows/build-release.yml` 会在每次 push 到 main 时，在云端自动构建 APK、用仓库内 `keystore/riding.jks.b64`（签名证书）签名，并发布到 GitHub Releases。
- 发布新版本流程 = **修改代码 → 双击 build.bat 本地出包 → push 到 GitHub** → 云端自动构建发版 → 手机端自动收到更新提示。
- 说明：签名证书以 base64 形式放在公开仓库中（个人自用 App 可接受；如需保密请改私有仓库并调整更新鉴权）。

## 工程结构

```
RidingCompanion/
├── build.bat               # 一键打包 + 自动版本迭代
├── version.properties      # 版本号（自动维护）
├── keystore/riding.jks     # 签名证书（密码 riding2026）
└── app/src/main/java/com/riding/companion/
    ├── MainActivity.kt              # 底部导航宿主
    ├── ui/                          # 对话/音乐/设置 Fragment + 适配器
    ├── audio/VoiceController.kt     # STT + TTS + 音频闪避
    ├── data/ChatEngine.kt           # OpenAI 兼容 SSE 流式对话
    ├── data/AppConfig.kt            # 配置
    ├── data/UpdateChecker.kt        # 内置更新（GitHub Releases）
    ├── music/                       # Media3 播放服务 + 控制器 + 曲库
    ├── control/SystemMediaControl.kt # 系统音量 + 跨 App 媒体控制
    └── cycling/                     # 骑行模式服务 + 本地指令路由(模糊匹配)
```

## 与完整方案差距（未包含项）

以下项需要硬件、专用云服务或原生 C 库，本次未内置，后续可按需迭代：

1. **离线唤醒词 + 声纹校验**（原方案的 sherpa-onnx/kws 引擎）：本版使用系统语音识别替代，无离线唤醒；如需"小智小智"离线唤醒需集成 onnxruntime 唤醒引擎（可迭代）。
2. **风噪 DSP（高通滤波/谱减/RNNoise/双麦波束）**：系统 SpeechRecognizer 无法拦截原始音频流做 DSP。要真正全链路降噪需自建录音+ASR 链路并集成 RNNoise C 库（可迭代）。
3. **云端 xiaozhi-server 后端 + 音乐 MCP 工具集**：本版为"自包含"实现（App 直连大模型 + 本地指令直执），未部署 xiaozhi-server。若要语音点歌需另接音乐 API 作为 MCP。
4. **硬件建议**（对骑行降噪效果最直接，请优先执行）：
   - 领夹麦/头盔麦加防风海绵+毛衣，装在下巴下方/衣领内侧，避开直吹
   - 挂耳式骨传导耳机（自带风噪抑制，骑行更安全）
   - 蓝牙耳机按键直接切歌/暂停，大风环境少用语音

## 技术栈

Kotlin · AndroidX · Material 3 · Media3 ExoPlayer · AGP 8.5.2 · Gradle 8.9 · JDK 17
minSdk 24 / targetSdk 35，release 已签名。
