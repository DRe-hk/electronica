package com.electrodomesticos.registry.ui.catalog

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.electrodomesticos.registry.data.model.CatalogAppliance
import com.electrodomesticos.registry.data.parser.AppliancePayloadParser
import com.electrodomesticos.registry.ui.CatalogUiState
import com.electrodomesticos.registry.ui.components.RemoteProductImage

@Composable
fun CatalogScreen(
  state: CatalogUiState,
  items: List<CatalogAppliance>,
  availableTypes: List<String>,
  onQueryChange: (String) -> Unit,
  onSelectTypeFilter: (String) -> Unit,
  onOpenAddDialog: () -> Unit,
  onOpenEditDialog: (CatalogAppliance) -> Unit,
  onDismissAddEditDialog: () -> Unit,
  onSaveItem: (CatalogAppliance) -> Unit,
  onDeleteItem: (CatalogAppliance) -> Unit,
  onViewHtml: (CatalogAppliance?) -> Unit,
  onDismissStatus: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Top bar: Search + "+ Añadir" button
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = state.query,
        onValueChange = onQueryChange,
        label = { Text("Buscar producto") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
          if (state.query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
              Icon(Icons.Default.Close, contentDescription = "Limpiar")
            }
          }
        },
        singleLine = true,
        modifier = Modifier.weight(1f),
      )

      Button(
        onClick = onOpenAddDialog,
        modifier = Modifier.height(54.dp),
      ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("Añadir")
      }
    }

    // Type filter chips
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      availableTypes.forEach { tipo ->
        FilterChip(
          selected = state.selectedType.equals(tipo, ignoreCase = true),
          onClick = { onSelectTypeFilter(tipo) },
          label = { Text(tipo) },
        )
      }
    }

    state.statusMessage?.let { msg ->
      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
          IconButton(onClick = onDismissStatus, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar", modifier = Modifier.size(16.dp))
          }
        }
      }
    }

    Text(
      text = "${items.size} producto(s)",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (items.isEmpty()) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "No hay productos registrados",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "Pulsa '+ Añadir' arriba para pegar el JSON y el HTML de un electrodoméstico.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        items(items, key = { it.id }) { item ->
          ApplianceProductRow(
            item = item,
            onViewMore = { onViewHtml(item) },
            onEdit = { onOpenEditDialog(item) },
          )
        }
      }
    }
  }

  if (state.isAddEditDialogOpen) {
    AddEditApplianceDialog(
      initialItem = state.editingItem,
      onDismiss = onDismissAddEditDialog,
      onSave = onSaveItem,
      onDelete = state.editingItem?.let { existing ->
        { onDeleteItem(existing) }
      },
    )
  }

  state.viewingHtmlItem?.let { htmlItem ->
    HtmlVerMasDialog(
      item = htmlItem,
      onDismiss = { onViewHtml(null) },
    )
  }
}

