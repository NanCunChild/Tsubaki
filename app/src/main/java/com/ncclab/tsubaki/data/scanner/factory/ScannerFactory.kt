package com.ncclab.tsubaki.data.scanner.factory

import com.ncclab.tsubaki.data.model.EngineType
import com.ncclab.tsubaki.data.scanner.strategy.MLKitScannerStrategy
import com.ncclab.tsubaki.data.scanner.strategy.ScannerStrategy
import com.ncclab.tsubaki.data.scanner.strategy.ZXingScannerStrategy
import javax.inject.Inject

class ScannerFactory @Inject constructor() {
    fun create(engineType: EngineType): ScannerStrategy {
        return when (engineType) {
            EngineType.ML_KIT -> MLKitScannerStrategy()
            EngineType.ZXING -> ZXingScannerStrategy()
        }
    }
}
