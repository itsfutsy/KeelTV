package com.keeltv.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import com.keeltv.app.ui.theme.KeelTvColors
import com.keeltv.app.ui.theme.KeelTvTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext

@Composable
fun Modifier.appBackground(): Modifier {
  val colors = KeelTvTheme.colors
  return this.background(
    Brush.linearGradient(
      0f to colors.bgLift,
      0.55f to colors.bgApp,
      1f to colors.bgSink,
      start = Offset.Zero,
      end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    ),
  )
}

@Composable
fun AmbientArt(
  url: () -> String?,
  modifier: Modifier = Modifier,
  strength: Float = 0.5f,
  softness: Float = 1f,
) {
  val colors = KeelTvTheme.colors
  val context = LocalContext.current
  val recipe = remember(colors, strength, softness) { Recipe(colors, strength, softness) }
  val bare = remember(recipe) { plates.getOrPut(recipe.key) { bake(recipe, null).asImageBitmap() } }

  var settled by remember(bare) { mutableStateOf(bare) }
  LaunchedEffect(url, recipe, bare) {
    snapshotFlow { url().orEmpty() }.debounce(SETTLE_MS).collect { next ->
      settled = if (next.isEmpty()) bare else ambientPlate(context, next, recipe) ?: bare
    }
  }

  Box(modifier.fillMaxSize()) {
    Crossfade(targetState = settled, animationSpec = tween(420), label = "ambient") { shown ->
      Image(
        bitmap = shown,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.Low,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

private class Recipe(colors: KeelTvColors, val strength: Float, val softness: Float) {
  val app = colors.bgApp.toArgb()
  val lift = colors.bgLift.toArgb()
  val sink = colors.bgSink.toArgb()
  val key = "$app|$lift|$sink|$strength|$softness"
}

private const val PLATE_WIDTH = 192
private const val PLATE_HEIGHT = 108

private const val BLUR_FRACTION = 1f / 6f

private const val BLUR_PASSES = 3

private const val SETTLE_MS = 220L

private val plates = LruCache<String, ImageBitmap>(16)

private val missing = LruCache<String, Boolean>(256)

private fun <K : Any, V : Any> LruCache<K, V>.getOrPut(key: K, make: () -> V): V =
  get(key) ?: make().also { put(key, it) }

private suspend fun ambientPlate(
  context: android.content.Context,
  url: String,
  recipe: Recipe,
): ImageBitmap? {
  val key = "$url|${recipe.key}"
  plates.get(key)?.let { return it }
  if (missing.get(url) == true) return null
  return withContext(Dispatchers.Default) {
    plates.get(key)?.let { return@withContext it }
    val request = ImageRequest.Builder(context)
      .data(url)
      .size(PLATE_WIDTH, PLATE_HEIGHT)
      .scale(Scale.FILL)
      // The pixels are read back to blur them, which a bitmap living on the GPU cannot do.
      .allowHardware(false)
      .build()
    val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull()
    val art = (result as? SuccessResult)?.image as? BitmapImage
    if (art == null) {
      missing.put(url, true)
      return@withContext null
    }
    val plate = runCatching { bake(recipe, art.bitmap).asImageBitmap() }.getOrNull()
      ?: return@withContext null
    plates.put(key, plate)
    plate
  }
}

private val FULL = Rect(0, 0, PLATE_WIDTH, PLATE_HEIGHT)

private fun bake(recipe: Recipe, art: Bitmap?): Bitmap {
  val plate = Bitmap.createBitmap(PLATE_WIDTH, PLATE_HEIGHT, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(plate)
  val paint = Paint()

  paint.shader = LinearGradient(
    0f, 0f, PLATE_WIDTH.toFloat(), PLATE_HEIGHT.toFloat(),
    intArrayOf(recipe.lift, recipe.app, recipe.sink), floatArrayOf(0f, 0.55f, 1f),
    Shader.TileMode.CLAMP,
  )
  canvas.drawRect(0f, 0f, PLATE_WIDTH.toFloat(), PLATE_HEIGHT.toFloat(), paint)
  paint.shader = null

  if (art != null) {
    val filtered = Paint(Paint.FILTER_BITMAP_FLAG)
    filtered.alpha = (recipe.strength * 255f).toInt().coerceIn(0, 255)
    canvas.drawBitmap(art, centreCrop(art.width, art.height), FULL, filtered)
    blur(plate, (PLATE_HEIGHT * BLUR_FRACTION * recipe.softness).toInt().coerceIn(1, PLATE_HEIGHT / 2))
  }

  paint.shader = LinearGradient(
    0f, 0f, 0f, PLATE_HEIGHT.toFloat(),
    intArrayOf(recipe.app.withAlpha(0.10f), recipe.app.withAlpha(0.62f), recipe.app.withAlpha(0.94f)),
    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
  )
  canvas.drawRect(0f, 0f, PLATE_WIDTH.toFloat(), PLATE_HEIGHT.toFloat(), paint)
  paint.shader = LinearGradient(
    0f, 0f, PLATE_WIDTH.toFloat(), 0f,
    intArrayOf(recipe.app.withAlpha(0.80f), recipe.app.withAlpha(0.22f), recipe.app.withAlpha(0f)),
    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
  )
  canvas.drawRect(0f, 0f, PLATE_WIDTH.toFloat(), PLATE_HEIGHT.toFloat(), paint)
  plate.setHasAlpha(false)
  return plate
}

private fun Int.withAlpha(alpha: Float): Int =
  (this and 0x00FFFFFF) or ((alpha * 255f).toInt().coerceIn(0, 255) shl 24)

private fun centreCrop(width: Int, height: Int): Rect {
  val wanted = PLATE_WIDTH.toFloat() / PLATE_HEIGHT
  val have = width.toFloat() / height.coerceAtLeast(1)
  return if (have > wanted) {
    val keep = (height * wanted).toInt().coerceAtLeast(1)
    val x = (width - keep) / 2
    Rect(x, 0, x + keep, height)
  } else {
    val keep = (width / wanted).toInt().coerceAtLeast(1)
    val y = (height - keep) / 2
    Rect(0, y, width, y + keep)
  }
}

private fun blur(plate: Bitmap, radius: Int) {
  val pixels = IntArray(PLATE_WIDTH * PLATE_HEIGHT)
  plate.getPixels(pixels, 0, PLATE_WIDTH, 0, 0, PLATE_WIDTH, PLATE_HEIGHT)
  val scratch = IntArray(pixels.size)
  repeat(BLUR_PASSES) {
    across(pixels, scratch, radius)
    down(scratch, pixels, radius)
  }
  plate.setPixels(pixels, 0, PLATE_WIDTH, 0, 0, PLATE_WIDTH, PLATE_HEIGHT)
}

private const val OPAQUE = 0xff shl 24

private fun across(src: IntArray, dst: IntArray, radius: Int) {
  val span = radius * 2 + 1
  val last = PLATE_WIDTH - 1
  for (y in 0 until PLATE_HEIGHT) {
    val row = y * PLATE_WIDTH
    var r = 0
    var g = 0
    var b = 0
    for (i in -radius..radius) {
      val p = src[row + i.coerceIn(0, last)]
      r += (p shr 16) and 0xff
      g += (p shr 8) and 0xff
      b += p and 0xff
    }
    for (x in 0 until PLATE_WIDTH) {
      dst[row + x] = OPAQUE or ((r / span) shl 16) or ((g / span) shl 8) or (b / span)
      val gone = src[row + (x - radius).coerceIn(0, last)]
      val come = src[row + (x + radius + 1).coerceIn(0, last)]
      r += ((come shr 16) and 0xff) - ((gone shr 16) and 0xff)
      g += ((come shr 8) and 0xff) - ((gone shr 8) and 0xff)
      b += (come and 0xff) - (gone and 0xff)
    }
  }
}

private fun down(src: IntArray, dst: IntArray, radius: Int) {
  val span = radius * 2 + 1
  val last = PLATE_HEIGHT - 1
  for (x in 0 until PLATE_WIDTH) {
    var r = 0
    var g = 0
    var b = 0
    for (i in -radius..radius) {
      val p = src[i.coerceIn(0, last) * PLATE_WIDTH + x]
      r += (p shr 16) and 0xff
      g += (p shr 8) and 0xff
      b += p and 0xff
    }
    for (y in 0 until PLATE_HEIGHT) {
      dst[y * PLATE_WIDTH + x] = OPAQUE or ((r / span) shl 16) or ((g / span) shl 8) or (b / span)
      val gone = src[(y - radius).coerceIn(0, last) * PLATE_WIDTH + x]
      val come = src[(y + radius + 1).coerceIn(0, last) * PLATE_WIDTH + x]
      r += ((come shr 16) and 0xff) - ((gone shr 16) and 0xff)
      g += ((come shr 8) and 0xff) - ((gone shr 8) and 0xff)
      b += (come and 0xff) - (gone and 0xff)
    }
  }
}
