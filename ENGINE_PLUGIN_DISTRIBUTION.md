# Mafari 按需内核插件分发

## 目标

主 APK 不依赖 Servo AAR，也不携带 Servo DEX/so。用户在设置中选择 Servo 时，主应用才下载独立的 `servo-engine.apk`，校验 SHA-256 后放入应用私有目录，通过 `DexClassLoader` 加载。

## 构建与发布

1. 构建 Servo 插件 APK：

   ```bash
   ./scripts/package-servo-plugin.sh
   ```

   插件模块 `:servo-engine-plugin` 在构建时下载 Servo AAR，并将其代码及 JNI 库封装进独立 APK。主模块 `:app` 不再引用 Servo AAR。

2. 将 `dist/servo-engine/servo-engine.apk` 上传到 HTTPS 发布地址。
3. 记录 `servo-engine.apk.sha256` 中的 SHA-256。
4. 构建主应用时注入固定的下载地址和校验值：

   ```bash
   ./gradlew :app:assembleRelease \
     -PservoPluginUrl=https://your-host.example/releases/servo-engine.apk \
     -PservoPluginSha256=<64位SHA256>
   ```

   生产版本应将 URL 与 SHA-256 固定在受信任的构建产物中，不要从不可信的远端清单无校验地更新它们。

## 运行流程

- WebView 是默认内核，无需下载。
- 选择 Servo：先检查私有目录是否已有插件；没有则下载、校验 APK、提取当前 ABI 的 `.so`，再由 `DexClassLoader` 加载。
- 下载失败或校验失败时，不更改已选择的内核，用户仍可使用 WebView。
- 插件卸载只删除 Mafari 私有目录中的文件，不涉及 Android 系统安装包。

## 安全与兼容性

- 仅允许 HTTPS 下载，并要求构建时固定 SHA-256。
- 插件 APK 不作为 Android 应用安装；它只作为代码容器由 Mafari 私有 ClassLoader 加载。
- 当前插件构建面向 `arm64-v8a` / `armeabi-v7a`，最终可用 ABI 取决于 Servo AAR 实际包含的 JNI 库。
- Android 动态代码加载受平台安全策略约束。插件只能来自可信发布源；不要允许用户任意导入 APK/JAR。
- Servo AAR 的构造函数与回调 API 仍需用目标 Servo release 实机验证。反射适配器刻意隔离 API，但无法替代真实设备测试。
