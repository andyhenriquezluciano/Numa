package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.ui.text.uiText
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
    IconButton(onClick = { open = true }, modifier = Modifier.size(40.dp), shape = MaterialTheme.shapes.small) {
        Icon(Icons.Outlined.Info, contentDescription = uiText("Information about $title", "Información sobre $title"),
            tint = SettingsStyle.Description, modifier = Modifier.size(17.dp))
    }
    if (open) GlassAlertDialog(onDismissRequest = { open = false },
        title = { Text(title) }, text = { Text(description) },
        confirmButton = { TextButton(onClick = { open = false }, shape = MaterialTheme.shapes.small) { Text(uiText("Got it", "Entendido")) } })
}

internal fun settingsHelp(title: String): String? = when (canonicalSettingTitle(title)) {
    "Tipo de transición" -> uiText("Off plays songs one after another. Crossfade overlaps the end and beginning with volume changes. Adaptive analyzes audio to match tempo and structure, needs more processing, and falls back to a regular crossfade if preparation fails. Does not change queue order.", "Desactivada: reproduce una canción después de otra. Fundido cruzado: superpone el final y el inicio mediante cambios de volumen. Adaptativa: analiza el audio para preparar una transición según el ritmo y la estructura; necesita más procesamiento y usa un fundido normal si no consigue prepararla. No cambia el orden de la cola.")
    "Duración de la transición" -> uiText("For crossfade, controls overlap duration in seconds. For Adaptive Mix, sets the requested duration; analysis may adjust it to the music.", "En el fundido cruzado controla los segundos de superposición. En la mezcla adaptativa es la duración solicitada; el análisis puede ajustar la transición según la música.")
    "Volumen exponencial" -> uiText("Changes the NÜMA volume curve for finer control at low volume. Does not improve quality or normalize songs. Off by default.", "Cambia la curva del control de volumen de NÜMA para ofrecer más precisión al escuchar bajo. No aumenta la calidad ni normaliza las canciones. Está desactivado por defecto.")
    "Normalización de volumen" -> uiText("Reduces volume differences between songs when loudness data is available. Does not change system volume or guarantee identical loudness for every recording.", "Reduce las diferencias de volumen entre canciones cuando hay información de sonoridad disponible. No cambia el volumen del sistema ni garantiza que todas las grabaciones suenen idénticas.")
    "Ecualizador" -> uiText("Adjusts frequencies, gain and bass. Raising bands too much can distort the sound. Effects are unavailable with Max audio.", "Ajusta las frecuencias, la ganancia y los graves. Aumentar demasiado las bandas puede saturar el sonido. Los efectos no están disponibles en la ruta de audio Max.")
    "Calidad de transmisión", "Calidad con datos móviles" -> uiText("Controls requested streaming quality. Higher quality uses more data. Mobile data quality applies to metered networks; download quality is configured separately.", "Controla la calidad solicitada para escuchar en línea. Una calidad mayor utiliza más datos. La calidad con datos móviles se aplica a las redes con consumo medido; la calidad de descarga se configura por separado.")
    "Fuente de traducción", "Translation source" -> uiText("Google Translate uses the online service first. If it is unavailable, Google’s on-device translator is used. Models are prepared over Wi-Fi and saved for offline use. Translations stay separate for each language.", "Google Translate intenta primero el servicio en línea. Si no está disponible, usa el traductor de Google en el dispositivo. Los modelos se preparan por Wi-Fi y quedan guardados para usar sin conexión. Cada idioma conserva sus traducciones.")
    "Calidad de traducción", "Translation quality" -> uiText("Selects the on-device model when available. Larger models may use more storage, memory and time. This does not change Google Translate.", "Selecciona el modelo local cuando esta opción está disponible. Un modelo mayor puede requerir más almacenamiento, memoria y tiempo. No cambia Google Translate.")
    "Audio en caché", "Cached audio" -> uiText("Limits temporary audio storage used to replay songs without downloading them again. Cache may be cleared automatically; saved downloads are managed separately.", "Limita el espacio de audio temporal usado para volver a escuchar canciones sin descargarlas otra vez. La caché puede eliminarse automáticamente; las descargas guardadas se gestionan por separado.")
    "Partes sin música", "Non-music parts" -> uiText("Play everything, show a skip button, or automatically skip segments marked by SponsorBlock volunteers. Segments may be inaccurate or unavailable; the audio file is unchanged.", "Puedes reproducir todo, mostrar un botón para saltar o avanzar automáticamente por segmentos marcados por voluntarios de SponsorBlock. Puede haber errores o canciones sin segmentos; no modifica el archivo de audio.")
    else -> null
}
