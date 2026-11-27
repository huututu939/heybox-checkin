# R8 keep 规则（v1.8.1 起 release 启用；新增反射/JS 桥时先按 docs/r8-checklist.md 回归）
#
# 项目里没有 @JavascriptInterface、没有 Class.forName；设备指纹走 evaluateJavascript 回调，
# 小组件用显式 RemoteViews id。所以规则保持最小：只兜住 WebView 回调和 device / widget 包。

# WebView 里的求值与回调入口，别被裁掉
-keepclassmembers class * extends android.webkit.WebViewClient {
    public *;
}

# 设备指纹 SDK 的注入脚本与 WebView 流程
-keep class com.heybox.checkin.device.** { *; }

# 小组件 Provider 由系统按类名实例化
-keep class com.heybox.checkin.widget.*Provider { *; }

# 历史记录存的是手写 JSON key，字段名保留，防止以后换序列化库时踩坑
-keepclassmembers class com.heybox.checkin.data.RunRecord {
    <fields>;
}

-keepattributes Signature
