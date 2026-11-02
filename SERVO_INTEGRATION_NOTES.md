# Servo 双内核集成说明

本次修改以 `mafari-main.zip` 为基线，并将用户提供的 `servo-aarch64-android.aar` 放入 `app/libs/`。

## 使用方式
1. 打开设置 -> 浏览 -> 浏览器内核。
2. 选择 Android WebView 或 Servo（实验性）。
3. 切换会将当前标签页的 URL 在新内核中重新打开；不会迁移旧内核的完整历史记录或页面运行状态。
4. 新建标签页默认使用当前设置的内核。

## 当前边界
Servo AAR 目前提供的 Android API 只暴露基础导航、标题/URL/加载状态和 Surface 渲染回调。以下 Mafari 功能仍由 WebView 实现，切换到 Servo 后暂不可用或不会生效：
- Tampermonkey / 用户脚本与 document-start 注入
- Mafari 页面元素检查、广告元素长按标记
- WebView DownloadListener 下载接管
- WebView UA / 桌面站点 UA 切换
- WebView 页内查找
- WebAuthn / Passkey 的 WebView 专属配置
- WebView 的下拉滚动位置检测与动态底栏联动

Servo 当前只包含 `arm64-v8a` 原生库，因此目标设备必须支持 arm64。AAR 的 `minSdkVersion` 为 29，项目当前 minSdk 33 满足要求。

## 构建
项目仍使用原有 Gradle Wrapper、AGP、Kotlin 和 Android SDK 配置。需要安装项目要求的 Android SDK 37 / Build Tools 36.0.0，并使用 JDK 17 或兼容版本。
