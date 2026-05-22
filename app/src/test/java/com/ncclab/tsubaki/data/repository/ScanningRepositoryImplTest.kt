package com.ncclab.tsubaki.data.repository

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.ncclab.tsubaki.data.feature.FeatureFlagProvider
import com.ncclab.tsubaki.data.model.EngineType
import com.ncclab.tsubaki.data.model.ScanResult
import com.ncclab.tsubaki.data.scanner.factory.ScannerFactory
import com.ncclab.tsubaki.data.scanner.strategy.ScannerStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ScanningRepositoryImplTest {

    private class FakeFeatureFlagProvider(
        private var engine: EngineType = EngineType.ML_KIT
    ) : FeatureFlagProvider {
        override fun setActiveEngine(engine: EngineType) {
            this.engine = engine
        }

        override fun getActiveEngine(): EngineType = engine
    }

    private fun newRepository(): ScanningRepositoryImpl {
        // The unit test exercises handleResultsForTest directly, which never reaches
        // the factory or strategy. A real ScannerFactory is fine because nothing in
        // this test path calls factory.create.
        return ScanningRepositoryImpl(ScannerFactory(), FakeFeatureFlagProvider())
    }

    private fun currentResults(repository: ScanningRepositoryImpl): List<ScanResult> {
        // scanResults is exposed as a Flow but is backed by a MutableStateFlow.
        // Reading the field directly lets us assert the latched value synchronously.
        val field = ScanningRepositoryImpl::class.java.getDeclaredField("_scanResults")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val state = field.get(repository) as MutableStateFlow<List<ScanResult>>
        return state.value
    }

    @Test
    fun `first non-empty detection latches and subsequent detections are dropped`() {
        val repository = newRepository()
        val first = listOf(ScanResult("first", "QR_CODE", 1L))
        val second = listOf(ScanResult("second", "QR_CODE", 2L))

        repository.handleResultsForTest(first)
        repository.handleResultsForTest(second)

        assertEquals(first, currentResults(repository))
    }

    @Test
    fun `acknowledgeResult clears the latch and the emitted results`() {
        val repository = newRepository()
        val first = listOf(ScanResult("first", "QR_CODE", 1L))
        val second = listOf(ScanResult("second", "QR_CODE", 2L))

        repository.handleResultsForTest(first)
        assertEquals(first, currentResults(repository))

        repository.acknowledgeResult()
        assertTrue(currentResults(repository).isEmpty())

        repository.handleResultsForTest(second)
        assertEquals(second, currentResults(repository))
    }

    @Test
    fun `empty results never latch and do not overwrite existing results`() {
        val repository = newRepository()
        val first = listOf(ScanResult("first", "QR_CODE", 1L))

        repository.handleResultsForTest(emptyList())
        assertTrue(currentResults(repository).isEmpty())

        repository.handleResultsForTest(first)
        assertEquals(first, currentResults(repository))

        // Another empty pass after latching must not clear the value.
        repository.handleResultsForTest(emptyList())
        assertEquals(first, currentResults(repository))
    }

    @Test
    fun `resetScanner discards in-flight callbacks from the released strategy`() {
        val repository = newRepository()
        val stale = listOf(ScanResult("stale-from-old-engine", "QR_CODE", 1L))
        val fresh = listOf(ScanResult("fresh-from-new-engine", "QR_CODE", 2L))

        // Snapshot the generation that an in-flight analyze callback would
        // have captured before the user switched engines.
        val dispatchGeneration = repository.currentGeneration

        // The user opens the engine dialog and confirms a switch. resetScanner
        // releases the old strategy, clears the latch, and bumps the generation.
        repository.resetScanner()

        // The old strategy's success listener now lands. Without the
        // generation check this would emit a result decoded by the engine
        // the user just switched away from.
        repository.handleResultsForTest(stale, dispatchGeneration = dispatchGeneration)

        assertTrue(
            "Stale callback from released strategy must be ignored",
            currentResults(repository).isEmpty(),
        )

        // The next detection (using the freshly-selected engine) is dispatched
        // against the new generation and is published normally.
        repository.handleResultsForTest(fresh)
        assertEquals(fresh, currentResults(repository))
    }

    @Test
    fun `stale callback that lands DURING resetScanner is dropped before the latch is cleared`() {
        // This test exercises the narrow interleaving the v2 review flagged:
        // a callback from the released strategy fires partway through
        // resetScanner. With the generation increment hoisted to the first
        // statement of resetScanner, the in-flight reader observes G+1 and
        // is dropped by the generation guard regardless of the latch state.
        val repository = newRepository()
        val stale = listOf(ScanResult("stale-during-reset", "QR_CODE", 1L))

        // Drive resetScanner from a worker thread so the main thread can
        // interleave a stale callback while the worker is parked inside
        // the fake strategy's release().
        val releaseEntered = CountDownLatch(1)
        val releaseMayProceed = CountDownLatch(1)
        val blockingStrategy = object : ScannerStrategy {
            override fun analyze(imageProxy: ImageProxy, onResult: (List<ScanResult>) -> Unit) = Unit
            override fun analyzeImage(bitmap: Bitmap, onResult: (List<ScanResult>) -> Unit) = Unit
            override fun release() {
                releaseEntered.countDown()
                releaseMayProceed.await()
            }
        }
        val activeScannerField = ScanningRepositoryImpl::class.java
            .getDeclaredField("activeScanner")
        activeScannerField.isAccessible = true
        activeScannerField.set(repository, blockingStrategy)

        // Capture the generation that an in-flight callback dispatched
        // before the user tapped "switch engine" would have observed.
        val dispatchGeneration = repository.currentGeneration

        val resetThread = Thread {
            repository.resetScanner()
        }.apply { name = "test-resetScanner"; start() }

        try {
            assertTrue(
                "resetScanner did not enter release() in time",
                releaseEntered.await(2, TimeUnit.SECONDS),
            )

            // The worker thread is now parked inside release(). With the
            // hoisted increment, generation has already been bumped past
            // the dispatch value — but the latch (had it been set) would
            // not yet be cleared. Either way, the generation guard must
            // drop the stale callback.
            assertTrue(
                "Generation must already be bumped by the time release() is reached",
                repository.currentGeneration != dispatchGeneration,
            )
            repository.handleResultsForTest(stale, dispatchGeneration = dispatchGeneration)

            assertTrue(
                "Stale callback that landed during resetScanner must be dropped",
                currentResults(repository).isEmpty(),
            )
        } finally {
            releaseMayProceed.countDown()
            resetThread.join(2_000)
        }
    }
}
