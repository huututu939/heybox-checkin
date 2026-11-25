package com.heybox.checkin.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.heybox.checkin.core.DeviceInfo
import com.heybox.checkin.core.HeyboxConfig
import com.heybox.checkin.core.SessionInfo
import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.data.SessionStore
import com.heybox.checkin.ui.theme.HeyboxTheme
import com.heybox.checkin.ui.theme.GlassBackdrop
import com.heybox.checkin.ui.theme.LocalBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.heybox.checkin.widget.Widget2x2Provider
import com.heybox.checkin.widget.Widget4x1Provider
import com.heybox.checkin.work.Scheduler

class MainActivity : ComponentActivity() {

    private val store by lazy { SessionStore(this) }
    private val resumeTick = mutableIntStateOf(0)
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * 「最近任务隐藏」：用户按 Home / 最近任务离开时把任务整个结束，多任务里就不留卡片。
     * EMUI 这类 ROM 不认 manifest 的 excludeFromRecents，只有主动移除任务才有效。
     * 注意：这不是 force-stop，闹钟不受影响；但未锁定卡片时系统可能连带回收进程。
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (skipHideOnLeave) {
            skipHideOnLeave = false
            return
        }
        if (store.hideFromRecents()) finishAndRemoveTask()
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
        skipHideOnLeave = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 系统默认会给手势条加一层半透明保护色（浅色主题下就是一条白边），关掉才真正沉浸。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPinFromIntent()
        setContent {
            var appearance by remember { mutableStateOf(store.appearance()) }
            val dark = when (appearance) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            // 状态栏图标的深浅必须跟着主题，不然浅色下时间是白的、看不见。
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            HeyboxTheme(darkTheme = dark) {
                val bgBackdrop = rememberLayerBackdrop()
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(bgBackdrop),
                    ) {
                        GlassBackdrop()
                    }
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalBackdrop provides bgBackdrop,
                    ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        App(
                        store = store,
                        appearance = appearance,
                        darkTheme = dark,
                        resumeTick = resumeTick.intValue,
                        onAppearanceChange = { mode ->
                            store.setAppearance(mode)
                            appearance = mode
                        },
                    )
                    }
                }
            }
        }
    }
    }

    /**
     * 仅 debug 包：`am start --es pin_widget 4x1` / `2x2` 直接请桌面把小组件钉到主屏，
     * 省去手动长按桌面找入口的步骤。release 包里这段不会执行。
     */
    private fun requestPinFromIntent() {
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        val kind = intent?.getStringExtra("pin_widget") ?: return
        val provider = if (kind == "2x2") Widget2x2Provider::class.java else Widget4x1Provider::class.java
        val manager = AppWidgetManager.getInstance(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, provider), null, null)
        }
    }

    companion object {
        /**
         * 从 App 内点开外部链接时置位一次：浏览器启动同样会触发 onUserLeaveHint，
         * 不跳过的话任务会被一起移除，用户从浏览器按返回就落到桌面而不是 App。
         */
        internal var skipHideOnLeave = false
    }
}

/** 设计走查用的假数据：最近七天里 19/20/22/23 完成，21 没记录，24 跳过，25 今天完成。 */
private fun previewRunHistory(): List<RunRecord> {
    fun at(daysAgo: Int): Long = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
        set(java.util.Calendar.HOUR_OF_DAY, 9)
        set(java.util.Calendar.MINUTE, 10)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun record(daysAgo: Int, executed: Boolean) = RunRecord(
        at = at(daysAgo),
        executed = executed,
        expDelta = if (executed) 90 else null,
        coinDelta = if (executed) 300 else 0,
        batteryDelta = if (executed) 1 else null,
        exp = 170,
        coin = 766,
        battery = 5,
        level = 3,
        maxExp = 200,
        signed = true,
        taskStates = mapOf(
            "sign" to "finish",
            "1" to "finish",
            "19" to "finish",
            "31" to "finish",
        ),
    )

    return listOf(
        record(6, true),
        record(5, true),
        record(3, true),
        record(2, true),
        record(1, false),
        record(0, true),
    )
}

@Composable
private fun App(
    store: SessionStore,
    appearance: String,
    darkTheme: Boolean,
    resumeTick: Int,
    onAppearanceChange: (String) -> Unit,
) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(store.load()) }
    // 设计走查用：debug 包可通过 `am start --ez preview_login true` 直接看登录页，不写任何会话。
    val previewLogin = remember {
        val activity = context as? ComponentActivity
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        debuggable && activity?.intent?.getBooleanExtra("preview_login", false) == true
    }
    val previewCode = remember {
        val activity = context as? ComponentActivity
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        debuggable && activity?.intent?.getBooleanExtra("preview_code", false) == true
    }
    val previewExpired = remember {
        val activity = context as? ComponentActivity
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        debuggable && activity?.intent?.getBooleanExtra("preview_expired", false) == true
    }
    val previewWelcome = remember {
        val activity = context as? ComponentActivity
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        debuggable && activity?.intent?.getBooleanExtra("preview_welcome", false) == true
    }
    var noticeAccepted by remember { mutableStateOf(store.acceptedNotice() >= NOTICE_VERSION) }
    // 设计走查用：debug 包带 `--ez preview_home true` 直接看首页，不写会话、不需要登录。
    val previewHome = remember {
        val activity = context as? ComponentActivity
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        debuggable && activity?.intent?.getBooleanExtra("preview_home", false) == true
    }
    val previewConfig = remember {
        HeyboxConfig(
            session = SessionInfo(heyboxId = "100000000", pkey = "", deviceId = ""),
            device = DeviceInfo(model = "JER-AN10", osVersion = "12"),
        )
    }
    val previewHistory = remember { previewRunHistory() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        if (previewWelcome || (!noticeAccepted && !previewHome && !previewLogin && !previewCode && !previewExpired)) {
            WelcomeScreen(
                onAccept = {
                    if (!previewWelcome) store.acceptNotice(NOTICE_VERSION)
                    noticeAccepted = true
                },
            )
        } else if ((config == null && !previewHome) || previewLogin) {
            LoginScreen(
                context = context,
                store = store,
                onLoggedIn = { config = it },
                previewCode = previewCode,
            )
        } else {
            HomeScreen(
                context = context,
                store = store,
                config = config ?: previewConfig,
                previewHistory = if (previewHome) previewHistory else null,
                appearance = appearance,
                darkTheme = darkTheme,
                resumeTick = resumeTick,
                previewExpired = previewExpired,
                onAppearanceChange = onAppearanceChange,
                onRelogin = {
                    // 只清凭据，保留定时与外观；重新登录后闹钟还在。
                    store.clearCredentials()
                    store.clearSessionExpired()
                    config = null
                },
                onLogout = {
                    Scheduler.cancel(context)
                    store.clear()
                    config = null
                },
            )
        }
    }
}
