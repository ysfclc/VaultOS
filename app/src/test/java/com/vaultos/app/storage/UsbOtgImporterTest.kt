package com.vaultos.app.storage

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class UsbOtgImporterTest {
    @Test
    fun returnsSuccessfulImportResult() {
        assertEquals(42, runCatchingUsbImport { 42 }.getOrThrow())
    }

    @Test
    fun capturesRecoverableProviderFailure() {
        val expected = IllegalStateException("provider unavailable")
        val result = runCatchingUsbImport<Int> { throw expected }
        assertSame(expected, result.exceptionOrNull())
    }

    @Test
    fun propagatesCoroutineCancellation() {
        val expected = CancellationException("import cancelled")
        val actual = try {
            runCatchingUsbImport<Int> { throw expected }
            null
        } catch (failure: CancellationException) {
            failure
        }
        assertSame(expected, actual)
    }
}
