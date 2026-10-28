package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.ui.text.uiText
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.ui.glass.GlassAlertDialog

@Composable
internal fun SettingsInfoButton(title: String, description: String) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(40.dp)) {
        Icon(Icons.Outlined.Info, contentDescription = uiText("Information about $title", "Información sobre $title"),
            tint = SettingsStyle.Description, modifier = Modifier.size(17.dp))
    }
    if (open) GlassAlertDialog(onDismissRequest = { open = false },
        title = { Text(title) }, text = { Text(description) },
        confirmButton = { TextButton(onClick = { open = false }) { Text(uiText("Got it", "Entendido")) } })
}

internal fun settingsHelp(title: String): String? = when (canonicalSettingTitle(title)) {
    "Orden de reproducción" -> uiText("Choose the order when starting a playlist or album. Original keeps its order; Shuffle randomizes upcoming songs; Best Mix analyzes tempo, key and energy to arrange up to 50 upcoming songs. Your selected song stays first. Dedicated Shuffle and Best Mix buttons keep their own behavior. Changing this setting does not interrupt the current queue.", "Elige el orden al iniciar una lista o un álbum. Original conserva su orden; Aleatorio mezcla las siguientes canciones; Mejor mezcla analiza ritmo, tonalidad y energía y organiza hasta las próximas 50 canciones. Se mantiene primero la canción que elijas. Los botones explícitos de Aleatorio y Mejor mezcla respetan su propia acción. Cambiar este ajuste no interrumpe la cola actual.")
    "Mejor mezcla" -> uiText("Arrange up to 50 upcoming songs by musical compatibility. Uses downloads or temporary low-quality audio; initial preparation can take time and use data. Analysis is saved for reuse. Does not enable or change transitions.", "Organiza hasta 50 canciones siguientes por compatibilidad musical. Usa archivos descargados o audio temporal de baja calidad; la primera preparación puede consumir datos y tardar. El análisis se guarda para reutilizarlo. No activa ni cambia el tipo de transición.")
    "Tipo de transición" -> uiText("Off plays songs one after another. Crossfade overlaps the end and beginning with volume changes. Adaptive analyzes audio to match tempo and structure, needs more processing, and falls back to a regular crossfade if preparation fails. Does not change queue order.", "Desactivada: reproduce una canción después de otra. Fundido cruzado: superpone el final y el inicio mediante cambios de volumen. Adaptativa: analiza el audio para preparar una transición según el ritmo y la estructura; necesita más procesamiento y usa un fundido normal si no consigue prepararla. No cambia el orden de la cola.")
    "Duración de la transición" -> uiText("For crossfade, controls overlap duration in seconds. For Adaptive Mix, sets the requested duration; analysis may adjust it to the music.", "En el fundido cruzado controla los segundos de superposición. En la mezcla adaptativa es la duración solicitada; el análisis puede ajustar la transición según la música.")
    "Volumen exponencial" -> uiText("Changes the NÜMA volume curve for finer control at low volume. Does not improve quality or normalize songs. Off by default.", "Cambia la curva del control de volumen de NÜMA para ofrecer más precisión al escuchar bajo. No aumenta la calidad ni normaliza las canciones. Está desactivado por defecto.")
    "Normalización de volumen" -> uiText("Reduces volume differences between songs when loudness data is available. Does not change system volume or guarantee identical loudness for every recording.", "Reduce las diferencias de volumen entre canciones cuando hay información de sonoridad disponible. No cambia el volumen del sistema ni garantiza que todas las grabaciones suenen idénticas.")
    "Ecualizador" -> uiText("Adjusts frequencies, gain and bass. Raising bands too much can distort the sound. Effects are unavailable with Max audio.", "Ajusta las frecuencias, la ganancia y los graves. Aumentar demasiado las bandas puede saturar el sonido. Los efectos no están disponibles en la ruta de audio Max.")
    "Calidad de transmisión", "Calidad con datos móviles" -> uiText("Controls requested streaming quality. Higher quality uses more data. Mobile data quality applies to metered networks; download quality is configured separately.", "Controla la calidad solicitada para escuchar en línea. Una calidad mayor utiliza más datos. La calidad con datos móviles se aplica a las redes con consumo medido; la calidad de descarga se configura por separado.")
    "Reproducción automática" -> uiText("Adds related songs when the queue ends. Independent of queue order and transitions. Disabling removes automatically added songs.", "Añade canciones relacionadas cuando se termina la cola. Es independiente del orden y de las transiciones. Al desactivarla se retiran las canciones añadidas automáticamente.")
    "Fuente de traducción", "Translation source" -> uiText("Google Translate needs Internet access. On-device translation downloads language models once and then works offline. Saved translations can be reused.", "Google Translate usa Internet para traducir. La traducción local necesita descargar modelos antes de poder funcionar sin conexión. Las traducciones guardadas pueden reutilizarse.")
    "Calidad de traducción", "Translation quality" -> uiText("Selects the on-device model when available. Larger models may use more storage, memory and time. This does not change Google Translate.", "Selecciona el modelo local cuando esta opción está disponible. Un modelo mayor puede requerir más almacenamiento, memoria y tiempo. No cambia Google Translate.")
    "Audio en caché", "Cached audio" -> uiText("Limits temporary audio storage used to replay songs without downloading them again. Cache may be cleared automatically; saved downloads are managed separately.", "Limita el espacio de audio temporal usado para volver a escuchar canciones sin descargarlas otra vez. La caché puede eliminarse automáticamente; las descargas guardadas se gestionan por separado.")
    "Partes sin música", "Non-music parts" -> uiText("Play everything, show a skip button, or automatically skip segments marked by SponsorBlock volunteers. Segments may be inaccurate or unavailable; the audio file is unchanged.", "Puedes reproducir todo, mostrar un botón para saltar o avanzar automáticamente por segmentos marcados por voluntarios de SponsorBlock. Puede haber errores o canciones sin segmentos; no modifica el archivo de audio.")
    else -> null
}
