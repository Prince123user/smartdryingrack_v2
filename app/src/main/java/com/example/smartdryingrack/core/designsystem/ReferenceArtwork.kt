package com.example.smartdryingrack.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import com.example.smartdryingrack.R

/** Render only the artwork region of the supplied mockup; labels and controls remain native UI. */
@Composable
fun ReferenceArtwork(@DrawableRes resource: Int, top: Int, bottom: Int,
                     modifier: Modifier = Modifier, description: String? = null) {
    val bitmap = ImageBitmap.imageResource(resource)
    Canvas(modifier.semantics { if (description != null) contentDescription = description }) {
        drawImage(bitmap, srcOffset = IntOffset(0, top), srcSize = IntSize(bitmap.width, bottom - top),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
    }
}

@Composable
fun ReferenceRack(modifier: Modifier = Modifier, rain: Boolean = false) {
    ReferenceArtwork(if (rain) R.drawable.reference_rain else R.drawable.reference_dashboard,
        if (rain) 440 else 335, if (rain) 1550 else 945, modifier,
        "Illustration of a motorized drying rack with hanging laundry")
}
