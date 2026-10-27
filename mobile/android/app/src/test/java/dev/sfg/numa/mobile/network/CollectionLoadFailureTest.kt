package dev.sfg.numa.mobile.network

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class CollectionLoadFailureTest {
    @Test fun knownOfflineStateExplainsHowToKeepListening() {
        val result = collectionLoadFailure(offline = true)
        assertTrue(result.message.contains("Descargas") || result.message.contains("Downloads"))
        assertTrue(result.title.isNotBlank())
    }
    @Test fun wrappedDnsFailuresDoNotExposeHostNames() {
        val result = collectionLoadFailure(IOException("wrapper", UnknownHostException("music.youtube.com")))
        assertFalse(result.message.contains("youtube.com"))
        assertTrue(result.message.contains("Wi-Fi"))
    }
    @Test fun providerErrorsWithNetworkTextAreRecognized() {
        val result = collectionLoadFailure(IllegalStateException("Unable to resolve host music.youtube.com: No address associated with hostname"))
        assertEquals(collectionLoadFailure(offline = true), result)
    }
    @Test fun timeoutHasItsOwnExplanation() {
        assertNotEquals(collectionLoadFailure(offline = true).title, collectionLoadFailure(SocketTimeoutException()).title)
    }
    @Test fun applicationFailuresDoNotLeakInternalDetails() {
        val result = collectionLoadFailure(IllegalStateException("internal-private-stack-trace"))
        assertFalse(result.message.contains("private"))
        assertFalse(result.title.isBlank())
    }
}
