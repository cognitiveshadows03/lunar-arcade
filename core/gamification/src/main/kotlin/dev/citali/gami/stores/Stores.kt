// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.gami.stores

import java.time.Instant
import java.time.LocalDate

/**
 * One immutable ledger row. Negative [points]/[shards]/[xp] encode spends
 * and reversals — cap math nets them, balances floor at zero.
 */
data class LedgerEntry(
    val refId: String,
    val day: LocalDate,
    val source: String,
    val tag: String?,
    val points: Long,
    val shards: Long,
    val xp: Long,
    val at: Instant,
    /**
     * False for pure spends (pulls, redeems, repair payments) so spending
     * can never free up earn-room under the daily caps.
     */
    val countsTowardCaps: Boolean = true,
)

interface LedgerStore {
    /**
     * Append, enforcing [LedgerEntry.refId] uniqueness.
     * @return false when [refId] was already recorded (idempotent retry).
     */
    suspend fun append(entry: LedgerEntry): Boolean

    /**
     * Net points for entries on [day] whose [LedgerEntry.source] starts with
     * [prefix], counting only [LedgerEntry.countsTowardCaps] rows.
     * Empty [prefix] matches everything (the global cap).
     */
    suspend fun sumPoints(
        prefix: String,
        day: LocalDate,
    ): Long

    /**
     * Non-null tags with net-positive points for [prefix] on [day].
     * Room: `SELECT tag … GROUP BY tag HAVING SUM(points) > 0`.
     */
    suspend fun positiveTags(
        prefix: String,
        day: LocalDate,
    ): Set<String>

    /** Count of cap-counted rows for [prefix] on [day] (ad-watch progress UI). */
    suspend fun count(
        prefix: String,
        day: LocalDate,
    ): Long
}

interface InventoryStore {
    suspend fun ownedIds(): Set<String>

    suspend fun add(id: String)

    suspend fun pityCounter(bannerId: String): Int

    suspend fun setPityCounter(
        bannerId: String,
        n: Int,
    )
}

data class ProfileRow(
    val points: Long = 0,
    val shards: Long = 0,
    val xp: Long = 0,
    val streakDays: Int = 0,
    val streakBroken: Boolean = false,
    val lastActiveDay: LocalDate? = null,
    val lastRepairDay: LocalDate? = null,
)

interface ProfileStore {
    suspend fun get(): ProfileRow

    suspend fun put(row: ProfileRow)
}

interface QuestStore {
    suspend fun progress(
        questId: String,
        windowKey: String,
    ): Long

    suspend fun setProgress(
        questId: String,
        windowKey: String,
        value: Long,
    )

    suspend fun claimed(
        questId: String,
        windowKey: String,
    ): Boolean

    suspend fun setClaimed(
        questId: String,
        windowKey: String,
    )
}
