package com.electrodomesticos.registry.ui.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.electrodomesticos.registry.ui.ScannerUiState

@Composable
fun ScannerScreen(
  state: ScannerUiState,
  onCodeDetected: (String) -> Unit,
  onCodeInputChange: (String) -> Unit,
  onResumeScan: () -> Unit,
  modifier: Modifier = Modifier,
) {
  @Suppress("DEPRECATION")
  val clipboardManager = LocalClipboardManager.current
  var copiedFeedback by remember(state.codeInput) { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    QrBarcodeScannerCard(
      isScanning = state.isScanning,
      scanLabel = "Apunta al código de barras o QR",
      pausedCode = state.codeInput.ifBlank { null },
      onCodeDetected = { detected ->
        copiedFeedback = false
        onCodeDetected(detected)
      },
      onResumeScan = {
        copiedFeedback = false
        onResumeScan()
      },
    )

    OutlinedTextField(
      value = state.codeInput,
      onValueChange = {
        copiedFeedback = false
        onCodeInputChange(it)
      },
      label = { Text("Código detectado (editable)") },
      placeholder = { Text("Escanea o edita el código aquí") },
      singleLine = true,
      trailingIcon = {
        if (state.codeInput.isNotEmpty()) {
          IconButton(onClick = {
            copiedFeedback = false
            onCodeInputChange("")
          }) {
            Icon(Icons.Default.Close, contentDescription = "Limpiar código")
          }
        }
      },
      modifier = Modifier.fillMaxWidth(),
    )

    Button(
      onClick = {
        if (state.codeInput.isNotBlank()) {
          clipboardManager.setText(AnnotatedString(state.codeInput))
          copiedFeedback = true
        }
      },
      enabled = state.codeInput.isNotBlank(),
      modifier = Modifier
        .fillMaxWidth()
        .height(54.dp),
    ) {
      Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(8.dp))
      Text("Copiar")
    }

    if (copiedFeedback) {
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        androidx.compose.foundation.layout.Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          Text(
            text = "Código copiado al portapapeles: ${state.codeInput}",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }
  }
}
