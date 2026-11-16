# ElectroRegistro

Aplicación Android (Kotlin + Jetpack Compose + SQLite) enfocada en el escaneo rápido de códigos de barras y la gestión visual de un catálogo de electrodomésticos a partir de plantillas **JSON** y **HTML**.

## Funcionalidades principales

### 1. Lector de Códigos (`Escáner`)
- Lectura de códigos de barras y QR mediante **CameraX** y **Google ML Kit** (`EAN-13`, `EAN-8`, `UPC-A`, `UPC-E`, `Code-128`, `Code-39`, `QR`, `DataMatrix`).
- Auto-pausa inmediata al detectar un código, evitando lecturas repetidas.
- Campo de texto **editable** para ajustar o corregir el código leído.
- Botón único de **Copiar** al portapapeles (sin búsquedas externas automáticas).

### 2. Catálogo de Electrodomésticos (`Productos`)
- **Botón `+ Añadir`** en la cabecera para pegar o **importar archivos `.json` y `.html`** directamente desde el almacenamiento del teléfono.
- Cada fila muestra:
  - **Imagen** cargada desde la URL del JSON
  - **Tipo** (Refrigerador, TV, Lavadora, Congeladora, etc.)
  - **Marca**
  - **Kilaje / Capacidad / Pulgadas**
  - **Color**
  - **Precio** (manual y opcional/nulo al registrar)
  - Botones **Editar** y **Ver más**
- **Edición completa** de todos los campos tanto antes de insertar como después de guardar en **SQLite**.
- **Vista "Ver más"** con renderizado HTML nativo tal cual se recibe mediante `WebView`.
- Búsqueda instantánea por texto y filtros dinámicos por tipo de producto.

---

## Formato de entrada JSON y HTML

Ejemplo de JSON aceptado para poblar la fila (ver carpeta [`examples/`](examples)):

```json
{
  "imagen": "https://ejemplo.com/imagen-producto.jpg",
  "tipo": "refrigerador",
  "marca": "LG",
  "valores": {
    "kilaje": null,
    "capacidad": "384 litros",
    "pulgadas": null
  },
  "color": "Silver"
}
```

> El precio **no** se extrae del JSON ni del HTML; se ingresa de forma manual en el formulario y puede quedar vacío (`null`) al registrar.

---

## Estructura del Monorepo

```text
.
├── app/                     # Aplicación Android nativa (Kotlin + Jetpack Compose)
│   └── src/
│       ├── main/kotlin/     # Escáner, Parser JSON/HTML, SQLite y Catálogo UI
│       └── test/kotlin/     # Pruebas unitarias (Parser + SQLite Robolectric)
├── examples/                # Plantillas de ejemplo JSON y HTML
├── gradle/                  # Wrapper y catálogo de dependencias (libs.versions.toml)
└── README.md
```

---

## Compilación y Pruebas

Requisitos: JDK 17/21 y Android SDK (`compileSdk 36`).

```bash
# Ejecutar pruebas unitarias
./gradlew testDebugUnitTest

# Compilar APK Debug
./gradlew assembleDebug
```

El APK compilado también está disponible directamente en la sección **Releases** de este repositorio.
