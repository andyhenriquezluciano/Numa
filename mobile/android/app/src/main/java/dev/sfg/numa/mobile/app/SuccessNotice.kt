package dev.sfg.numa.mobile.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class SuccessNotice(private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow("")
    val state = mutable.asStateFlow()
    private val top = MutableStateFlow(false)
    val atTop = top.asStateFlow()
    private var dismissal: Job? = null
    fun show(message: String, atTop: Boolean = false) {
        dismissal?.cancel()
        top.value = atTop
        mutable.value = message
        dismissal = scope.launch { delay(3_000); mutable.value = "" }
    }
    fun dismiss() { dismissal?.cancel(); mutable.value = "" }
}
