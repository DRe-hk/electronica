package com.electrodomesticos.registry.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrBarcodeScannerCard(
  isScanning: Boolean,
  scanLabel: String,
  pausedCode: String?,
  onCodeDetected: (String) -> Unit,
  onResumeScan: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    hasCameraPermission = granted
  }

  LaunchedEffect(Unit) {
    if (!hasCameraPermission) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  OutlinedCard(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(
      width = 1.5.dp,
      color = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
    ),
  ) {
    if (!hasCameraPermission) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Default.CameraAlt,
          contentDescription = null,
          modifier = Modifier.size(36.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = "Se requiere permiso de cámara para escanear códigos QR y códigos de barras.",
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
        )
        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
          Text("Permitir Cámara")
        }
      }
    } else if (!isScanning) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Default.PauseCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
          )
          Column {
            Text(
              text = "Escáner pausado (código detectado)",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
            )
            if (!pausedCode.isNullOrBlank()) {
              Text(
                text = pausedCode,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        Button(onClick = onResumeScan) {
          Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(6.dp))
          Text("Escanear")
        }
      }
    } else {
      ActiveCameraScannerBox(
        scanLabel = scanLabel,
        onCodeDetected = { rawValue ->
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
          onCodeDetected(rawValue)
        },
      )
    }
  }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun ActiveCameraScannerBox(
  scanLabel: String,
  onCodeDetected: (String) -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val latestOnCodeDetected by rememberUpdatedState(onCodeDetected)

  var torchEnabled by remember { mutableStateOf(false) }
  var boundCamera by remember { mutableStateOf<Camera?>(null) }
  val hasTriggered = remember { AtomicBoolean(false) }
  val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

  val scanner = remember {
    val options = BarcodeScannerOptions.Builder()
      .setBarcodeFormats(
        Barcode.FORMAT_QR_CODE,
        Barcode.FORMAT_EAN_13,
        Barcode.FORMAT_EAN_8,
        Barcode.FORMAT_UPC_A,
        Barcode.FORMAT_UPC_E,
        Barcode.FORMAT_CODE_128,
        Barcode.FORMAT_CODE_39,
        Barcode.FORMAT_DATA_MATRIX,
      )
      .build()
    BarcodeScanning.getClient(options)
  }

  val previewView = remember {
    PreviewView(context).apply {
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }
  }

  DisposableEffect(lifecycleOwner) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val mainExecutor = ContextCompat.getMainExecutor(context)

    val listener = Runnable {
      val cameraProvider = cameraProviderFuture.get()
      val preview = Preview.Builder().build().also {
        it.surfaceProvider = previewView.surfaceProvider
      }

      @Suppress("DEPRECATION")
      val imageAnalysis = ImageAnalysis.Builder()
        .setTargetResolution(Size(1280, 720))
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()

      imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
        if (hasTriggered.get()) {
          imageProxy.close()
          return@setAnalyzer
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
          imageProxy.close()
          return@setAnalyzer
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(inputImage)
          .addOnSuccessListener { barcodes ->
            val value = barcodes.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf(String::isNotEmpty) }
            if (value != null && hasTriggered.compareAndSet(false, true)) {
              // Stop analyzer immediately before notifying UI
              imageAnalysis.clearAnalyzer()
              mainExecutor.execute {
                latestOnCodeDetected(value)
              }
            }
          }
          .addOnCompleteListener {
            imageProxy.close()
          }
      }

      runCatching {
        cameraProvider.unbindAll()
        boundCamera = cameraProvider.bindToLifecycle(
          lifecycleOwner,
          CameraSelector.DEFAULT_BACK_CAMERA,
          preview,
          imageAnalysis,
        )
      }
    }

    cameraProviderFuture.addListener(listener, mainExecutor)

    onDispose {
      runCatching {
        if (cameraProviderFuture.isDone) {
          cameraProviderFuture.get().unbindAll()
        }
      }
      runCatching { scanner.close() }
      analysisExecutor.shutdown()
    }
  }

  LaunchedEffect(torchEnabled, boundCamera) {
    boundCamera?.cameraControl?.enableTorch(torchEnabled)
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(250.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(Color.Black),
  ) {
    AndroidView(
      factory = { previewView },
      modifier = Modifier.fillMaxSize(),
    )

    // Framing reticle & top instructions
    Surface(
      color = Color.Black.copy(alpha = 0.6f),
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 12.dp),
    ) {
      Text(
        text = scanLabel,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
      )
    }

    // Center viewfinder border
    Surface(
      modifier = Modifier
        .size(width = 210.dp, height = 140.dp)
        .align(Alignment.Center),
      color = Color.Transparent,
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {}

    // Torch button
    IconButton(
      onClick = { torchEnabled = !torchEnabled },
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(10.dp)
        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)),
    ) {
      Icon(
        imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
        contentDescription = "Linterna",
        tint = if (torchEnabled) Color.Yellow else Color.White,
      )
    }
  }
}
