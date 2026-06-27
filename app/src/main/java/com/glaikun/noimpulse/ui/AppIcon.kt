package com.glaikun.noimpulse.ui

import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.glaikun.noimpulse.model.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private val ICON_SIZE = 52.dp

/**
 * Process-lifetime cache of render-ready icons keyed by package name. Each entry is decoded
 * once, at display size, with greyscale already baked in (see [toGreyscaleImageBitmap]), so a
 * recomposed grid item paints synchronously on its first frame with a plain bitmap blit — no
 * reload, no off-thread hop, no per-frame scaling or colour filtering. Only touched from the
 * main thread, but kept concurrent defensively.
 */
private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

/**
 * The app's launcher icon rendered in greyscale — a deliberate de-stimulation choice
 * (colorful icons are the impulse cue NoImpulse aims to dampen). Falls back to a letter
 * badge until the icon is available (or permanently, if [loadIcon] can't resolve one).
 */
@Composable
fun AppIcon(
    app: AppEntry,
    loadIcon: (String) -> Drawable?,
    modifier: Modifier = Modifier,
) {
    val sizePx = with(LocalDensity.current) { ICON_SIZE.roundToPx() }
    var bitmap by remember(app.packageName) { mutableStateOf(iconCache[app.packageName]) }
    LaunchedEffect(app.packageName) {
        if (bitmap == null) {
            val loaded = withContext(Dispatchers.IO) {
                loadIcon(app.packageName)?.toGreyscaleImageBitmap(sizePx)
            }
            if (loaded != null) {
                iconCache[app.packageName] = loaded
                bitmap = loaded
            }
        }
    }

    Box(
        modifier = modifier
            .size(ICON_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val icon = bitmap
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = app.label.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * Renders this drawable into a [sizePx]²  bitmap with saturation removed, baking the greyscale
 * in once so the render path stays a plain bitmap draw. Sized to the display so adaptive icons
 * (whose intrinsic size is much larger) aren't kept full-resolution and rescaled every frame.
 */
private fun Drawable.toGreyscaleImageBitmap(sizePx: Int): ImageBitmap {
    val target = createBitmap(sizePx, sizePx)
    val canvas = Canvas(target)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }
    val layer = canvas.saveLayer(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    canvas.restoreToCount(layer)
    return target.asImageBitmap()
}
