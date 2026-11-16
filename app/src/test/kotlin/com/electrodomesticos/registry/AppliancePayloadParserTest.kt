package com.electrodomesticos.registry

import com.electrodomesticos.registry.data.parser.AppliancePayloadParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppliancePayloadParserTest {

  @Test
  fun `parses nested valores object ignoring JsonNull fields`() {
    val jsonInput = """
      {
        "imagen": "https://example.com/images/lg-refri.jpg",
        "tipo": "refrigerador",
        "marca": "LG",
        "valores": {
          "kilaje": null,
          "capacidad": "420 litros",
          "pulgadas": null
        },
        "color": "Silver"
      }
    """.trimIndent()

    val parsed = AppliancePayloadParser.parse(jsonInput, "<div>Ficha HTML</div>")
    assertEquals("https://example.com/images/lg-refri.jpg", parsed.imageUrl)
    assertEquals("refrigerador", parsed.tipo)
    assertEquals("LG", parsed.marca)
    assertEquals("420 litros", parsed.capacidad)
    assertEquals("Silver", parsed.color)
    assertEquals("<div>Ficha HTML</div>", parsed.htmlContent)
  }

  @Test
  fun `parses separate JSON characteristics including image link and raw HTML`() {
    val jsonInput = """
      {
        "tipo": "Refrigerador",
        "marca": "Samsung",
        "capacidad": "384 L",
        "color": "Acero Inoxidable",
        "imagen": "https://example.com/images/rt38.jpg",
        "codigo": "RT38K5930S8"
      }
    """.trimIndent()

    val htmlInput = """
      <html>
        <body>
          <h1>Refrigeradora Samsung 384L</h1>
          <p>Tecnología Twin Cooling Plus</p>
        </body>
      </html>
    """.trimIndent()

    val parsed = AppliancePayloadParser.parse(jsonInput, htmlInput)
    assertEquals("Refrigerador", parsed.tipo)
    assertEquals("Samsung", parsed.marca)
    assertEquals("384 L", parsed.capacidad)
    assertEquals("Acero Inoxidable", parsed.color)
    assertEquals("https://example.com/images/rt38.jpg", parsed.imageUrl)
    assertEquals("RT38K5930S8", parsed.codigo)
    assertEquals(htmlInput, parsed.htmlContent)
  }

  @Test
  fun `parses combined markdown fenced JSON and HTML plus embedded html field`() {
    val combined = """
      ```json
      {
        "categoria": "Lavadora",
        "brand": "LG",
        "kilaje": "22 kg",
        "color": "Negro",
        "image_url": "https://example.com/lg-22kg.png"
      }
      ```
      <div class="ficha"><h2>Lavadora LG AI DD</h2><ul><li>Motor Inverter</li></ul></div>
    """.trimIndent()

    val parsed = AppliancePayloadParser.parse(combined)
    assertEquals("Lavadora", parsed.tipo)
    assertEquals("LG", parsed.marca)
    assertEquals("22 kg", parsed.capacidad)
    assertEquals("Negro", parsed.color)
    assertEquals("https://example.com/lg-22kg.png", parsed.imageUrl)
    assertTrue(parsed.htmlContent.contains("<h2>Lavadora LG AI DD</h2>"))
  }

  @Test
  fun `extracts image and table characteristics from pure HTML when JSON is omitted`() {
    val htmlOnly = """
      <div>
        <img src="https://example.com/tv-55.jpg" alt="TV"/>
        <table>
          <tr><th>Tipo</th><td>TV</td></tr>
          <tr><th>Marca</th><td>TCL</td></tr>
          <tr><th>Pulgadas</th><td>55"</td></tr>
          <tr><th>Color</th><td>Negro</td></tr>
        </table>
      </div>
    """.trimIndent()

    val parsed = AppliancePayloadParser.parse("", htmlOnly)
    assertEquals("TV", parsed.tipo)
    assertEquals("TCL", parsed.marca)
    assertEquals("55\"", parsed.capacidad)
    assertEquals("Negro", parsed.color)
    assertEquals("https://example.com/tv-55.jpg", parsed.imageUrl)
    assertEquals(htmlOnly, parsed.htmlContent)
  }
}
