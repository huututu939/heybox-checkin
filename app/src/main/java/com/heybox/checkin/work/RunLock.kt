package com.heybox.checkin.work

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 进程内互斥：闹钟、前台服务兜底、界面「立即执行一次」可能同时发起，
 * 不加锁会出现两次签到请求叠加（对官方来说就是异常高频行为）。
 */
object RunLock {
    private val running = AtomicBoolean(false)

    fun tryAcquire(): Boolean = running.compareAndSet(false, true)

    /** 界面点「立即执行」前先看一眼，避免明知有人在跑还去拉起服务。 */
    fun isBusy(): Boolean = running.get()

    fun release() {
        running.set(false)
    }
}
