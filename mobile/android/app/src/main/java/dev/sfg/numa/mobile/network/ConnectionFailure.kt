package dev.sfg.numa.mobile.network

import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal fun connectionFailureMessage(error: Throwable): String? {
    val causes = generateSequence(error) { it.cause }.take(10).toList()
    return when {
        causes.any { it is UnknownHostException } ->
            "No se pudo localizar el servidor de NÜMA. Comprueba tu conexión o prueba otra red."
        causes.any { it is SocketTimeoutException } ->
            "El servidor de NÜMA tardó demasiado en responder. Inténtalo de nuevo."
        causes.any { it is SSLException } ->
            "No se pudo establecer una conexión segura con NÜMA. Comprueba la fecha y la conexión del dispositivo."
        causes.any { it is ConnectException || it is SocketException } ->
            "No se pudo conectar con NÜMA. Comprueba tu conexión e inténtalo de nuevo."
        else -> null
    }
}
