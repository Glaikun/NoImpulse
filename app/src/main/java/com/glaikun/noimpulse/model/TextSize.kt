package com.glaikun.noimpulse.model

/**
 * An accessibility text-size choice. [scale] multiplies every `sp`-based text size in the
 * app (applied at the theme level — see `NoImpulseTheme` and the styles in `Type.kt`).
 */
enum class TextSize(val scale: Float) {
    DEFAULT(1f),
    LARGE(1.15f),
    LARGEST(1.3f),
}
