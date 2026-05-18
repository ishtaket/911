package com.pca.assistant.pipeline

import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.workDataOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BootstrapProgressTest {

    @Test fun `parses RUNNING progress with model + percent`() {
        val data: Data = workDataOf(
            ModelBootstrapWorker.KEY_MODEL_ID to "whisper-large-v3-turbo-q5_0",
            ModelBootstrapWorker.KEY_MODEL_ORDINAL to 1,
            ModelBootstrapWorker.KEY_MODEL_TOTAL to 2,
            ModelBootstrapWorker.KEY_PERCENT to 42,
            ModelBootstrapWorker.KEY_DOWNLOADED_MB to 336,
            ModelBootstrapWorker.KEY_TOTAL_MB to 800,
        )
        val p = BootstrapProgress.from(data, WorkInfo.State.RUNNING)
        assertTrue(p.running)
        assertFalse(p.enqueued)
        assertFalse(p.failed)
        assertFalse(p.succeeded)
        assertEquals(1, p.modelOrdinal)
        assertEquals(2, p.modelTotal)
        assertEquals(42, p.percent)
        assertEquals(336, p.downloadedMb)
        assertEquals(800, p.totalMb)
        assertNull(p.failReason)
    }

    @Test fun `parses ENQUEUED state - waiting for wifi`() {
        val p = BootstrapProgress.from(Data.EMPTY, WorkInfo.State.ENQUEUED)
        assertFalse(p.running)
        assertTrue(p.enqueued)
        assertFalse(p.failed)
    }

    @Test fun `parses BLOCKED state - constraint not met`() {
        val p = BootstrapProgress.from(Data.EMPTY, WorkInfo.State.BLOCKED)
        assertTrue("BLOCKED is treated like ENQUEUED for UI purposes", p.enqueued)
    }

    @Test fun `parses SUCCEEDED state`() {
        val p = BootstrapProgress.from(Data.EMPTY, WorkInfo.State.SUCCEEDED)
        assertTrue(p.succeeded)
        assertFalse(p.failed)
    }

    @Test fun `parses FAILED state and surfaces reason`() {
        val data = workDataOf(
            ModelBootstrapWorker.KEY_FAIL_REASON to "refused: model URL must be https"
        )
        val p = BootstrapProgress.from(data, WorkInfo.State.FAILED)
        assertTrue(p.failed)
        assertFalse(p.succeeded)
        assertEquals("refused: model URL must be https", p.failReason)
    }

    @Test fun `missing fields default to 0 or null`() {
        val p = BootstrapProgress.from(Data.EMPTY, WorkInfo.State.RUNNING)
        assertEquals(0, p.percent)
        assertEquals(0, p.downloadedMb)
        assertNull(p.failReason)
    }
}
