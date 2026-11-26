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
import com.heybox.checkin.data.Failure
import com.heybox.checkin.data.FailureKind
import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.data.SessionStore
import com.heybox.checkin.data.classifyFailure
import com.heybox.checkin.data.missStreak
import kotlinx.coroutines.CancellationException
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
        // 同一实例重复收到 start 时，不要用「准备中」覆盖正在跑的那次进度。
        startForeground(SERVICE_ID, Notifications.serviceNotification(this, RunState.stage.value.ifEmpty { "准备中…" }))
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

    /**
     * 失败也记一笔，记录页才能说清哪天为什么没签上。
     * 今天已经成功过就不再写入：别把一次手动重试的失败盖在成功记录上。
     */
    private fun recordFailure(store: SessionStore, message: String, kind: FailureKind) {
        val today = todayRun(store)
        if (today?.signed == true) return
        val failure = RunRecord.failure(System.currentTimeMillis(), message, kind)
        store.saveRun(today?.let { failure.withBalanceOf(it) } ?: failure)
    }

    /** 本次只是发现「今天已完成」：余额按最新刷新，增量沿用今天已记录的那次。 */
    private fun mergeBalance(store: SessionStore, fresh: RunRecord): RunRecord =
        todayRun(store)?.withBalanceOnly(fresh) ?: fresh

    private suspend fun runDaily(force: Boolean) {
        val store = SessionStore(this)
        if (!RunLock.tryAcquire()) {
            // 另一次执行持有锁，它自己会负责排下一次。这里只是重复启动，
            // 既不能覆盖它的闹钟，也不该发一条「签到失败」通知。
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        RunState.start("检查今日状态…")
        try {
            execute(store, force)
        } finally {
            RunLock.release()
            RunState.finish()
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
            onStage = { stage ->
                val text = stageText(stage)
                RunState.stage(text)
                Notifications.updateService(this, text)
            },
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
        } catch (e: CancellationException) {
            // 服务被系统回收 / 主动停止：把取消继续往上抛，不要报成签到失败。
            throw e
        } catch (e: SessionExpiredException) {
            val failure = classifyFailure(e)
            store.markSessionExpired()
            store.setLastResult("登录已过期：${failure.message}")
            recordFailure(store, failure.message, failure.kind)
            finish(store, false, failure.message, retry = false, failure = failure)
        } catch (e: Exception) {
            val failure = classifyFailure(e)
            store.setLastResult("失败：${failure.message}")
            recordFailure(store, failure.message, failure.kind)
            // 手动点「立即重试」时不再自动重试，避免连点造成的循环；
            // 人机验证重试多少次都过不去，也直接省掉这次重试预算。
            val retry = !force && failure.kind != FailureKind.CAPTCHA
            finish(store, false, failure.message, retry = retry, failure = failure)
        }
    }

    /**
     * @param retry 失败时是否用当天重试预算再试一次（10–25 分钟后），预算用完就排到明天。
     * @param failure 失败分类；连续漏签天数会把今天这次算进去，≥2 天时通知会响一声。
     */
    private fun finish(
        store: SessionStore,
        success: Boolean,
        text: String,
        retry: Boolean = false,
        failure: Failure? = null,
    ) {
        // 必须在 recordFailure 之后算，否则今天这次还没落库，连续天数会少一天
        val streak = if (success) 0 else missStreak(store.history())
        Notifications.result(this, success, text, hint = failure?.hint, streak = streak)
        when {
            success -> {
                store.resetRetry()
                Scheduler.scheduleNext(this, skipToday = true)
            }
            retry && store.takeRetry() -> {
                val at = System.currentTimeMillis() + Random.nextLong(600_000L, 1_500_000L)
                val ok = Scheduler.schedule(this, at)
                store.setNextTrigger(if (ok) at else 0L)
            }
            else -> Scheduler.scheduleNext(this, skipToday = true)
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

        fun start(context: Context, force: Boolean = false) {
            val intent = Intent(context, CheckInService::class.java)
            if (force) intent.putExtra(EXTRA_FORCE, true)
            context.startForegroundService(intent)
        }
    }
}
