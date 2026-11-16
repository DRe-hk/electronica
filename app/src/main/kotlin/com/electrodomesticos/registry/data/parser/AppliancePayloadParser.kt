package com.electrodomesticos.registry.data.parser

import com.electrodomesticos.registry.data.model.ParsedAppliancePayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup

object AppliancePayloadParser {

  private val json = Json { ignoreUnknownKeys = true }

  private val tipoKeys = setOf("tipo", "type", "categoria", "category", "producto", "product_type")
  private val marcaKeys = setOf("marca", "brand", "fabricante", "manufacturer")
  private val capacidadKeys = setOf(
    "capacidad", "kilaje", "pulgadas", "kilaje_capacidad_pulgadas",
    "capacity", "size", "medida", "tamano", "tamaño", "litros", "kilos", "kg", "inches",
  )
  private val colorKeys = setOf("color", "acabado", "colour")
  private val imageKeys = setOf(
    "imagen", "image", "image_url", "imageurl", "url_imagen",
    "imagen_url", "img", "foto", "thumbnail", "picture",
  )
  private val codigoKeys = setOf("codigo", "código", "barcode", "modelo", "model", "sku", "referencia")
  private val htmlKeys = setOf("html", "html_content", "htmlcontent", "detalle_html", "ficha_html")

  fun parse(
    jsonOrCombinedInput: String,
    separateHtmlInput: String = "",
  ): ParsedAppliancePayload {
    val cleanedPrimary = stripCodeFences(jsonOrCombinedInput.trim())
    val cleanedSeparateHtml = stripHtmlFences(separateHtmlInput.trim())

    val extractedJsonSpan = extractFirstJsonObject(cleanedPrimary)
    val rawJson = extractedJsonSpan?.text.orEmpty()
    val flatMap = if (rawJson.isNotEmpty()) parseJsonToFlatMap(rawJson) else emptyMap()

    // Determine HTML content: explicit separate box > embedded JSON field > remaining HTML in combined input
    val remainderHtml = if (extractedJsonSpan != null) {
      val before = cleanedPrimary.substring(0, extractedJsonSpan.start).trim()
      val after = cleanedPrimary.substring(extractedJsonSpan.endExclusive).trim()
      stripHtmlFences(listOf(before, after).filter { it.isNotBlank() }.joinToString("\n"))
    } else if (looksLikeHtml(cleanedPrimary)) {
      cleanedPrimary
    } else {
      ""
    }

    val embeddedHtml = findFirstValue(flatMap, htmlKeys)
    val resolvedHtml = when {
      cleanedSeparateHtml.isNotBlank() -> cleanedSeparateHtml
      embeddedHtml.isNotBlank() -> embeddedHtml
      looksLikeHtml(remainderHtml) -> remainderHtml
      else -> ""
    }

    var tipo = findFirstValue(flatMap, tipoKeys)
    var marca = findFirstValue(flatMap, marcaKeys)
    var capacidad = joinDistinctValues(flatMap, capacidadKeys)
    var color = findFirstValue(flatMap, colorKeys)
    var imageUrl = findFirstValue(flatMap, imageKeys)
    var codigo = findFirstValue(flatMap, codigoKeys)

    if (resolvedHtml.isNotBlank()) {
      val htmlFallbacks = extractFallbacksFromHtml(resolvedHtml)
      if (imageUrl.isBlank()) imageUrl = htmlFallbacks.imageUrl
      if (tipo.isBlank()) tipo = htmlFallbacks.tipo
      if (marca.isBlank()) marca = htmlFallbacks.marca
      if (capacidad.isBlank()) capacidad = htmlFallbacks.capacidad
      if (color.isBlank()) color = htmlFallbacks.color
      if (codigo.isBlank()) codigo = htmlFallbacks.codigo
    }

    return ParsedAppliancePayload(
      tipo = tipo,
      marca = marca,
      capacidad = capacidad,
      color = color,
      imageUrl = imageUrl,
      codigo = codigo,
      htmlContent = resolvedHtml,
      rawJson = rawJson,
    )
  }

  private data class JsonSpan(val start: Int, val endExclusive: Int, val text: String)

  private fun extractFirstJsonObject(text: String): JsonSpan? {
    val start = text.indexOf('{')
    if (start < 0) return null

    var depth = 0
    var inString = false
    var escaped = false

    for (i in start until text.length) {
      val ch = text[i]
      if (inString) {
        if (escaped) {
          escaped = false
        } else if (ch == '\\') {
          escaped = true
        } else if (ch == '"') {
          inString = false
        }
      } else {
        when (ch) {
          '"' -> inString = true
          '{' -> depth++
          '}' -> {
            depth--
            if (depth == 0) {
              val candidate = text.substring(start, i + 1)
              val isValid = runCatching { json.parseToJsonElement(candidate) is JsonObject }.getOrDefault(false)
              return if (isValid) JsonSpan(start, i + 1, candidate) else null
            }
          }
        }
      }
    }
    return null
  }

