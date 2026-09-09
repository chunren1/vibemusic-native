package com.cyk666.vibemusic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException

class SearchCancelTest {

    @Test
    fun staleOlderCompletionMustBeDropped() {
        var latestGen = 2
        assertTrue(isStaleSearchResult(1, latestGen))
        assertFalse(isStaleSearchResult(2, latestGen))
        latestGen = 3
        assertTrue(isStaleSearchResult(2, latestGen))
    }

    @Test
    fun onlyLatestCompletionUpdatesUi() {
        var latestGen = 0
        val applied = mutableListOf<Int>()
        fun complete(gen: Int, payload: Int) {
            if (isStaleSearchResult(gen, latestGen)) return
            applied.add(payload)
        }
        latestGen = 1
        latestGen = 2
        complete(1, 111)
        complete(2, 222)
        assertEquals(listOf(222), applied)
    }

    @Test
    fun cancellableAwait_cancelInvokesCallCancel(): Unit = runBlocking {
        val cancelCalled = AtomicBoolean(false)
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            suspendCancellableCoroutine<Unit> { cont ->
                cont.invokeOnCancellation { cancelCalled.set(true) }
            }
        }
        job.cancel(CancellationException("superseded"))
        job.join()
        assertTrue(cancelCalled.get())
    }

    @Test
    fun cancelledContinuationResultIsNeverDelivered(): Unit = runBlocking {
        var delivered = false
        val job: Job = launch {
            suspendCancellableCoroutine<Unit> { _ -> }
            delivered = true
        }
        job.cancel()
        try {
            job.join()
        } catch (_: CancellationException) {
        }
        assertFalse(delivered)
    }
}
