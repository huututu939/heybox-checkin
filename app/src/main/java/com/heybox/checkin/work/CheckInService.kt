package com.heybox.checkin.work

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.heybox.checkin.core.HeyboxClient
import com.heybox.checkin.core.SessionExpiredException
import com.heybox.checkin.core.TaskParser
import com.heybox.checkin.core.TaskRunner
import com.heybox.checkin.widget.Widgets
import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 前台服务承载真正的签到流程：整个流程要几十秒到两分钟（要轮询结算），
 * 放在 BroadcastReceiver 的 goAsync 里会被系统掐断，所以用 dataSync 前台服务。
 */
class CheckInService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(SERVICE_ID, Notifications.serviceNotification(this, "准备中…"))
        val force = intent?.getBooleanExtra(EXTRA_FORCE, false) == true
        if (job?.isActive != true) job = scope.launch { runDaily(force) }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun todayRun(store: SessionStore): RunRecord? =
        store.loadRun()?.takeIf { Scheduler.isSameLocalDay(it.at, System.currentTimeMillis()) }

    /** 本次有真正执行：同一天多次执行时把增量累加。 */
    private fun mergeRun(store: SessionStore, fresh: RunRecord): RunRecord =
        todayRun(store)?.withAnotherRun(fresh) ?: fresh

    /** 本次只是发现「今天已完成」：余额按最新刷新，增量沿用今天已记录的那次。 */
    private fun mergeBalance(store: SessionStore, fresh: RunRecord): RunRecord =
        todayRun(store)?.withBalanceOnly(fresh) ?: fresh

    private suspend fun runDaily(force: Boolean) {
        val store = SessionStore(this)
        if (!RunLock.tryAcquire()) {
            // 另一次执行持有锁，它自己会负责排下一次，这里不要覆盖它的闹钟。
            finish(store, false, "已有一次任务正在执行，本次跳过", reschedule = false)
            return
        }
        try {
            execute(store, force)
        } finally {
            RunLock.release()
        }
    }

    private suspend fun execute(store: SessionStore, force: Boolean) {
        val config = store.load()
        if (config == null) {
            finish(store, false, "还没有登录小黑盒账号，打开 App 登录一次再开启定时")
            return
        }

        val runner = TaskRunner(
            client = HeyboxClient(config),
            account = config.session.heyboxId,
            onStage = { stage -> Notifications.updateService(this, stageText(stage)) },
            enabledShares = store.enabledShares(),
        )

        try {
            // 今天已经全部完成就不重复请求：既省事，也少留一次可被统计的请求痕迹。
            val inspection = runner.inspect()
            val enabled = store.enabledShares()
            val done = inspection.signed &&
                TaskParser.SHARE_IDS.filter { it in enabled }.all { inspection.snapshot.stateOf(it) == "finish" }
            if (done) {
                store.setLastResult("今天已完成，跳过")
                store.markRanToday()
                store.saveRun(mergeBalance(store, RunRecord.from(inspection.snapshot, true)))
                finish(store, true, "今天已完成，跳过")
                return
            }
            val report = runner.runDaily(inspection)
            val text = buildString {
                append("经验 +${report.expDelta?.toString() ?: "?"}")
                append(" · H币 +${report.coinDelta}")
                report.batteryDelta?.let { append(" · 电池 +$it") }
                val executed = report.execution.filterValues { it == "executed" }.keys
                if (executed.isNotEmpty()) append("\n本次执行：${executed.joinToString("、") { name(it) }}")
            }
            store.setLastResult(text)
            store.markRanToday()
            store.clearSessionExpired()
            store.saveRun(mergeRun(store, RunRecord.from(report.snapshot, true, report)))
            finish(store, true, text)
        } catch (e: SessionExpiredException) {
            store.markSessionExpired()
            store.setLastResult("登录已过期：${e.message}")
            finish(store, false, "登录已过期，打开 App 重新登录", retry = false)
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            store.setLastResult("失败：$message")
            // 手动点「立即重试」时不再自动重试，避免连点造成的循环。
            finish(store, false, message, retry = !force)
        }
    }

    /**
     * @param retry 失败时是否用当天重试预算再试一次（10–25 分钟后），预算用完就排到明天。
     * @param reschedule false 表示这次没有真正执行，不应该覆盖另一次执行排好的闹钟。
     */
    private fun finish(
        store: SessionStore,
        success: Boolean,
        text: String,
        retry: Boolean = false,
        reschedule: Boolean = true,
    ) {
        Notifications.result(this, success, text)
        if (reschedule) {
            when {
                success -> {
                    store.resetRetry()
                    Scheduler.scheduleNext(this, skipToday = true)
                }
                retry && store.takeRetry() -> {
                    val at = System.currentTimeMillis() + Random.nextLong(600_000L, 1_500_000L)
                    store.setNextTrigger(at)
                    Scheduler.schedule(this, at)
                }
                else -> Scheduler.scheduleNext(this, skipToday = true)
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        Widgets.refresh(this)
        stopSelf()
    }

    private fun stageText(stage: String): String = when {
        stage == "inspect" -> "检查今日状态…"
        stage == "sign" -> "正在签到…"
        stage == "verify" -> "核对结果…"
        stage.startsWith("share_") -> "正在做${name(stage.removePrefix("share_"))}…"
        else -> stage
    }

    private fun name(id: String): String = when (id) {
        "sign" -> "签到"
        "1" -> "分享帖子"
        "19" -> "分享游戏"
        "31" -> "分享游戏评价"
        else -> id
    }

    companion object {
        const val ACTION_RETRY = "com.heybox.checkin.RETRY"
        const val EXTRA_FORCE = "force"
        private const val SERVICE_ID = 10

        fun start(context: Context) {
            val intent = Intent(context, CheckInService::class.java)
            context.startForegroundService(intent)
        }
    }
}
