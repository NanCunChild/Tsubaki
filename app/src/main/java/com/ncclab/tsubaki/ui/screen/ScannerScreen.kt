package com.ncclab.tsubaki.ui.screen

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size as AndroidSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.ncclab.tsubaki.data.model.EngineType
import com.ncclab.tsubaki.ui.viewmodel.ScannerViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = hiltViewModel(),
    onScanResult: (format: String, rawValue: String) -> Unit
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showEngineDialog by remember { mutableStateOf(false) }
    val currentEngine by viewModel.currentEngine.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(context.contentResolver, selectedUri)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, selectedUri)
                }
                viewModel.scanFromBitmap(bitmap)
            } catch (e: Exception) {
                // TODO: 可以添加错误处理，比如显示Toast
                e.printStackTrace()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                onLoginClick = {
                    scope.launch { drawerState.close() }
                    // TODO: 在这里处理登录逻辑或导航到登录页面
                },
                onCreateQrClick = {
                    scope.launch { drawerState.close() }
                    // TODO: 在这里导航到创建二维码的 Activity
                },
                currentEngine = viewModel.getCurrentEngine(),
                onScannerEngineClick = {
                    scope.launch { drawerState.close() }
                    showEngineDialog = true
                },
                onSettingsClick = {
                    scope.launch { drawerState.close() }
                    // TODO: 在这里导航到设置 Activity
                }
            )
        }
    ) {

        val lifecycleOwner = LocalLifecycleOwner.current
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
        val scanResult by viewModel.scanResult.collectAsState()
        val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
        val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

        DisposableEffect(Unit) {
            onDispose { analyzerExecutor.shutdown() }
        }

        LaunchedEffect(cameraPermissionState.status.isGranted) {
            if (!cameraPermissionState.status.isGranted) {
                cameraPermissionState.launchPermissionRequest()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (cameraPermissionState.status.isGranted) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val mainExecutor = ContextCompat.getMainExecutor(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setResolutionSelector(
                                    ResolutionSelector.Builder()
                                        .setResolutionStrategy(
                                            ResolutionStrategy(
                                                AndroidSize(1280, 720),
                                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                                            )
                                        )
                                        .build()
                                )
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { it.setAnalyzer(analyzerExecutor, viewModel.analyzer) }

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        }, mainExecutor)
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                ScannerOverlay(
                    modifier = Modifier.fillMaxSize(),
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                    onGalleryClick = {
                        imagePickerLauncher.launch("image/*")
                    }
                )

            } else {
                // 如果没有权限，显示提示信息
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("需要相机权限才能扫描", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("授予权限")
                    }
                }
            }

            if (scanResult != null) {
                LaunchedEffect(scanResult) {
                    scanResult?.let { result ->
                        onScanResult(result.format, result.rawValue)
                        viewModel.clearResult()
                    }
                }
            }
        }
    }

    if (showEngineDialog) {
        EngineSelectionDialog(
            currentEngine = currentEngine,
            onEngineSelected = { engine ->
                viewModel.setEngine(engine)
            },
            onDismiss = {
                showEngineDialog = false
            }
        )
    }
}

@Composable
fun AppDrawerContent(
    currentEngine: EngineType,
    onLoginClick: () -> Unit,
    onCreateQrClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onScannerEngineClick: () -> Unit
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Tsubaki Scanner",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // 1. 用户登录行
//            DrawerItem(
//                icon = Icons.Default.AccountCircle,
//                text = "登录/同步",
//                onClick = onLoginClick
//            )
//
//            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
//
//            // 2. 创建二维码
//            DrawerItem(
//                icon = Icons.Default.Add,
//                text = "创建二维码",
//                onClick = onCreateQrClick
//            )

            DrawerItemWithSubtext(
                icon = Icons.Default.Tune,
                text = "扫描引擎",
                subtext = "当前: ${getEngineDisplayName(currentEngine)}",
                onClick = onScannerEngineClick
            )

            // 3. 设置
//            DrawerItem(
//                icon = Icons.Default.Settings,
//                text = "设置",
//                onClick = onSettingsClick
//            )
        }
    }
}


@Composable
private fun DrawerItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun DrawerItemWithSubtext(
    icon: ImageVector,
    text: String,
    subtext: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}



@Composable
fun ScannerOverlay(modifier: Modifier, onMenuClick: () -> Unit, onGalleryClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_line")
    val boxSizePx = with(LocalDensity.current) { 256.dp.toPx() }
    val animatedProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = boxSizePx,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, delayMillis = 200),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanner_line_animation"
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val boxSize = size.minDimension * 0.7f

            val rect = Rect(
                left = (canvasWidth - boxSize) / 2,
                top = (canvasHeight - boxSize) / 2,
                right = (canvasWidth + boxSize) / 2,
                bottom = (canvasHeight + boxSize) / 2
            )

            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
            )

            drawRoundRect(
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(16.dp.toPx()),
                color = Color.Transparent,
                blendMode = BlendMode.Clear // 关键！这会使绘制区域变透明
            )

            drawRoundRect(
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(16.dp.toPx()),
                color = Color.White,
                style = Stroke(width = 2.dp.toPx())
            )

            // 绘制移动的扫描线
//            val gradient = Brush.verticalGradient(
//                colors = listOf(Color.Transparent, Color(0xFF00C853).copy(alpha = 0.8f), Color.Transparent),
//                startY = rect.top,
//                endY = rect.bottom
//            )
//            drawLine(
//                brush = gradient,
//                start = Offset(rect.left, rect.top + animatedProgress.value),
//                end = Offset(rect.right, rect.top + animatedProgress.value),
//                strokeWidth = 4.dp.toPx()
//            )
        }

        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp, 34.dp, 16.dp, 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "菜单",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        IconButton(
            onClick = onGalleryClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp, 34.dp, 16.dp, 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = "从相册选择",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun EngineSelectionDialog(
    currentEngine: EngineType,
    onEngineSelected: (EngineType) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedEngine by remember { mutableStateOf(currentEngine) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择扫描引擎")
        },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                EngineType.entries.forEach { engine ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (selectedEngine == engine),
                                onClick = { selectedEngine = engine }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedEngine == engine),
                            onClick = { selectedEngine = engine }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = getEngineDisplayName(engine),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = getEngineDescription(engine),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onEngineSelected(selectedEngine)
                    onDismiss()
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun getEngineDisplayName(engine: EngineType): String {
    return when (engine) {
        EngineType.ML_KIT -> "ML Kit"
        EngineType.ZXING -> "ZXing"
    }
}

private fun getEngineDescription(engine: EngineType): String {
    return when (engine) {
        EngineType.ML_KIT -> "Google ML Kit - 更快，更准确"
        EngineType.ZXING -> "ZXing - 开源，支持更多格式"
    }
}

