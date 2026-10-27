package dev.sfg.numa.mobile.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.ui.text.uiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Static images share the square crop on every playlist surface. Motion files keep their animation. */
@Composable
internal fun rememberPlaylistCoverPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val current by rememberUpdatedState(onPicked)
    var pending by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberCoverPicker { uri ->
        val type = context.contentResolver.getType(uri).orEmpty()
        if (type.startsWith("video/") || type == "image/gif") current(uri) else pending = uri
    }
    pending?.let { uri ->
        PlaylistCoverCrop(uri, onDismiss = { pending = null }, onSave = { cropped ->
            pending = null
            current(cropped)
        })
    }
    return picker
}

@Composable
private fun PlaylistCoverCrop(uri: Uri, onDismiss: () -> Unit, onSave: (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failure by remember(uri) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var pan by remember(uri) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(uri) {
        runCatching { withContext(Dispatchers.IO) { decodeCover(context, uri) } }
            .onSuccess { bitmap = it }
            .onFailure { failure = "No se pudo abrir esta imagen." }
    }
    val configuration = LocalConfiguration.current
    val previewSide = minOf(configuration.screenWidthDp - 80, configuration.screenHeightDp - 260, 448).coerceAtLeast(64).dp
    CoverCropDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(uiText("Adjust cover", "Ajustar carátula")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(uiText("Drag and pinch to frame your image.", "Arrastra y pellizca para encuadrar la imagen."))
                Spacer(Modifier.height(12.dp))
                val image = bitmap
                if (image != null) {
                    val preview = remember(image) { image.asImageBitmap() }
                    Canvas(Modifier.size(previewSide).clipToBounds().onSizeChanged {
                        val ratio = if (viewport.width > 0) it.width.toFloat() / viewport.width else 1f
                        pan = clampCoverPan(image.width, image.height, it.width, zoom, pan * ratio)
                        viewport = it
                    }
                        .pointerInput(image, saving) {
                            if (saving) return@pointerInput
                            detectTransformGestures { _, movement, magnification, _ ->
                                zoom = (zoom * magnification).coerceIn(1f, 4f)
                                pan = clampCoverPan(image.width, image.height, viewport.width, zoom, pan + movement)
                            }
                        }) {
                        val scale = maxOf(size.width / image.width, size.height / image.height) * zoom
                        val width = (image.width * scale).toInt()
                        val height = (image.height * scale).toInt()
                        drawImage(preview,
                            dstOffset = IntOffset(((size.width - width) / 2 + pan.x).toInt(), ((size.height - height) / 2 + pan.y).toInt()),
                            dstSize = IntSize(width, height))
                    }
                    val changeZoom: (Float) -> Unit = {
                        zoom = it.coerceIn(1f, 4f)
                        pan = clampCoverPan(image.width, image.height, viewport.width, zoom, pan)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { changeZoom(zoom - 0.25f) }, enabled = !saving && zoom > 1f) {
                            Icon(Icons.Rounded.Remove, uiText("Zoom out", "Alejar"))
                        }
                        ThinGlassSlider(value = zoom, onValueChange = changeZoom, valueRange = 1f..4f,
                            enabled = !saving, modifier = Modifier.weight(1f), description = uiText("Cover zoom", "Zoom de la carátula"))
                        IconButton(onClick = { changeZoom(zoom + 0.25f) }, enabled = !saving && zoom < 4f) {
                            Icon(Icons.Rounded.Add, uiText("Zoom in", "Acercar"))
                        }
                    }
                    Text("${(zoom * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                } else if (failure == null) CircularProgressIndicator()
                failure?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(enabled = bitmap != null && viewport.width > 0 && !saving, onClick = {
                val image = bitmap ?: return@TextButton
                saving = true
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) {
                        val output = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
                        val scale = maxOf(1024f / image.width, 1024f / image.height) * zoom
                        val ratio = 1024f / viewport.width
                        val x = (1024 - image.width * scale) / 2 + pan.x * ratio
                        val y = (1024 - image.height * scale) / 2 + pan.y * ratio
                        AndroidCanvas(output).drawBitmap(image, null,
                            RectF(x, y, x + image.width * scale, y + image.height * scale), Paint(Paint.FILTER_BITMAP_FLAG))
                        val directory = File(context.cacheDir, "playlist-crops").apply { mkdirs() }
                        directory.listFiles().orEmpty().filter { System.currentTimeMillis() - it.lastModified() > 86400000L }.forEach { it.delete() }
                        val file = File(directory, "${UUID.randomUUID()}.jpg")
                        file.outputStream().use { check(output.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
                        output.recycle()
                        Uri.fromFile(file)
                    } }.onSuccess(onSave).onFailure { failure = "No se pudo guardar la imagen." }
                    saving = false
                }
            }) { Text(uiText("Save", "Guardar")) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(uiText("Cancel", "Cancelar")) } },
    )
}

internal fun clampCoverPan(width: Int, height: Int, side: Int, zoom: Float, pan: Offset): Offset {
    if (side <= 0) return Offset.Zero
    val scale = maxOf(side.toFloat() / width, side.toFloat() / height) * zoom
    val x = ((width * scale - side) / 2).coerceAtLeast(0f)
    val y = ((height * scale - side) / 2).coerceAtLeast(0f)
    return Offset(if (x == 0f) 0f else pan.x.coerceIn(-x, x), if (y == 0f) 0f else pan.y.coerceIn(-y, y))
}

private fun decodeCover(context: android.content.Context, uri: Uri): Bitmap {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val options = BitmapFactory.Options().apply {
        inSampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 2048) inSampleSize *= 2
    }
    val image = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("Invalid image")
    val orientation = runCatching { resolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } }.getOrNull()
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
        }
    }
    return if (matrix.isIdentity) image else Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrix, true)
}
