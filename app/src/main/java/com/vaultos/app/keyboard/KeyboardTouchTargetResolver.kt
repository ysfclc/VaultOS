package com.vaultos.app.keyboard

import kotlin.math.abs

/**
 * Resolves a letter touch to the nearest key centre. The IME uses this only
 * for letter rows, so punctuation and modifier keys keep their explicit hit areas.
 */
internal object KeyboardTouchTargetResolver {
    fun nearestIndex(pointerX: Float, keyCenters: FloatArray, maxDistance: Float): Int? {
        if (keyCenters.isEmpty() || maxDistance < 0f) return null
        var bestIndex = -1
        var bestDistance = Float.MAX_VALUE
        keyCenters.forEachIndexed { index, center ->
            val distance = abs(pointerX - center)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex.takeIf { bestIndex >= 0 && bestDistance <= maxDistance }
    }
}
