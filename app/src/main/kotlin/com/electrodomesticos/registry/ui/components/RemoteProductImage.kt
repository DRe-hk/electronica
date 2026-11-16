package com.electrodomesticos.registry.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private object RemoteImageLoader {
  private val cache = LruCache<String, ImageBitmap>(40)
  private val client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  suspend fun load(url: String): ImageBitmap? {
    val cleanUrl = url.trim()
    if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
      return null
    }
    cache.get(cleanUrl)?.let { return it }

    return withContext(Dispatchers.IO) {
      runCatching {
        val request = Request.Builder()
          .url(cleanUrl)
          .header("User-Agent", "Mozilla/5.0 (Android) ElectroRegistro/1.0")
          .get()
          .build()
        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) return@runCatching null
          val bytes = response.body?.bytes() ?: return@runCatching null
          val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
          val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxDim = 512)
          val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
          val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return@runCatching null
          val imageBitmap = bitmap.asImageBitmap()
          cache.put(cleanUrl, imageBitmap)
          imageBitmap
        }
      }.getOrNull()
    }
  }

  private fun calculateSampleSize(width: Int, height: Int, maxDim: Int): Int {
    var sample = 1
    while (width / sample > maxDim * 2 || height / sample > maxDim * 2) {
      sample *= 2
    }
    return sample
  }
}

@Composable
fun RemoteProductImage(
  imageUrl: String,
  contentDescription: String,
  size: Dp = 84.dp,
  modifier: Modifier = Modifier,
) {
  var bitmap by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
  var isLoading by remember(imageUrl) { mutableStateOf(false) }

  LaunchedEffect(imageUrl) {
    if (imageUrl.isBlank()) {
      bitmap = null
      isLoading = false
      return@LaunchedEffect
    }
    isLoading = true
    bitmap = RemoteImageLoader.load(imageUrl)
    isLoading = false
  }

  Box(
    modifier = modifier
      .size(size)
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    contentAlignment = Alignment.Center,
  ) {
    val loaded = bitmap
    when {
      loaded != null -> {
        Image(
          bitmap = loaded,
          contentDescription = contentDescription,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      }
      isLoading -> {
        CircularProgressIndicator(
          modifier = Modifier.size(22.dp),
          strokeWidth = 2.dp,
        )
      }
      else -> {
        Icon(
          imageVector = Icons.Default.DevicesOther,
          contentDescription = contentDescription,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(32.dp),
        )
      }
    }
  }
}
