package com.electrodomesticos.registry.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.electrodomesticos.registry.data.local.ApplianceSqliteRepository
import com.electrodomesticos.registry.data.model.CatalogAppliance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScannerUiState(
  val isScanning: Boolean = true,
  val codeInput: String = "",
)

data class CatalogUiState(
  val query: String = "",
  val selectedType: String = "Todos",
  val isAddEditDialogOpen: Boolean = false,
  val editingItem: CatalogAppliance? = null,
  val viewingHtmlItem: CatalogAppliance? = null,
  val statusMessage: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

  private val sqliteRepository = ApplianceSqliteRepository(application)

  private val _scannerState = MutableStateFlow(ScannerUiState())
  val scannerState: StateFlow<ScannerUiState> = _scannerState.asStateFlow()

  private val _catalogState = MutableStateFlow(CatalogUiState())
  val catalogState: StateFlow<CatalogUiState> = _catalogState.asStateFlow()

  val availableTypes: StateFlow<List<String>> = sqliteRepository.itemsFlow
    .map { items ->
      val dynamicTypes = items.map { it.tipo.trim() }.filter { it.isNotEmpty() }.distinct()
      listOf("Todos") + dynamicTypes
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf("Todos"))

  val filteredCatalog: StateFlow<List<CatalogAppliance>> = combine(
    sqliteRepository.itemsFlow,
    _catalogState,
  ) { items, state ->
    val tokens = state.query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    items.filter { item ->
      val typeMatches = state.selectedType.equals("Todos", ignoreCase = true) ||
        item.tipo.equals(state.selectedType, ignoreCase = true)
      if (!typeMatches) return@filter false
      if (tokens.isEmpty()) return@filter true

      val searchable = listOf(
        item.tipo,
        item.marca,
        item.capacidad,
        item.color,
        item.precio.orEmpty(),
        item.codigo,
      ).joinToString(" ")
      tokens.all { token -> searchable.contains(token, ignoreCase = true) }
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  // --- Screen 1: Barcode Scanner (Copy-only, no search) ---

  fun onBarcodeDetected(rawCode: String) {
    _scannerState.update {
      it.copy(
        isScanning = false,
        codeInput = rawCode.trim(),
      )
    }
  }

  fun onScannerCodeInputChange(value: String) {
    _scannerState.update { it.copy(codeInput = value) }
  }

  fun resumeScanner() {
    _scannerState.update { it.copy(isScanning = true) }
  }

  // --- Screen 2: Product Catalog ---

  fun updateCatalogQuery(query: String) {
    _catalogState.update { it.copy(query = query) }
  }

  fun selectTypeFilter(tipo: String) {
    _catalogState.update { it.copy(selectedType = tipo) }
  }

  fun openAddDialog() {
    _catalogState.update {
      it.copy(isAddEditDialogOpen = true, editingItem = null, statusMessage = null)
    }
  }

  fun openEditDialog(item: CatalogAppliance) {
    _catalogState.update {
      it.copy(isAddEditDialogOpen = true, editingItem = item, statusMessage = null)
    }
  }

  fun dismissAddEditDialog() {
    _catalogState.update { it.copy(isAddEditDialogOpen = false, editingItem = null) }
  }

  fun saveAppliance(item: CatalogAppliance) {
    viewModelScope.launch(Dispatchers.IO) {
      if (item.id > 0L) {
        sqliteRepository.update(item)
      } else {
        sqliteRepository.insert(item)
      }
      _catalogState.update {
        it.copy(
          isAddEditDialogOpen = false,
          editingItem = null,
          statusMessage = if (item.id > 0L) "Producto actualizado" else "Producto añadido",
        )
      }
    }
  }

  fun deleteAppliance(item: CatalogAppliance) {
    viewModelScope.launch(Dispatchers.IO) {
      sqliteRepository.delete(item.id)
      _catalogState.update {
        it.copy(
          isAddEditDialogOpen = false,
          editingItem = null,
          statusMessage = "Producto eliminado",
        )
      }
    }
  }

  fun viewHtmlForItem(item: CatalogAppliance?) {
    _catalogState.update { it.copy(viewingHtmlItem = item) }
  }

  fun exportDatabaseToUri(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      val result = runCatching {
        val resolver = getApplication<Application>().contentResolver
        resolver.openOutputStream(uri)?.use { out ->
          sqliteRepository.exportDatabaseTo(out)
        } ?: error("No se pudo abrir el archivo de destino.")
      }
      _catalogState.update {
        it.copy(
          statusMessage = result.fold(
            onSuccess = { "Base de datos SQLite exportada correctamente." },
            onFailure = { err -> "Error al exportar BD: ${err.localizedMessage ?: "desconocido"}" },
          ),
        )
      }
    }
  }

  fun importDatabaseFromUri(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      val result = runCatching {
        val resolver = getApplication<Application>().contentResolver
        resolver.openInputStream(uri)?.use { input ->
          sqliteRepository.importDatabaseFrom(input, replaceExisting = true)
        } ?: error("No se pudo abrir el archivo seleccionado.")
      }
      _catalogState.update {
        it.copy(
          statusMessage = result.fold(
            onSuccess = { count -> "Base de datos importada ($count producto(s))." },
            onFailure = { err -> "Error al importar BD: ${err.localizedMessage ?: "archivo inválido"}" },
          ),
        )
      }
    }
  }

  fun dismissCatalogStatus() {
    _catalogState.update { it.copy(statusMessage = null) }
  }

  override fun onCleared() {
    sqliteRepository.close()
    super.onCleared()
  }
}
