# LiteList

依据仓库 PRD V1.0 开发的原生 Android 本地待办应用。当前仅实现基础任务切片；完整范围与状态见 [需求拆解](REQUIREMENTS.md)。

## 构建

- JDK 17，Android SDK API 36、Build Tools 35.0.0 或更新版本；最低运行系统 API 31。
- 在本机配置 `ANDROID_HOME` 或 `local.properties` 的 `sdk.dir`。不要提交 `local.properties`。
- Windows：`gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
- macOS/Linux：`./gradlew :app:testDebugUnitTest :app:assembleDebug`
- Debug APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

2026-11-17 的本地验证：Gradle 8.13、JDK 17.0.21、Android SDK API 36 下，两项领域单元测试通过，Debug APK 构建成功。此验证不等于 PRD 第 12 章的功能、真机或性能验收。
