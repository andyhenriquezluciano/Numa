package dev.sfg.numa.mobile.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class QueueDragHandle(val modifier: Modifier, val active: Boolean, val row: (String) -> Modifier)

@Composable
internal fun rememberQueueDrag(
    list: LazyListState, queueIds: List<String>, currentIndex: Int, enabled: Boolean,
    onMove: (Int, Int) -> Unit,
): QueueDragHandle {
    val latestIds by rememberUpdatedState(queueIds)
    val latestIndex by rememberUpdatedState(currentIndex)
    val latestMove by rememberUpdatedState(onMove)
    val canDrag by rememberUpdatedState(enabled)
    var source by remember { mutableStateOf<String?>(null) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var grabOffset by remember { mutableFloatStateOf(0f) }
    var target by remember { mutableStateOf<String?>(null) }
    var initialIds by remember { mutableStateOf(emptyList<String>()) }
    var initialIndex by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    fun updateTarget() {
        target = list.layoutInfo.visibleItemsInfo.filter { it.key.toString().startsWith("upcoming:") }
            .minByOrNull { kotlin.math.abs(pointerY - (it.offset + it.size / 2f)) }?.key?.toString()
    }
    val modifier = Modifier.pointerInput(list) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull {
                down.position.y >= it.offset && down.position.y < it.offset + it.size && it.key.toString().startsWith("upcoming:")
            }
            if (!canDrag || item == null) return@awaitEachGesture
            var cancelled = false
            var last = down
            withTimeoutOrNull(500L) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        cancelled = true
                        break
                    }
                    last = change
                }
            }
            if (cancelled || !last.pressed) return@awaitEachGesture
            source = item.key.toString()
            target = source
            pointerY = last.position.y
            grabOffset = pointerY - item.offset
            initialIds = latestIds.toList()
            initialIndex = latestIndex
            last.consume()
            val scrolling = scope.launch {
                while (source != null) {
                    val bottom = list.layoutInfo.viewportEndOffset
                    val step = when {
                        pointerY < 48 -> -16f
                        pointerY > bottom - 48 -> 16f
                        else -> 0f
                    }
                    if (step != 0f) { list.scrollBy(step); updateTarget() }
                    delay(16)
                }
            }
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    pointerY = change.position.y
                    updateTarget()
                    if (!change.pressed) {
                        if (canDrag && initialIds == latestIds && initialIndex == latestIndex) {
                            queueDragMove(source, target, initialIndex)?.let { (from, to) -> latestMove(from, to) }
                        }
                        break
                    }
                }
            } finally {
                scrolling.cancel()
                source = null
                target = null
            }
        }
    }
    return QueueDragHandle(modifier, source != null) { key ->
        Modifier.zIndex(if (source == key) 1f else 0f).graphicsLayer {
            val row = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
            translationY = if (source == key && row != null) pointerY - grabOffset - row.offset else 0f
            alpha = if (source == key) 0.8f else 1f
            scaleX = if (source == key) 1.02f else 1f
            scaleY = scaleX
        }
    }
}
internal fun queueDragMove(source: String?, target: String?, currentIndex: Int): Pair<Int, Int>? {
    fun offset(key: String?): Int? = key?.takeIf { it.startsWith("upcoming:") }
        ?.substringAfter("upcoming:")?.substringBefore(':')?.toIntOrNull()?.takeIf { it >= 0 }
    val from = offset(source) ?: return null
    val to = offset(target) ?: return null
    return if (from == to) null else (currentIndex + 1 + from) to (currentIndex + 1 + to)
}

