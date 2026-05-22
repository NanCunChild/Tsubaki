package com.ncclab.tsubaki.data.repository

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import kotlinx.coroutines.flow.Flow
import com.ncclab.tsubaki.data.model.ScanResult

interface ScanningRepository {
    val scanResults: Flow<List<ScanResult>>
    val analyzer: ImageAnalysis.Analyzer
    fun scanFromBitmap(bitmap: Bitmap)
    fun resetScanner()
    fun acknowledgeResult()
}
