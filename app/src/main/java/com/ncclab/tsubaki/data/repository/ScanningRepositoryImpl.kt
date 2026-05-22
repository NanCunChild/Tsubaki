package com.ncclab.tsubaki.data.repository

import android.graphics.Bitmap
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageAnalysis
import com.ncclab.tsubaki.data.feature.FeatureFlagProvider
import com.ncclab.tsubaki.data.model.EngineType
import com.ncclab.tsubaki.data.model.ScanResult
import com.ncclab.tsubaki.data.scanner.factory.ScannerFactory
import com.ncclab.tsubaki.data.scanner.strategy.ScannerStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanningRepositoryImpl @Inject constructor(
    private val scannerFactory: ScannerFactory,
    private val featureFlagProvider: FeatureFlagProvider
) : ScanningRepository {

    private var activeScanner: ScannerStrategy? = null
    private var activeEngine: EngineType? = null

    @Volatile
    private var detectionLatched = false

    /**
     * Monotonic counter bumped on every [resetScanner]. Each in-flight
     * detection callback captures the value at dispatch time and
     * [handleResults] discards results whose generation no longer matches
     * the current one. This closes the race where a callback from the
     * strategy that was just released arrives after the user switched
     * engines and emits a stale result decoded by the previous engine.
     */
    @Volatile
    private var generation: Long = 0L

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    override val scanResults: Flow<List<ScanResult>> = _scanResults.asStateFlow()

    override val analyzer: ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { imageProxy ->
        if (detectionLatched) {
            imageProxy.close()
            return@Analyzer
        }

        val dispatchGeneration = generation
        ensureActiveScanner().analyze(imageProxy) { results ->
            handleResults(results, dispatchGeneration)
        }
    }

    override fun scanFromBitmap(bitmap: Bitmap) {
        if (detectionLatched) return

        val dispatchGeneration = generation
        ensureActiveScanner().analyzeImage(bitmap) { results ->
            handleResults(results, dispatchGeneration)
        }
    }

    override fun resetScanner() {
        // Invariant: bump the generation BEFORE touching any other state.
        //
        // handleResults reads generation first and the latch second, so if we
        // cleared the latch first an in-flight callback from the strategy we
        // are about to release could observe a still-matching generation and
        // a freshly-cleared latch and emit a stale result. Hoisting the
        // increment guarantees any in-flight reader observes G+1 (and is
        // dropped by the generation guard) before the latch is cleared.
        generation += 1
        activeScanner?.release()
        activeScanner = null
        activeEngine = null
        detectionLatched = false
    }

    override fun acknowledgeResult() {
        detectionLatched = false
        _scanResults.value = emptyList()
    }

    private fun ensureActiveScanner(): ScannerStrategy {
        val scanner = activeScanner
        if (scanner != null) return scanner

        val engine = featureFlagProvider.getActiveEngine()
        val newScanner = scannerFactory.create(engine)
        activeScanner = newScanner
        activeEngine = engine
        return newScanner
    }

    private fun handleResults(results: List<ScanResult>, dispatchGeneration: Long) {
        // Drop callbacks that were dispatched against a strategy which has
        // since been released by resetScanner().
        if (dispatchGeneration != generation) return
        if (detectionLatched) return
        if (results.isEmpty()) return
        detectionLatched = true
        _scanResults.value = results
    }

    @VisibleForTesting
    internal val currentGeneration: Long
        get() = generation

    @VisibleForTesting
    internal fun handleResultsForTest(
        results: List<ScanResult>,
        dispatchGeneration: Long = generation,
    ) = handleResults(results, dispatchGeneration)
}
