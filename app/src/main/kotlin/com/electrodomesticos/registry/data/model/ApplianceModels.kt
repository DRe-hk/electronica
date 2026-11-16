package com.electrodomesticos.registry.data.model

data class CatalogAppliance(
  val id: Long = 0L,
  val tipo: String,
  val marca: String,
  val capacidad: String,
  val color: String,
  val precio: String? = null,
  val imageUrl: String,
  val htmlContent: String,
  val rawJson: String = "",
  val codigo: String = "",
  val createdAt: Long = System.currentTimeMillis(),
)

data class ParsedAppliancePayload(
  val tipo: String = "",
  val marca: String = "",
  val capacidad: String = "",
  val color: String = "",
  val imageUrl: String = "",
  val codigo: String = "",
  val htmlContent: String = "",
  val rawJson: String = "",
)