  private fun parseJsonToFlatMap(rawJson: String): Map<String, String> {
    val root = runCatching { json.parseToJsonElement(rawJson) }.getOrNull() as? JsonObject ?: return emptyMap()
    val result = linkedMapOf<String, String>()

    fun collect(obj: JsonObject) {
      for ((key, value) in obj) {
        val normalizedKey = key.trim().lowercase()
        when (value) {
          is JsonNull -> Unit
          is JsonPrimitive -> {
            val content = value.content.trim()
            if (content.isNotEmpty() && !content.equals("null", ignoreCase = true) && normalizedKey !in result) {
              result[normalizedKey] = content
            }
          }
          is JsonObject -> collect(value)
          is JsonArray -> {
            if (normalizedKey in imageKeys) {
              val firstImg = value.firstOrNull()
                ?.takeIf { it !is JsonNull }
                ?.let { (it as? JsonPrimitive)?.content?.trim() }
                ?.takeIf { !it.equals("null", ignoreCase = true) }
                .orEmpty()
              if (firstImg.isNotEmpty() && normalizedKey !in result) {
                result[normalizedKey] = firstImg
              }
            }
          }
        }
      }
    }

    collect(root)
    return result
  }

  private fun findFirstValue(map: Map<String, String>, keys: Set<String>): String {
    for (key in keys) {
      val value = map[key]
      if (!value.isNullOrBlank() && !value.equals("null", ignoreCase = true)) return value
    }
    return ""
  }

  private fun joinDistinctValues(map: Map<String, String>, keys: Set<String>): String {
    return keys
      .mapNotNull { key -> map[key]?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) } }
      .distinct()
      .joinToString(" / ")
  }

  private fun extractFallbacksFromHtml(html: String): ParsedAppliancePayload {
    val doc = Jsoup.parse(html)
    val imgUrl = doc.selectFirst("img[src]")?.attr("src")?.trim().orEmpty()
      .ifBlank { doc.selectFirst("meta[property=og:image]")?.attr("content")?.trim().orEmpty() }

    val labelValuePairs = linkedMapOf<String, String>()

    // Check table rows <tr><th>Label</th><td>Value</td></tr> or <tr><td>Label</td><td>Value</td></tr>
    for (row in doc.select("tr")) {
      val cells = row.select("th, td")
      if (cells.size >= 2) {
        val key = cells[0].text().trim().trimEnd(':').lowercase()
        val value = cells[1].text().trim()
        if (key.isNotEmpty() && value.isNotEmpty()) {
          labelValuePairs.putIfAbsent(key, value)
        }
      }
    }

    // Check definition lists <dt>Label</dt><dd>Value</dd>
    for (dt in doc.select("dt")) {
      val dd = dt.nextElementSibling()
      if (dd != null && dd.tagName().equals("dd", ignoreCase = true)) {
        val key = dt.text().trim().trimEnd(':').lowercase()
        val value = dd.text().trim()
        if (key.isNotEmpty() && value.isNotEmpty()) {
          labelValuePairs.putIfAbsent(key, value)
        }
      }
    }

    // Check data-* attributes on root container
    for (el in doc.select("[data-tipo], [data-marca], [data-capacidad], [data-color], [data-imagen]")) {
      for (attr in el.attributes()) {
        if (attr.key.startsWith("data-")) {
          val k = attr.key.removePrefix("data-").lowercase()
          if (attr.value.isNotBlank()) labelValuePairs.putIfAbsent(k, attr.value.trim())
        }
      }
    }

    return ParsedAppliancePayload(
      tipo = findFirstValue(labelValuePairs, tipoKeys),
      marca = findFirstValue(labelValuePairs, marcaKeys),
      capacidad = findFirstValue(labelValuePairs, capacidadKeys),
      color = findFirstValue(labelValuePairs, colorKeys),
      imageUrl = imgUrl,
      codigo = findFirstValue(labelValuePairs, codigoKeys),
    )
  }

  private fun looksLikeHtml(text: String): Boolean {
    return text.contains('<') && text.contains('>')
  }

  private fun stripCodeFences(text: String): String {
    return text
      .replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
      .replace(Regex("```\\s*$"), "")
      .trim()
  }

  private fun stripHtmlFences(text: String): String {
    return text
      .replace(Regex("^```(?:html)?\\s*", RegexOption.IGNORE_CASE), "")
      .replace(Regex("```\\s*$"), "")
      .trim()
  }
}
