package dev.sfg.numa.mobile.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import android.os.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal data class GlassSnapshot(val image: ImageBitmap, val origin: Offset, val scale: Float)
internal val LocalGlassSnapshot = staticCompositionLocalOf<GlassSnapshot?> { null }

/** Cross-window textures follow the source scene while a menu is visible. */
@Composable
internal fun rememberModalGlassSnapshot(open: Boolean): GlassSnapshot? {
    val scene = LocalGlassScene.current
    var snapshot by remember(scene, open) { mutableStateOf<GlassSnapshot?>(null) }
    DisposableEffect(scene, open) {
        if (open && scene != null) scene.snapshotConsumers++
        onDispose {
            if (open && scene != null && --scene.snapshotConsumers == 0) scene.snapshotCache.clear()
        }
    }
    LaunchedEffect(scene, open) {
        if (open && scene != null) {
            while (isActive) {
                withFrameNanos { }
                if (scene.sceneRecorded) {
                    snapshot = scene.snapshotCache.read(scene)
                }
                delay(150)
            }
        }
    }
    return snapshot
}

/** Nested popups share one readback; unchanged scenes reuse their existing texture. */
internal class GlassSnapshotCache {
    private var pixelsImage: ImageBitmap? = null
    private var pixelMap: PixelMap? = null

    fun pixels(snapshot: GlassSnapshot): PixelMap {
        if (snapshot.image !== pixelsImage) {
            pixelMap = snapshot.image.toPixelMap()
            pixelsImage = snapshot.image
        }
        return checkNotNull(pixelMap)
    }
    private val mutex = Mutex()
    private var value: GlassSnapshot? = null
    private var revision = -1L
    private var capturedAt = 0L

    suspend fun read(scene: GlassScene): GlassSnapshot = mutex.withLock {
        val now = SystemClock.uptimeMillis()
        value?.takeIf { revision == scene.sceneRevision || now - capturedAt < 150L }?.let { return@withLock it }
        val version = scene.sceneRevision
        GlassSnapshot(scene.sceneSample.toImageBitmap(), scene.sceneOrigin, scene.downscale).also {
            value = it
            revision = version
            capturedAt = now
        }
    }

    fun clear() { value = null; revision = -1L; pixelsImage = null; pixelMap = null }
}
