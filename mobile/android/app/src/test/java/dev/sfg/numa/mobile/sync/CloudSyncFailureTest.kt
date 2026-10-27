package dev.sfg.numa.mobile.sync

import org.junit.Assert.*
import org.junit.Test

class CloudSyncFailureTest {
    @Test fun serverFailuresExplainDataSafetyAndRetry() {
        for (code in listOf(500, 502, 503, 504)) {
            val message = cloudSyncFailureMessage(code, "private server trace")
            assertTrue(message.contains("datos se conservan"))
            assertTrue(message.contains("automáticamente"))
            assertFalse(message.contains("private"))
        }
    }
    @Test fun authenticationAndQuotaHaveDifferentRecoverySteps() {
        assertTrue(cloudSyncFailureMessage(401, "").contains("iniciar sesión"))
        assertTrue(cloudSyncFailureMessage(413, "").contains("espacio"))
        assertTrue(cloudSyncFailureMessage(429, "").contains("automáticamente"))
    }
}
