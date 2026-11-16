package com.electrodomesticos.registry

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.electrodomesticos.registry.data.local.ApplianceSqliteRepository
import com.electrodomesticos.registry.data.model.CatalogAppliance
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApplianceSqliteRepositoryTest {

  private lateinit var repository: ApplianceSqliteRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    repository = ApplianceSqliteRepository(context, dbName = "test_catalog_${System.nanoTime()}.db")
  }

  @After
  fun tearDown() {
    repository.close()
  }

  @Test
  fun `inserts appliance with nullable price and preserves raw HTML`() {
    val rawHtml = "<div><h1>Lavadora LG 22kg</h1><p>Detalle completo</p></div>"
    val id = repository.insert(
      CatalogAppliance(
        tipo = "Lavadora",
        marca = "LG",
        capacidad = "22 kg",
        color = "Negro",
        precio = null,
        imageUrl = "https://example.com/lavadora.jpg",
        htmlContent = rawHtml,
        rawJson = """{"tipo":"Lavadora","marca":"LG"}""",
        codigo = "WM22VV2S6BG",
      )
    )

    assertTrue(id > 0)
    val stored = repository.getById(id)
    assertNotNull(stored)
    requireNotNull(stored)
    assertEquals("Lavadora", stored.tipo)
    assertEquals("LG", stored.marca)
    assertEquals("22 kg", stored.capacidad)
    assertEquals("Negro", stored.color)
    assertNull(stored.precio)
    assertEquals("https://example.com/lavadora.jpg", stored.imageUrl)
    assertEquals(rawHtml, stored.htmlContent)
  }

  @Test
  fun `updates all editable fields including manual price and supports search and type filter`() {
    val id = repository.insert(
      CatalogAppliance(
        tipo = "Refrigerador",
        marca = "Samsung",
        capacidad = "384 L",
        color = "Gris",
        precio = null,
        imageUrl = "https://example.com/refri1.jpg",
        htmlContent = "<p>Refri</p>",
      )
    )
    repository.insert(
      CatalogAppliance(
        tipo = "TV",
        marca = "LG",
        capacidad = "55\"",
        color = "Negro",
        precio = "2499.00",
        imageUrl = "https://example.com/tv.jpg",
        htmlContent = "<p>TV OLED</p>",
      )
    )

    val original = repository.getById(id)!!
    repository.update(
      original.copy(
        capacidad = "400 L",
        color = "Acero Inoxidable",
        precio = "2150.00",
        imageUrl = "https://example.com/refri2.jpg",
      )
    )

    val updated = repository.getById(id)!!
    assertEquals("400 L", updated.capacidad)
    assertEquals("Acero Inoxidable", updated.color)
    assertEquals("2150.00", updated.precio)
    assertEquals("https://example.com/refri2.jpg", updated.imageUrl)

    val tvOnly = repository.search("", tipoFilter = "TV")
    assertEquals(1, tvOnly.size)
    assertEquals("LG", tvOnly.first().marca)

    val searchSamsung = repository.search("Samsung Acero")
    assertEquals(1, searchSamsung.size)
    assertEquals(id, searchSamsung.first().id)

    repository.delete(id)
    assertNull(repository.getById(id))
  }
}
