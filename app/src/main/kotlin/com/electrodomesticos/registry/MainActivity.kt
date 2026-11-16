package com.electrodomesticos.registry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.electrodomesticos.registry.ui.AppViewModel
import com.electrodomesticos.registry.ui.catalog.CatalogScreen
import com.electrodomesticos.registry.ui.scanner.ScannerScreen
import com.electrodomesticos.registry.ui.theme.ElectroRegistroTheme

private enum class AppTab(val title: String, val icon: ImageVector) {
  SCANNER("Escáner", Icons.Default.QrCodeScanner),
  PRODUCTS("Productos", Icons.Default.Inventory2),
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      ElectroRegistroTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          ElectroRegistroApp()
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectroRegistroApp(
  viewModel: AppViewModel = viewModel(),
) {
  var currentTab by rememberSaveable { mutableStateOf(AppTab.SCANNER) }
  val scannerState by viewModel.scannerState.collectAsStateWithLifecycle()
  val catalogState by viewModel.catalogState.collectAsStateWithLifecycle()
  val filteredCatalog by viewModel.filteredCatalog.collectAsStateWithLifecycle()
  val availableTypes by viewModel.availableTypes.collectAsStateWithLifecycle()

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            when (currentTab) {
              AppTab.SCANNER -> "Lector de Códigos"
              AppTab.PRODUCTS -> "Catálogo de Productos"
            }
          )
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
      )
    },
    bottomBar = {
      NavigationBar {
        AppTab.entries.forEach { tab ->
          NavigationBarItem(
            selected = currentTab == tab,
            onClick = { currentTab = tab },
            icon = { Icon(tab.icon, contentDescription = tab.title) },
            label = { Text(tab.title) },
          )
        }
      }
    },
  ) { innerPadding ->
    when (currentTab) {
      AppTab.SCANNER -> {
        ScannerScreen(
          state = scannerState,
          onCodeDetected = viewModel::onBarcodeDetected,
          onCodeInputChange = viewModel::onScannerCodeInputChange,
          onResumeScan = viewModel::resumeScanner,
          modifier = Modifier.padding(innerPadding),
        )
      }
      AppTab.PRODUCTS -> {
        CatalogScreen(
          state = catalogState,
          items = filteredCatalog,
          availableTypes = availableTypes,
          onQueryChange = viewModel::updateCatalogQuery,
          onSelectTypeFilter = viewModel::selectTypeFilter,
          onOpenAddDialog = viewModel::openAddDialog,
          onOpenEditDialog = viewModel::openEditDialog,
          onDismissAddEditDialog = viewModel::dismissAddEditDialog,
          onSaveItem = viewModel::saveAppliance,
          onDeleteItem = viewModel::deleteAppliance,
          onViewHtml = viewModel::viewHtmlForItem,
          onExportDatabase = viewModel::exportDatabaseToUri,
          onImportDatabase = viewModel::importDatabaseFromUri,
          onDismissStatus = viewModel::dismissCatalogStatus,
          modifier = Modifier.padding(innerPadding),
        )
      }
    }
  }
}
