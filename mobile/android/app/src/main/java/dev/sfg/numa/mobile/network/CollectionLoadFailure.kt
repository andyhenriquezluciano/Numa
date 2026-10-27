package dev.sfg.numa.mobile.network

import dev.sfg.numa.mobile.model.LoadState
import dev.sfg.numa.mobile.ui.text.uiText
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal fun collectionLoadFailure(error: Throwable? = null, offline: Boolean = false): LoadState.Error {
    val causes = generateSequence(error) { it.cause }.take(10).toList()
    val connectionFailed = causes.any { it is UnknownHostException || it is ConnectException || it is SocketException }
        || causes.any { cause -> listOf("unable to resolve host", "no address associated", "network is unreachable",
            "failed to connect", "network request failed").any { cause.message.orEmpty().contains(it, ignoreCase = true) } }
    return when {
        offline || connectionFailed -> LoadState.Error(
            title = uiText("Connection unavailable", "Sin conexión con el servicio"),
            message = uiText("This collection needs an Internet connection. Check Wi-Fi or mobile data and try again. You can still listen to downloaded songs from Library > Downloads.",
                "Necesitas conexión para abrir esta colección. Revisa el Wi-Fi o los datos móviles y vuelve a intentarlo. Puedes seguir escuchando tus canciones descargadas en Biblioteca > Descargas."))
        causes.any { it is SocketTimeoutException } -> LoadState.Error(
            title = uiText("The service is taking too long", "El servicio tarda en responder"),
            message = uiText("The collection could not load in time. Check your connection and try again. Your downloaded songs are still available.",
                "No se pudo cargar la colección a tiempo. Comprueba tu conexión y vuelve a intentarlo. Tus descargas siguen disponibles."))
        causes.any { it is SSLException } -> LoadState.Error(
            title = uiText("Secure connection unavailable", "No se pudo conectar de forma segura"),
            message = uiText("Check the device date and time or try another network, then reopen the collection.",
                "Comprueba la fecha y hora del dispositivo o prueba otra red. Después vuelve a abrir la colección."))
        else -> LoadState.Error(
            title = uiText("Collection unavailable", "Colección no disponible"),
            message = uiText("This collection could not be loaded. Try again later. Your saved library and downloads are still available.",
                "No se pudo cargar esta colección. Inténtalo de nuevo más tarde. Tu biblioteca guardada y tus descargas siguen disponibles."))
    }
}
