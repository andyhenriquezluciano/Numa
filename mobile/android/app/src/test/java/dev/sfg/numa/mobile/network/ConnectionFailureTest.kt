package dev.sfg.numa.mobile.network

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ConnectionFailureTest {
    @Test fun dnsFailureDoesNotExposeTechnicalHostDetails() {
        val message = connectionFailureMessage(IOException("wrapped", UnknownHostException("private-host.example")))!!
        assertTrue(message.contains("servidor de NÜMA"))
        assertFalse(message.contains("private-host"))
        assertFalse(message.contains("UnknownHostException"))
    }
    @Test fun distinguishesTimeoutFromApplicationErrors() {
        assertTrue(connectionFailureMessage(SocketTimeoutException())!!.contains("tardó demasiado"))
        assertNull(connectionFailureMessage(IllegalStateException("not a connection error")))
    }
}
