package com.heybox.checkin.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.random.Random

data class RunReport(
    val expDelta: Long?,
    val coinDelta: Long,
    val batteryDelta: Long?,
    val execution: Map<String, String>,
    val snapshot: TaskSnapshot,
)

/**
 * 每日任务编排，对齐上游 runDaily：
 * 检查 → 签到（未确认到账就中止）→ 三个分享任务（show / tap / success）→ 最终校验。
 * 任一环节失败都抛出，失败信息给界面直接展示。
 */
class TaskRunner(
    private val client: HeyboxClient,
    private val account: String,
    private val onStage: (String) -> Unit = {},
    private val onLog: (String) -> Unit = {},
    /** 允许执行的分享任务 id；不在集合里的直接跳过，签到不受影响。 */
    private val enabledShares: Set<String> = TaskParser.SHARE_IDS.toSet(),
) {

    private val delays = longArrayOf(2000, 3000, 5000, 10000)

    suspend fun inspect(): Inspection = withContext(Dispatchers.IO) {
        val snapshot = TaskParser.snapshot(client.tasksResult(), account)
        val sign = TaskParser.signState(client.signStateResult())
        Inspection(snapshot, TaskParser.signedToday(sign))
    }

    suspend fun runDaily(initial: Inspection? = null): RunReport = withContext(Dispatchers.IO) {
        val execution = LinkedHashMap<String, String>()
        fun record(id: String, action: String) {
            execution[id] = action
        }

        onStage("inspect")
        // 定时任务进来时已经查过一次，复用这次结果，少打一整轮接口。
        val before = initial ?: inspect()
        var current = before.snapshot
        var expDelta: Long? = 0L
        fun absorb(value: TaskSnapshot) {
            val gain = experienceDelta(current, value)
            expDelta = if (expDelta == null || gain == null) null else expDelta!! + gain
            current = value
        }

        if (!before.signed) {
            record("sign", "executed")
            onStage("sign")
            client.signResult()
            var verified = false
            for (wait in delays) {
                delay(wait + Random.nextLong(0, 1000))
                val state = TaskParser.signState(client.signStateResult())
                if (TaskParser.signedToday(state)) {
                    verified = true
                    break
                }
            }
            if (!verified) throw IllegalStateException("签到未确认到账，任务已停止")
            onLog("签到完成")
            absorb(TaskParser.snapshot(client.tasksResult(), account))
        } else {
            record("sign", "skipped")
            onLog("今天已签到")
        }

        for (id in TaskParser.SHARE_IDS) {
            val name = TaskParser.SHARE_NAMES[id] ?: id
            if (id !in enabledShares) {
                record(id, "disabled")
                onLog("$name 已关闭，跳过")
                continue
            }
            onStage("share_$id")
            if (current.stateOf(id) == "finish") {
                record(id, "skipped")
                onLog("$name 已完成，跳过")
                continue
            }
            val content = selectShare(id)
            val sessionId = UUID.randomUUID().toString()
            for (action in listOf("show", "tap", "success")) {
                if (action == "show") record(id, "executed")
                client.reportResult(
                    sessionId,
                    TaskParser.shareEventJson(action, content, sessionId, System.currentTimeMillis() / 1000),
                )
                // 三步之间插入随机停顿：固定 2 秒的节奏本身就是可识别特征。
                when (action) {
                    "show" -> delay(1200L + Random.nextLong(0, 2500))
                    "tap" -> delay(3000L + Random.nextLong(0, 4000))
                }
            }
            var settled = false
            for (wait in delays) {
                delay(wait + Random.nextLong(0, 1000))
                absorb(TaskParser.snapshot(client.tasksResult(), account))
                if (current.stateOf(id) == "finish") {
                    settled = true
                    break
                }
            }
            if (!settled) throw IllegalStateException("$name 上报后未结算，请稍后重新检查")
            onLog("$name 完成")
        }

        onStage("verify")
        val after = inspect()
        absorb(after.snapshot)
        if (!after.signed || TaskParser.SHARE_IDS.any { it in enabledShares && after.snapshot.stateOf(it) != "finish" }) {
            throw IllegalStateException("最终状态未通过检查，可能跨日或任务状态已变化")
        }

        RunReport(
            expDelta = expDelta,
            coinDelta = after.snapshot.coin - before.snapshot.coin,
            batteryDelta = if (before.snapshot.battery == null || after.snapshot.battery == null) {
                null
            } else {
                after.snapshot.battery - before.snapshot.battery
            },
            execution = execution,
            snapshot = after.snapshot,
        )
    }

    private fun selectShare(id: String): ShareContent {
        if (id == "1") {
            val feed = client.webResult(
                "/bbs/app/feeds",
                mapOf("offset" to "0", "limit" to "20", "pull" to "1", "last_pull" to "1"),
            )
            // 每天分享同一条帖子是最明显的机器特征，候选里随机挑。
            val post = TaskParser.posts(feed).take(20).randomOrNull()
                ?: throw IllegalStateException("没有找到可分享的帖子")
            return ShareContent.Link(post.first, post.second)
        }

        val games = TaskParser.games(client.webResult("/game/all_recommend/v2"))
        if (games.isEmpty()) throw IllegalStateException("没有找到可分享的游戏")
        if (id == "19") {
            val game = games.take(10).random()
            return ShareContent.GameDetail(game.appId, game.hSrc)
        }
        if (id != "31") throw IllegalStateException("不支持的任务")

        for (game in games.take(6).shuffled()) {
            val comments = client.webResult(
                "/bbs/app/link/game/comments",
                mapOf("appid" to game.appId, "api_version" to "4", "offset" to "0", "limit" to "20"),
            )
            val review = TaskParser.reviews(comments).take(8).randomOrNull() ?: continue
            val detail = client.webResult(
                "/game/comment/share_data/",
                mapOf("appid" to game.appId, "userid" to review.first),
            )
            val linkId = TaskParser.reviewLinkId(detail)
            if (linkId != null) return ShareContent.GameComment(linkId)
        }
        throw IllegalStateException("没有找到可分享的游戏评价")
    }

    private fun experienceDelta(before: TaskSnapshot, after: TaskSnapshot): Long? {
        if (before.level == null || after.level == null) return null
        var delta = after.exp - before.exp
        if (after.level == before.level + 1 && before.maxExp != null && before.exp <= before.maxExp) {
            delta += before.maxExp
        } else if (after.level != before.level) {
            return null
        }
        return if (delta >= 0) delta else null
    }
}
