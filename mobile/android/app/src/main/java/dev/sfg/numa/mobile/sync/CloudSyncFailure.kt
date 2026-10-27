package dev.sfg.numa.mobile.sync

internal fun cloudSyncFailureMessage(code: Int, description: String): String = when (code) {
    401 -> "La sesión de NÜMA expiró. Vuelve a iniciar sesión."
    409 -> "Otro dispositivo está sincronizando. Se reintentará automáticamente."
    413 -> "La copia supera el espacio de la nube. Tus datos locales se conservan."
    429 -> "El servidor recibió demasiadas solicitudes. Se reintentará automáticamente."
    in 500..599 -> "El servidor de sincronización no está disponible temporalmente. Tus datos se conservan y se reintentará automáticamente."
    else -> description.ifBlank { "No se pudo sincronizar. Tus datos locales se conservan." }
}
