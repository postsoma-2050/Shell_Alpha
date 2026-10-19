package com.cylonid.nativealpha.util

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils

object ColorPalette {
    val HERMIT_COLORS: Map<String, String> = linkedMapOf(
        "Amber" to "#FFC107",
        "Aquamarine" to "#00BCD4",
        "Blue" to "#2196F3",
        "Brick" to "#A43E32",
        "Brown" to "#795548",
        "Crimson" to "#C62828",
        "Cyan" to "#00ACC1",
        "Green" to "#4CAF50",
        "Indigo" to "#3F51B5",
        "Lavender" to "#9575CD",
        "Lime" to "#9CCC65",
        "Olive" to "#827717",
        "Orange" to "#FF9800",
        "Pink" to "#E91E63",
        "Purple" to "#9C27B0",
        "Red" to "#EB5757",
        "Tangerine" to "#F57C00",
        "Teal" to "#009688",
        "Violet" to "#673AB7",
        "Yellow" to "#FBC02D",
        "Signature" to "#EB5757"
    )

    private val CONTAINER_COLORS = listOf(
        "Blue", "Green", "Orange", "Purple",
        "Teal", "Pink", "Amber", "Indigo"
    )

    @JvmStatic
    fun containerColorNames(): List<String> = CONTAINER_COLORS

    @JvmStatic
    fun isValidContainer(containerId: Int): Boolean =
        containerId in CONTAINER_COLORS.indices

    @JvmStatic
    @ColorInt
    fun colorForTheme(themeColor: String?): Int {
        val normalized = themeColor?.trim().orEmpty()
        if (normalized.startsWith("#")) {
            return runCatching { Color.parseColor(normalized) }
                .getOrDefault(Color.parseColor(HERMIT_COLORS.getValue("Red")))
        }
        return HERMIT_COLORS.entries.firstOrNull { it.key.equals(normalized, ignoreCase = true) }
            ?.value
            ?.let(Color::parseColor)
            ?: Color.parseColor(HERMIT_COLORS.getValue("Red"))
    }

    @JvmStatic
    @ColorInt
    fun colorForContainer(containerId: Int): Int {
        if (containerId == Const.NO_CONTAINER) {
            return Color.rgb(117, 117, 117)
        }
        if (!isValidContainer(containerId)) {
            return Color.rgb(117, 117, 117)
        }
        val name = CONTAINER_COLORS[containerId]
        return Color.parseColor(HERMIT_COLORS.getValue(name))
    }

    @JvmStatic
    @ColorInt
    fun webAppColor(themeColor: String?, containerId: Int): Int =
        if (isValidContainer(containerId)) colorForContainer(containerId) else colorForTheme(themeColor)

    @JvmStatic
    @ColorInt
    fun contrastingTextColor(@ColorInt background: Int): Int =
        if (ColorUtils.calculateLuminance(background) > 0.45) Color.BLACK else Color.WHITE
}
