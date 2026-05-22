package com.ncclab.tsubaki.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncclab.tsubaki.data.feature.FeatureFlagProvider
import com.ncclab.tsubaki.data.model.EngineType
import com.ncclab.tsubaki.data.model.ScanResult
import com.ncclab.tsubaki.data.repository.ScanningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val repository: ScanningRepository,
    private val featureFlagProvider: FeatureFlagProvider
) : ViewModel() {

    private val _scanResult = MutableStateFlow<ScanResult?>(null)
    val scanResult = _scanResult.asStateFlow()

    private val _currentEngine = MutableStateFlow(featureFlagProvider.getActiveEngine())
    val currentEngine = _currentEngine.asStateFlow()

    val analyzer = repository.analyzer

    init {
        viewModelScope.launch {
            repository.scanResults.collect { results ->
                Log.d("TsubakiTrace", "[ViewModel] Collected ${results.size} results. First rawValue: ${results.firstOrNull()?.rawValue}")
                _scanResult.value = results.firstOrNull()
            }
        }
    }

    fun getCurrentEngine(): EngineType = featureFlagProvider.getActiveEngine()

    fun setEngine(engine: EngineType) {
        featureFlagProvider.setActiveEngine(engine)
        _currentEngine.value = engine
        repository.resetScanner()
    }

    fun clearResult() {
        _scanResult.value = null
        repository.acknowledgeResult()
    }

    fun scanFromBitmap(bitmap: Bitmap) {
        repository.scanFromBitmap(bitmap)
    }
}