# R8 keep 规则（在 r8-test 分支上启用，真机回归前不要合并）
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

-keepattributes Signature
