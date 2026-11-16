package com.electrodomesticos.registry.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.electrodomesticos.registry.data.model.CatalogAppliance
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ApplianceSqliteRepository(
  context: Context,
  dbName: String = DEFAULT_DB_NAME,
) : SQLiteOpenHelper(context.applicationContext, dbName, null, DB_VERSION), Closeable {

  private val _itemsFlow = MutableStateFlow<List<CatalogAppliance>>(emptyList())
  val itemsFlow: StateFlow<List<CatalogAppliance>> = _itemsFlow.asStateFlow()

  init {
    refreshFlow()
  }

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS $TABLE_CATALOG (
        $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $COL_TIPO TEXT NOT NULL,
        $COL_MARCA TEXT NOT NULL,
        $COL_CAPACIDAD TEXT NOT NULL,
        $COL_COLOR TEXT NOT NULL,
        $COL_PRECIO TEXT NULL,
        $COL_IMAGE_URL TEXT NOT NULL DEFAULT '',
        $COL_HTML_CONTENT TEXT NOT NULL DEFAULT '',
        $COL_RAW_JSON TEXT NOT NULL DEFAULT '',
        $COL_CODIGO TEXT NOT NULL DEFAULT '',
        $COL_CREATED_AT INTEGER NOT NULL
      )
      """.trimIndent()
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_catalog_tipo ON $TABLE_CATALOG($COL_TIPO)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_catalog_marca ON $TABLE_CATALOG($COL_MARCA)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_catalog_codigo ON $TABLE_CATALOG($COL_CODIGO)")
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

  fun insert(item: CatalogAppliance): Long {
    val sanitized = sanitize(item)
    val id = writableDatabase.insertOrThrow(TABLE_CATALOG, null, sanitized.toContentValues())
    refreshFlow()
    return id
  }

  fun update(item: CatalogAppliance): Int {
    val sanitized = sanitize(item)
    val count = writableDatabase.update(
      TABLE_CATALOG,
      sanitized.toContentValues(),
      "$COL_ID = ?",
      arrayOf(sanitized.id.toString()),
    )
    refreshFlow()
    return count
  }

  fun delete(id: Long): Int {
    val count = writableDatabase.delete(TABLE_CATALOG, "$COL_ID = ?", arrayOf(id.toString()))
    refreshFlow()
    return count
  }

  fun getById(id: Long): CatalogAppliance? {
    readableDatabase.rawQuery(
      "SELECT * FROM $TABLE_CATALOG WHERE $COL_ID = ? LIMIT 1",
      arrayOf(id.toString()),
    ).use { cursor ->
      return if (cursor.moveToFirst()) readRow(cursor) else null
    }
  }

  fun getAll(): List<CatalogAppliance> {
    readableDatabase.rawQuery(
      "SELECT * FROM $TABLE_CATALOG ORDER BY $COL_CREATED_AT DESC, $COL_ID DESC",
      null,
    ).use { cursor ->
      return readList(cursor)
    }
  }

  fun search(query: String, tipoFilter: String? = null): List<CatalogAppliance> {
    val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val whereParts = mutableListOf<String>()
    val args = mutableListOf<String>()

    if (!tipoFilter.isNullOrBlank() && !tipoFilter.equals("Todos", ignoreCase = true)) {
      whereParts += "LOWER(TRIM($COL_TIPO)) = LOWER(?)"
      args += tipoFilter.trim()
    }

    for (token in tokens) {
      val like = "%$token%"
      whereParts += """
        (
          $COL_TIPO LIKE ? COLLATE NOCASE
          OR $COL_MARCA LIKE ? COLLATE NOCASE
          OR $COL_CAPACIDAD LIKE ? COLLATE NOCASE
          OR $COL_COLOR LIKE ? COLLATE NOCASE
          OR COALESCE($COL_PRECIO, '') LIKE ? COLLATE NOCASE
          OR $COL_CODIGO LIKE ? COLLATE NOCASE
        )
      """.trimIndent()
      repeat(6) { args += like }
    }

    val whereSql = if (whereParts.isEmpty()) "" else "WHERE ${whereParts.joinToString(" AND ")}"
    readableDatabase.rawQuery(
      "SELECT * FROM $TABLE_CATALOG $whereSql ORDER BY $COL_CREATED_AT DESC, $COL_ID DESC",
      args.toTypedArray(),
    ).use { cursor ->
      return readList(cursor)
    }
  }

  private fun refreshFlow() {
    _itemsFlow.value = getAll()
  }

  private fun sanitize(item: CatalogAppliance): CatalogAppliance {
    val cleanPrice = item.precio?.trim()?.takeIf { it.isNotEmpty() }
    return item.copy(
      tipo = item.tipo.trim(),
      marca = item.marca.trim(),
      capacidad = item.capacidad.trim(),
      color = item.color.trim(),
      precio = cleanPrice,
      imageUrl = item.imageUrl.trim(),
      htmlContent = item.htmlContent,
      rawJson = item.rawJson,
      codigo = item.codigo.trim(),
    )
  }

  private fun CatalogAppliance.toContentValues(): ContentValues {
    return ContentValues().apply {
      put(COL_TIPO, tipo)
      put(COL_MARCA, marca)
      put(COL_CAPACIDAD, capacidad)
      put(COL_COLOR, color)
      if (precio == null) putNull(COL_PRECIO) else put(COL_PRECIO, precio)
      put(COL_IMAGE_URL, imageUrl)
      put(COL_HTML_CONTENT, htmlContent)
      put(COL_RAW_JSON, rawJson)
      put(COL_CODIGO, codigo)
      put(COL_CREATED_AT, createdAt)
    }
  }

  private fun readList(cursor: Cursor): List<CatalogAppliance> {
    val items = mutableListOf<CatalogAppliance>()
    while (cursor.moveToNext()) {
      items += readRow(cursor)
    }
    return items
  }

  private fun readRow(cursor: Cursor): CatalogAppliance {
    val precioIdx = cursor.getColumnIndexOrThrow(COL_PRECIO)
    val precioValue = if (cursor.isNull(precioIdx)) null else cursor.getString(precioIdx)?.trim()?.takeIf { it.isNotEmpty() }
    return CatalogAppliance(
      id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
      tipo = cursor.getString(cursor.getColumnIndexOrThrow(COL_TIPO)),
      marca = cursor.getString(cursor.getColumnIndexOrThrow(COL_MARCA)),
      capacidad = cursor.getString(cursor.getColumnIndexOrThrow(COL_CAPACIDAD)),
      color = cursor.getString(cursor.getColumnIndexOrThrow(COL_COLOR)),
      precio = precioValue,
      imageUrl = cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_URL)),
      htmlContent = cursor.getString(cursor.getColumnIndexOrThrow(COL_HTML_CONTENT)),
      rawJson = cursor.getString(cursor.getColumnIndexOrThrow(COL_RAW_JSON)),
      codigo = cursor.getString(cursor.getColumnIndexOrThrow(COL_CODIGO)),
      createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT)),
    )
  }

  companion object {
    const val DEFAULT_DB_NAME = "electro_catalogo.db"
    private const val DB_VERSION = 1
    const val TABLE_CATALOG = "catalog_appliances"

    private const val COL_ID = "id"
    private const val COL_TIPO = "tipo"
    private const val COL_MARCA = "marca"
    private const val COL_CAPACIDAD = "capacidad"
    private const val COL_COLOR = "color"
    private const val COL_PRECIO = "precio"
    private const val COL_IMAGE_URL = "image_url"
    private const val COL_HTML_CONTENT = "html_content"
    private const val COL_RAW_JSON = "raw_json"
    private const val COL_CODIGO = "codigo"
    private const val COL_CREATED_AT = "created_at"
  }
}