@Composable
private fun ApplianceProductRow(
  item: CatalogAppliance,
  onViewMore: () -> Unit,
  onEdit: () -> Unit,
) {
  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
      ) {
        RemoteProductImage(
          imageUrl = item.imageUrl,
          contentDescription = "${item.tipo} ${item.marca}",
          size = 88.dp,
        )

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = RoundedCornerShape(6.dp),
            ) {
              Text(
                text = item.tipo.ifBlank { "Producto" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }

            Text(
              text = item.precio?.let { "Precio: $it" } ?: "Sin precio",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = if (item.precio != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Text(
            text = item.marca.ifBlank { "Sin marca" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )

          Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            if (item.capacidad.isNotBlank()) {
              Text(
                text = "Cap/Tam: ${item.capacidad}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (item.color.isNotBlank()) {
              Text(
                text = "Color: ${item.color}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          if (item.codigo.isNotBlank()) {
            Text(
              text = "Cód: ${item.codigo}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      HorizontalDivider()

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(onClick = onEdit) {
          Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Editar")
        }

        Button(
          onClick = onViewMore,
          enabled = item.htmlContent.isNotBlank(),
        ) {
          Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Ver más")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditApplianceDialog(
  initialItem: CatalogAppliance?,
  onDismiss: () -> Unit,
  onSave: (CatalogAppliance) -> Unit,
  onDelete: (() -> Unit)?,
) {
  @Suppress("DEPRECATION")
  val clipboardManager = LocalClipboardManager.current
  val isEditing = initialItem != null

  var jsonInput by remember(initialItem) { mutableStateOf(initialItem?.rawJson.orEmpty()) }
  var htmlInput by remember(initialItem) { mutableStateOf(initialItem?.htmlContent.orEmpty()) }
  var tipo by remember(initialItem) { mutableStateOf(initialItem?.tipo.orEmpty()) }
  var marca by remember(initialItem) { mutableStateOf(initialItem?.marca.orEmpty()) }
  var capacidad by remember(initialItem) { mutableStateOf(initialItem?.capacidad.orEmpty()) }
  var color by remember(initialItem) { mutableStateOf(initialItem?.color.orEmpty()) }
  var precio by remember(initialItem) { mutableStateOf(initialItem?.precio.orEmpty()) }
  var imageUrl by remember(initialItem) { mutableStateOf(initialItem?.imageUrl.orEmpty()) }
  var codigo by remember(initialItem) { mutableStateOf(initialItem?.codigo.orEmpty()) }
  var validationError by remember { mutableStateOf<String?>(null) }

  fun applyParsedPayload(rawJsonText: String, rawHtmlText: String) {
    val parsed = AppliancePayloadParser.parse(rawJsonText, rawHtmlText)
    if (parsed.tipo.isNotBlank()) tipo = parsed.tipo
    if (parsed.marca.isNotBlank()) marca = parsed.marca
    if (parsed.capacidad.isNotBlank()) capacidad = parsed.capacidad
    if (parsed.color.isNotBlank()) color = parsed.color
    if (parsed.imageUrl.isNotBlank()) imageUrl = parsed.imageUrl
    if (parsed.codigo.isNotBlank()) codigo = parsed.codigo
    if (parsed.htmlContent.isNotBlank() && htmlInput.isBlank()) {
      htmlInput = parsed.htmlContent
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(if (isEditing) "Editar producto" else "Añadir producto (JSON / HTML)") },
          navigationIcon = {
            IconButton(onClick = onDismiss) {
              Icon(Icons.Default.Close, contentDescription = "Cerrar")
            }
          },
          actions = {
            if (onDelete != null) {
              IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
              }
            }
          },
        )
      },
    ) { innerPadding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "1. Pegar JSON y/o HTML",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = {
                  val clip = clipboardManager.getText()?.text.orEmpty()
                  if (clip.isNotBlank()) {
                    jsonInput = clip
                    applyParsedPayload(clip, htmlInput)
                  }
                },
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Pegar JSON")
              }

              OutlinedButton(
                onClick = {
                  val clip = clipboardManager.getText()?.text.orEmpty()
                  if (clip.isNotBlank()) {
                    htmlInput = clip
                    applyParsedPayload(jsonInput, clip)
                  }
                },
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Pegar HTML")
              }
            }

            OutlinedTextField(
              value = jsonInput,
              onValueChange = { newJson ->
                jsonInput = newJson
                applyParsedPayload(newJson, htmlInput)
              },
              label = { Text("JSON (tipo, marca, capacidad/kilaje/pulgadas, color, imagen)") },
              minLines = 3,
              maxLines = 6,
              modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
              value = htmlInput,
              onValueChange = { newHtml ->
                htmlInput = newHtml
                applyParsedPayload(jsonInput, newHtml)
              },
              label = { Text("HTML (para renderizar en 'Ver más')") },
              minLines = 3,
              maxLines = 6,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }

        Text(
          text = "2. Características editables antes de guardar",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RemoteProductImage(
            imageUrl = imageUrl,
            contentDescription = "Vista previa",
            size = 72.dp,
          )
          OutlinedTextField(
            value = imageUrl,
            onValueChange = { imageUrl = it },
            label = { Text("Link de imagen (URL)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }

        OutlinedTextField(
          value = tipo,
          onValueChange = { tipo = it; validationError = null },
          label = { Text("Tipo * (ej. Refrigerador, TV, Lavadora, Congeladora)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
          value = marca,
          onValueChange = { marca = it; validationError = null },
          label = { Text("Marca * (ej. LG, Samsung, Mabe)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
          value = capacidad,
          onValueChange = { capacidad = it },
          label = { Text("Kilaje / Capacidad / Pulgadas (ej. 22 kg, 384 L, 55\")") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
          value = color,
          onValueChange = { color = it },
          label = { Text("Color (ej. Negro, Acero Inoxidable)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
          value = precio,
          onValueChange = { precio = it },
          label = { Text("Precio (manual, opcional — puede quedar vacío)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
          value = codigo,
          onValueChange = { codigo = it },
          label = { Text("Código / Modelo (opcional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        validationError?.let { err ->
          Text(
            text = err,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          TextButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
          ) {
            Text("Cancelar")
          }
          Button(
            onClick = {
              if (tipo.isBlank() && marca.isBlank()) {
                validationError = "Ingresa al menos el Tipo o la Marca del producto."
                return@Button
              }
              val toSave = CatalogAppliance(
                id = initialItem?.id ?: 0L,
                tipo = tipo.trim(),
                marca = marca.trim(),
                capacidad = capacidad.trim(),
                color = color.trim(),
                precio = precio.trim().takeIf { it.isNotEmpty() },
                imageUrl = imageUrl.trim(),
                htmlContent = htmlInput,
                rawJson = jsonInput,
                codigo = codigo.trim(),
                createdAt = initialItem?.createdAt ?: System.currentTimeMillis(),
              )
              onSave(toSave)
            },
            modifier = Modifier
              .weight(2f)
              .height(50.dp),
          ) {
            Text(if (isEditing) "Guardar cambios" else "Insertar producto")
          }
        }

        Spacer(Modifier.height(16.dp))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HtmlVerMasDialog(
  item: CatalogAppliance,
  onDismiss: () -> Unit,
) {
  val hasViewportMeta = remember(item.htmlContent) {
    item.htmlContent.contains("viewport", ignoreCase = true)
  }
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = listOf(item.tipo, item.marca, item.capacidad).filter { it.isNotBlank() }.joinToString(" • "),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          navigationIcon = {
            IconButton(onClick = onDismiss) {
              Icon(Icons.Default.Close, contentDescription = "Cerrar")
            }
          },
        )
      },
    ) { innerPadding ->
      AndroidView(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        factory = { context ->
          WebView(context).apply {
            webViewClient = WebViewClient()
            settings.apply {
              javaScriptEnabled = false
              loadsImagesAutomatically = true
              useWideViewPort = hasViewportMeta
              loadWithOverviewMode = hasViewportMeta
              builtInZoomControls = true
              displayZoomControls = false
              mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            loadDataWithBaseURL(null, item.htmlContent, "text/html", "UTF-8", null)
          }
        },
        update = { webView ->
          webView.settings.useWideViewPort = hasViewportMeta
          webView.settings.loadWithOverviewMode = hasViewportMeta
          webView.loadDataWithBaseURL(null, item.htmlContent, "text/html", "UTF-8", null)
        },
      )
    }
  }
}
