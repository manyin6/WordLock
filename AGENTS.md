# AGENTS.md · 单词锁 WordLock

## 定位
离线背单词 + 手机专注锁机的安卓应用。原生 Java，不用 Gradle，无网络权限、无账号、无第三方 SDK。

## 构建与测试
- 环境：JDK 17、Android SDK Platform 34、Build Tools 34.0.0、zip、Python 3。
- 构建：设置 `JAVA_HOME`、`ANDROID_SDK_ROOT` 后运行 `bash app/build.sh`。
  - 未设签名变量 → `app/build/WordLock-v<版本>-debug.apk`（自动生成调试密钥）。
  - 正式签名：设置 `WL_KEYSTORE`、`WL_KEY_ALIAS`、`WL_STORE_PASS`、`WL_KEY_PASS` → `app/build/WordLock-v<版本>.apk`。
- 测试：无框架的纯 Java 测试，命令见 README「无障碍反馈排查」末尾（`tests/*Test.java`）。

## 目录
- `app/src/com/wordlock/app/`：全部 Java 源码。
  - `Clock`：开机单调时钟锚定的可信时间；锁机、宽限期、每日限额都要用它，不要直接用 `System.currentTimeMillis()`。
  - `ExemptNames` + `LockService`：锁机时的紧急放行（默认拨号应用，或系统应用且包名命中电话/闹钟规则）。
  - `AppLock`：应用限时；`Plan`/`PlanReceiver`：定时锁机；`Docs`：应用内协议与隐私政策。
- `app/res/`：界面资源与图标；`app/assets/`：词库 TSV、例句、图片、第三方许可。
- `docs/`：Logo 与 README 配图；`tests/`：单元测试。

## 约定
- 发版时同步改版本号：`app/build.sh`（VERSION、--version-code）、`Perms.java`、`MainActivity.java` 关于文字、README、CHANGELOG。
- 严禁提交密钥、密码、令牌（`.gitignore` 已排除 `*.p12` `*.jks` `*.keystore` `*.apk` `app/build/`）。只公开证书指纹（见 SECURITY.md）。
- 0.6.5 起使用新签名；以后的版本必须继续用同一把密钥签名，否则用户无法覆盖升级。
- 第三方资源许可见 THIRD_PARTY_NOTICES.md；新增词库或图片要补许可。
- 安全问题走 SECURITY.md 的私密报告流程。

## 当前状态
- 最新版 0.6.5（versionCode 13），已发布 GitHub Release，附带 APK 和源码包。
- 已知限制：重启手机后再修改系统时间，仍可能影响计时；厂商系统可能自动关闭无障碍服务。
- README 配图是设计参考图，不是实机截图。
