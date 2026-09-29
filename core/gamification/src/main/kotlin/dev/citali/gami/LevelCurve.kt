// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.gami

import kotlin.math.pow

/** XP curve. Default: level 2 at 282 XP, 10 at ~3.1k, 50 at ~35k. */
data class XpCurve(
    val base: Long = 100,
    val exponent: Double = 1.5,
) {
    /** Cumulative XP required to REACH [level]. Level 1 starts at 0. */
    fun xpForLevel(level: Int): Long {
        require(level >= 1) { "level must be >= 1" }
        if (level == 1) return 0
        return (base * level.toDouble().pow(exponent)).toLong()
    }

    fun levelFor(totalXp: Long): Int {
        var level = 1
        while (xpForLevel(level + 1) <= totalXp) level++
        return level
    }
}
