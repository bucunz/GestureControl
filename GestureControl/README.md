# 手势控屏 GestureControl

用摄像头识别手势来操作手机：返回、回桌面、最近任务、滚动页面，以及**移动光标 + 捏合点击**。

技术栈：**CameraX**（采集）+ **MediaPipe Gesture Recognizer**（识别）+ **AccessibilityService**（执行）。

---

## 一、三种拿到 APK 的方式

### 方式 A：GitHub Actions 云端构建（推荐，本机不用装环境）

1. 把整个项目推到你的 GitHub 仓库
2. 打开仓库 → **Actions** → `Build Debug APK` → **Run workflow**
3. 约 5–10 分钟后，在 Artifacts 里下载 `GestureControl-debug`，解压即得 APK
4. 传到手机安装

工作流会自动下载 Android SDK、Gradle 和手势模型，你什么都不用配。

### 方式 B：Android Studio 本地构建

1. 打开 Android Studio（Koala / Ladybug 及以上）→ `File > Open` 选本项目目录
2. 首次同步会自动下载 Gradle 与依赖，等待完成
3. 先执行一次模型下载（二选一）：
   ```bash
   bash scripts/download_model.sh
   ```
   或直接把 `hand_landmarker.task` 放进 `app/src/main/assets/`
4. `Build > Build Bundle(s)/APK(s) > Build APK(s)`
5. 产物：`app/build/outputs/apk/debug/app-debug.apk`

> 首次打开时 App 也会自动用系统下载器补下模型，但手动放好更省事。

### 方式 C：命令行

```bash
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleDebug          # 若无 wrapper jar，用本机 gradle 8.7 亦可
```

---

## 二、安装后必须做的三步授权

系统不允许 App 自己开这些开关，必须手动：

1. **摄像头**：App 内点「去开启」→ 允许
2. **悬浮窗**：跳转到「显示在其他应用上层」→ 打开开关
3. **无障碍**：跳转到无障碍设置 → 找到 **手势控屏** → 打开
   （路径：设置 → 无障碍/辅助功能 → 已下载的服务 → 手势控屏）

三步都显示「已授权」后，点主界面的**开启手势控屏**。

---

## 三、手势对照表

| 手势 | 动作 |
|---|---|
| ✊ 握拳 `Closed_Fist` | 返回 |
| ✋ 张开手掌 `Open_Palm` | 回到桌面 |
| ✌️ 剪刀手 `Victory` | 最近任务 |
| 👍 点赞 `Thumb_Up` | 向下滚动 |
| 👎 点踩 `Thumb_Down` | 向上滚动 |
| ☝️ 食指伸出 `Pointing_Up` | 进入光标模式，移动手指即移动光标 |
| 🤏 拇指与食指捏合 | 点击；捏住 0.6s 以上 = 长按 |

防误触规则：**连续 3 帧**识别到同一手势才执行，且同一动作有 **900ms** 冷却。

---

## 四、代码结构

```
app/src/main/java/com/gesture/control/
├── MainActivity.kt                 权限引导、开关、日志
├── CameraGestureService.kt         前台服务：CameraX 采集 + 推理调度
├── GestureRecognizerHelper.kt      MediaPipe 封装
├── GestureMapper.kt                手势→动作，防抖 + 冷却 + 光标计算
├── GestureAccessibilityService.kt  真正执行返回/点击/滑动
├── OverlayController.kt            悬浮光标
├── Prefs.kt                        灵敏度、冷却、前后摄像头等配置
└── StopServiceReceiver.kt          通知栏停止按钮
```

---

## 五、常见问题

**Q：手势识别不灵敏？**
- 保持手掌在画面中央、光线充足，避免复杂背景
- 手离摄像头 30–60cm 效果最好
- 调整 `Prefs.sensitivity`（默认 1.2，调大更灵敏）和 `Prefs.cooldownMs`

**Q：耗电太快？**
摄像头常开 + 实时推理是耗电大户。当前采集分辨率已压到 480×640。建议随用随开，不用就点停止。

**Q：某些 App 里点击失效？**
Android 的无障碍 `dispatchGesture` 在部分游戏/视频/自定义渲染界面上不生效，这是系统限制。

**Q：想改手势映射？**
直接改 `GestureMapper.kt` 里 `when (name)` 那一段，MediaPipe 内置类别为：
`None / Closed_Fist / Open_Palm / Pointing_Up / Thumb_Down / Thumb_Up / Victory / ILoveYou`

**Q：怎么训练自定义手势？**
MediaPipe 支持自定义分类器。把训练好的 `.task` 模型替换 `hand_landmarker.task` 即可，
`GestureMapper` 中按新的 `categoryName()` 加分支。

**Q：想上架应用商店？**
无障碍服务用途必须在商店页面明确声明为「为行动不便用户提供免触控操作」，否则会被拒审。

---

## 六、已知限制

- 无障碍服务无法由 App 自动开启（系统强制）
- 摄像头常开显著耗电
- 暗光、快速移动时识别率下降
- 不支持跨应用的全局手势（只能在无障碍框架允许范围内操作）
