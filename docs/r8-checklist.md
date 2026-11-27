# R8 回归清单（v1.8.1 起已开启）

release 构建从 v1.8.1 起 `isMinifyEnabled = true` / `isShrinkResources = true`，包体 8.9 MB → 1.7 MB，
已在真机回归登录（设备令牌）、签到、小组件、自检与更新。

这份清单保留给以后改依赖、加反射 / JS 桥或调 keep 规则时重跑；不是待办。

## 1. 先确认没有隐式反射

截至 v1.8.0 的结论：

- 没有 `@JavascriptInterface`：设备令牌走 `evaluateJavascript` 回调，不是 JS 桥
- 没有 `Class.forName` / `getDeclaredMethod`
- 桌面小组件用显式 `RemoteViews` id，不靠反射
- 唯一需要照顾的是 `DeviceTokenProvider` 里的 WebView 流程与 Retrofit/OkHttp 式的动态调用（本项目没有）

所以先只加最小 keep 规则，不要一上来抄一大段。

## 2. 草稿 keep 规则（`app/proguard-rules.pro`）

```proguard
# WebView 里的 JS 求值与回调入口，别被裁掉
-keepclassmembers class * extends android.webkit.WebViewClient {
    public *;
}

# 设备指纹 SDK 的注入脚本与 WebView 流程在 device 包，整体保留
-keep class com.heybox.checkin.device.** { *; }

# 小组件 Provider 由系统按类名实例化
-keep class com.heybox.checkin.widget.*Provider { *; }

# 序列化到 prefs 的 RunRecord 字段名不能改（历史记录按 key 读）
-keepclassmembers class com.heybox.checkin.data.RunRecord {
    <fields>;
}
```

`RunRecord` 没有用反射，字段名只影响自己写的 JSON key，理论上不受 R8 影响；留着是为了防止以后顺手改成
Gson/Moshi 时踩坑。

## 3. 构建配置（v1.8.1 已启用）

```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
}
```

## 4. 真机回归（按顺序，缺一不可）

1. 覆盖安装 R8 包，打开 App 不崩、首启说明正常
2. 退出登录 → 重新登录，**设备令牌能拿到**（这一步最容易炸，WebView 主线程、`fp-it.portal101.cn` 可达）
3. 手动执行一次签到，通知与记录正常
4. 添加 4×1 与 2×2 小组件，按钮能拉起 App / 执行
5. 杀掉进程再开，历史、定时、外观都还在
6. 应用内检查更新 → 下载 → 安装，`FileProvider` 正常
7. 检查 APK 体积变化（预期从 ~8.9 MB 降到 ~4–5 MB）

## 5. 出问题怎么退

- 先看 `adb logcat | grep -i "ClassNotFound\|NoSuchMethod\|NullPointer"`，八成是某个类被裁
- 用 `-keep class <包>.** { *; }` 定位到具体包，再缩小规则
- 回归不过就先把 `isMinifyEnabled` 改回 `false` 发版，R8 不影响功能

## 6. 不要做的事

- 不要在没跑完第 4 步的情况下推 `v*` tag
- 不要同时升 AGP / Kotlin / Compose BOM —— 出问题分不清是 R8 还是版本
