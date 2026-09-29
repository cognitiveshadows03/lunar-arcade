// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.gami

/** Spendable currency. Earned from activity and ads, spent on pulls. */
@JvmInline
value class Points(val amount: Long) {
    init {
        require(amount >= 0) { "Points must be non-negative, was $amount" }
    }

    operator fun plus(other: Points): Points = Points(amount + other.amount)

    operator fun compareTo(other: Points): Int = amount.compareTo(other.amount)
}

/** Dupe-salvage currency. Shard shop only. */
@JvmInline
value class Shards(val amount: Long) {
    init {
        require(amount >= 0) { "Shards must be non-negative, was $amount" }
    }

    operator fun plus(other: Shards): Shards = Shards(amount + other.amount)

    operator fun compareTo(other: Shards): Int = amount.compareTo(other.amount)
}

/** Lifetime progress. Earned, never spent. */
@JvmInline
value class Xp(val amount: Long) {
    init {
        require(amount >= 0) { "XP must be non-negative, was $amount" }
    }

    operator fun plus(other: Xp): Xp = Xp(amount + other.amount)

    operator fun compareTo(other: Xp): Int = amount.compareTo(other.amount)
}

enum class Rarity {
    COMMON,
    RARE,
    EPIC,
    LEGENDARY,
}

/**
 * A collectible item. The engine owns identity + rarity; the host owns art —
 * [artRef] is an opaque key the host maps to a drawable.
 */
data class Collectible(
    val id: String,
    val collectionId: String,
    val rarity: Rarity,
    val name: String,
    val flavor: String,
    val artRef: String,
)

enum class StreakState {
    ACTIVE,
    BROKEN,
}

data class PlayerState(
    val points: Points,
    val shards: Shards,
    val xp: Xp,
    val level: Int,
    val streakDays: Int,
    val streakState: StreakState,
    val ownedIds: Set<String>,
)
