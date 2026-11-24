package com.heybox.checkin.work

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 当前进程内一次执行的可见状态。
 *
 * 界面不再自己跑长流程，只观察这里；真正的执行由 [CheckInService] 承载。
 * 进程被杀时这份状态随进程一起消失，不会留下「永远执行中」的假象。
 */
object RunState {

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _stage = MutableStateFlow("")
    val stage: StateFlow<String> = _stage

    fun start(stage: String) {
        _stage.value = stage
        _running.value = true
    }

    fun stage(text: String) {
        if (_running.value) _stage.value = text
    }

    fun finish() {
        _running.value = false
        _stage.value = ""
    }
}
