package app.overbay.swiftfox.ui.sync

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import app.overbay.swiftfox.ui.icons.rounded.Link
import app.overbay.swiftfox.ui.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.overbay.swiftfox.features.sync.MozillaSyncCoordinator
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * CameraX + ZXing QR code scanner for Mozilla Account / Zen Browser device pairing,
 * with a manual paste-pairing-link fallback dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrPairingOverlay(
    onPairingUrlDetected: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var showManualDialog by remember { mutableStateOf(false) }
    var invalidCodeHint by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Pair with Firefox / Zen",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close scanner")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    var cameraBindError by remember { mutableStateOf<String?>(null) }
                    if (cameraBindError == null) {
                        QrCameraPreview(
                            lifecycleOwner = lifecycleOwner,
                            onQrDecoded = { text ->
                                if (MozillaSyncCoordinator.isValidPairingUrl(text)) {
                                    onPairingUrlDetected(text)
                                } else {
                                    invalidCodeHint = "Scanned QR code is not a Mozilla pairing link"
                                }
                            },
                            onCameraError = { errMsg ->
                                cameraBindError = errMsg
                            }
                        )

                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Transparent,
                            border = BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.size(232.dp)
                        ) {}
                    } else {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCodeScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Camera preview unavailable",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = cameraBindError ?: "Could not open back camera. Paste the pairing link below instead.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Camera access needed to scan QR code",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "You can also paste the pairing URL directly if you prefer.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Grant camera permission")
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "On Desktop Firefox or Zen Browser, open Settings -> Sync -> Pair another device and point the camera at the QR code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!invalidCodeHint.isNullOrBlank()) {
                        Text(
                            text = invalidCodeHint!!,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    OutlinedButton(
                        onClick = { showManualDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Paste pairing link manually")
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        var linkDraft by remember { mutableStateOf("") }
        val trimmedLink = linkDraft.trim()
        val isLinkValid = remember(trimmedLink) {
            trimmedLink.isNotEmpty() && MozillaSyncCoordinator.isValidPairingUrl(trimmedLink)
        }
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("Paste pairing URL") },
            text = {
                OutlinedTextField(
                    value = linkDraft,
                    onValueChange = { linkDraft = it },
                    label = { Text("https://accounts.firefox.com/pair#...") },
                    singleLine = true,
                    isError = trimmedLink.isNotEmpty() && !isLinkValid,
                    supportingText = if (trimmedLink.isNotEmpty() && !isLinkValid) {
                        { Text("Enter a valid Firefox / Mozilla pairing link") }
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showManualDialog = false
                        onPairingUrlDetected(trimmedLink)
                    },
                    enabled = isLinkValid
                ) {
                    Text("Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun QrCameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onQrDecoded: (String) -> Unit,
    onCameraError: (String) -> Unit
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val reader = remember {
        MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }
    }
    val delivered = remember { AtomicBoolean(false) }
    val lastRejectedQr = remember { AtomicReference<String?>(null) }
    val currentOnQrDecoded by rememberUpdatedState(onQrDecoded)
    val currentOnCameraError by rememberUpdatedState(onCameraError)

    DisposableEffect(lifecycleOwner) {
        val disposed = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var boundAnalysis: ImageAnalysis? = null
        var reusableBytes = ByteArray(0)
        var reusableRowBuf = ByteArray(0)

        providerFuture.addListener(
            {
                if (disposed.get()) return@addListener
                val provider = runCatching { providerFuture.get() }.getOrElse { err ->
                    if (!disposed.get()) {
                        currentOnCameraError(err.message ?: "Camera provider unavailable")
                    }
                    return@addListener
                }
                if (disposed.get()) return@addListener
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                boundAnalysis = analysis

                analysis.setAnalyzer(executor) { imageProxy ->
                    try {
                        if (delivered.get() || disposed.get()) return@setAnalyzer
                        val plane = imageProxy.planes.firstOrNull() ?: return@setAnalyzer
                        val buffer = plane.buffer
                        buffer.rewind()
                        val rowStride = plane.rowStride
                        val width = imageProxy.width
                        val height = imageProxy.height
                        val needed = width * height
                        if (reusableBytes.size != needed) {
                            reusableBytes = ByteArray(needed)
                        }
                        val bytes = reusableBytes

                        if (rowStride == width) {
                            val toRead = minOf(bytes.size, buffer.remaining())
                            buffer.get(bytes, 0, toRead)
                        } else {
                            if (reusableRowBuf.size < rowStride) {
                                reusableRowBuf = ByteArray(rowStride)
                            }
                            val rowBuf = reusableRowBuf
                            val limit = buffer.limit()
                            for (row in 0 until height) {
                                val rowStart = row * rowStride
                                if (rowStart >= limit) break
                                buffer.position(rowStart)
                                val remaining = minOf(rowStride, buffer.remaining())
                                if (remaining <= 0) break
                                buffer.get(rowBuf, 0, remaining)
                                val copyLen = minOf(width, remaining)
                                System.arraycopy(rowBuf, 0, bytes, row * width, copyLen)
                            }
                        }

                        val source = PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false)
                        val bitmap = BinaryBitmap(HybridBinarizer(source))
                        val result = runCatching { reader.decodeWithState(bitmap) }.getOrNull()
                        val text = result?.text?.trim()
                        if (!text.isNullOrEmpty() && !disposed.get() && MozillaSyncCoordinator.isValidPairingUrl(text)) {
                            if (delivered.compareAndSet(false, true)) {
                                mainExecutor.execute {
                                    if (!disposed.get()) currentOnQrDecoded(text)
                                }
                            }
                        } else if (!text.isNullOrEmpty() && !disposed.get() && lastRejectedQr.getAndSet(text) != text) {
                            mainExecutor.execute {
                                if (!disposed.get()) currentOnQrDecoded(text)
                            }
                        }
                    } finally {
                        reader.reset()
                        imageProxy.close()
                    }
                }

                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }.onFailure { err ->
                    if (!disposed.get()) {
                        currentOnCameraError(err.message ?: "Unable to start camera on this device")
                    }
                }
            },
            mainExecutor
        )

        onDispose {
            disposed.set(true)
            runCatching { boundAnalysis?.clearAnalyzer() }
            boundAnalysis = null
            if (providerFuture.isDone) {
                runCatching { providerFuture.get().unbindAll() }
            }
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}
