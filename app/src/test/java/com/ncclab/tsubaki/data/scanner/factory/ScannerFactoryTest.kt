package com.ncclab.tsubaki.data.scanner.factory

import com.ncclab.tsubaki.data.model.EngineType
import com.ncclab.tsubaki.data.scanner.strategy.MLKitScannerStrategy
import com.ncclab.tsubaki.data.scanner.strategy.ZXingScannerStrategy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ScannerFactory].
 *
 * Note on coverage: the ML_KIT branch cannot be exercised end-to-end in a plain JVM
 * unit test because [MLKitScannerStrategy] eagerly calls
 * `BarcodeScanning.getClient(...)` in its constructor, which requires an
 * initialized `MlKitContext` (an Android-only singleton). The project does not pull
 * in Robolectric or a mocking framework, so we instead verify the regression that
 * actually shipped: [EngineType.ZXING] used to be hard-coded to return
 * [MLKitScannerStrategy]. Asserting that ZXING produces a [ZXingScannerStrategy]
 * (and not an [MLKitScannerStrategy]) is enough to catch that bug if it returns.
 */
class ScannerFactoryTest {

    private val factory = ScannerFactory()

    @Test
    fun `create returns ZXingScannerStrategy for ZXING`() {
        val strategy = factory.create(EngineType.ZXING)
        assertTrue(
            "Expected ZXingScannerStrategy but got ${strategy::class.java.name}",
            strategy is ZXingScannerStrategy
        )
        assertFalse(
            "ZXING must not silently fall back to MLKitScannerStrategy",
            strategy is MLKitScannerStrategy
        )
    }
}
