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

internal fun settingsHelp(title: String): String? = when (title) {
    "Orden de reproducción" -> "Elige el orden al iniciar una lista o un álbum. Original conserva su orden; Aleatorio mezcla las siguientes canciones; Mejor mezcla analiza ritmo, tonalidad y energía y organiza hasta las próximas 50 canciones. Se mantiene primero la canción que elijas. Los botones explícitos de Aleatorio y Mejor mezcla respetan su propia acción. Cambiar este ajuste no interrumpe la cola actual."
    "Mejor mezcla" -> "Organiza hasta 50 canciones siguientes por compatibilidad musical. Usa archivos descargados o audio temporal de baja calidad; la primera preparación puede consumir datos y tardar. El análisis se guarda para reutilizarlo. No activa ni cambia el tipo de transición."
    "Tipo de transición" -> "Desactivada: reproduce una canción después de otra. Fundido cruzado: superpone el final y el inicio mediante cambios de volumen. Adaptativa: analiza el audio para preparar una transición según el ritmo y la estructura; necesita más procesamiento y usa un fundido normal si no consigue prepararla. No cambia el orden de la cola."
    "Duración de la transición" -> "En el fundido cruzado controla los segundos de superposición. En la mezcla adaptativa es la duración solicitada; el análisis puede ajustar la transición según la música."
    "Volumen exponencial" -> "Cambia la curva del control de volumen de NÜMA para ofrecer más precisión al escuchar bajo. No aumenta la calidad ni normaliza las canciones. Está desactivado por defecto."
    "Normalización de volumen" -> "Reduce las diferencias de volumen entre canciones cuando hay información de sonoridad disponible. No cambia el volumen del sistema ni garantiza que todas las grabaciones suenen idénticas."
    "Ecualizador" -> "Ajusta las frecuencias, la ganancia y los graves. Aumentar demasiado las bandas puede saturar el sonido. Los efectos no están disponibles en la ruta de audio Max."
    "Calidad de transmisión", "Calidad con datos móviles" -> "Controla la calidad solicitada para escuchar en línea. Una calidad mayor utiliza más datos. La calidad con datos móviles se aplica a las redes con consumo medido; la calidad de descarga se configura por separado."
    "Reproducción automática" -> "Añade canciones relacionadas cuando se termina la cola. Es independiente del orden y de las transiciones. Al desactivarla se retiran las canciones añadidas automáticamente."
    "Fuente de traducción", "Translation source" -> uiText("Google Translate needs Internet access. On-device translation downloads language models once and then works offline. Saved translations can be reused.", "Google Translate usa Internet para traducir. La traducción local necesita descargar modelos antes de poder funcionar sin conexión. Las traducciones guardadas pueden reutilizarse.")
    "Calidad de traducción", "Translation quality" -> uiText("Selects the on-device model when available. Larger models may use more storage, memory and time. This does not change Google Translate.", "Selecciona el modelo local cuando esta opción está disponible. Un modelo mayor puede requerir más almacenamiento, memoria y tiempo. No cambia Google Translate.")
    "Audio en caché", "Cached audio" -> "Limita el espacio de audio temporal usado para volver a escuchar canciones sin descargarlas otra vez. La caché puede eliminarse automáticamente; las descargas guardadas se gestionan por separado."
    "Partes sin música", "Non-music parts" -> "Puedes reproducir todo, mostrar un botón para saltar o avanzar automáticamente por segmentos marcados por voluntarios de SponsorBlock. Puede haber errores o canciones sin segmentos; no modifica el archivo de audio."
    else -> null
}
