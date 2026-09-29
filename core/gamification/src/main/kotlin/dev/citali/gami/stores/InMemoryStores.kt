// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.gami.stores

import java.time.LocalDate

/** In-memory [LedgerStore]. Tests, simulator, and demos — not production. */
class InMemoryLedgerStore : LedgerStore {
    private val entries = mutableListOf<LedgerEntry>()
    private val refs = mutableSetOf<String>()

    override suspend fun append(entry: LedgerEntry): Boolean {
        if (!refs.add(entry.refId)) return false
        entries.add(entry)
        return true
    }

    override suspend fun sumPoints(
        prefix: String,
        day: LocalDate,
    ): Long =
        entries
            .filter { it.day == day && it.countsTowardCaps && it.source.startsWith(prefix) }
            .sumOf { it.points }

    override suspend fun positiveTags(
        prefix: String,
        day: LocalDate,
    ): Set<String> =
        entries
            .filter { it.day == day && it.countsTowardCaps && it.tag != null && it.source.startsWith(prefix) }
            .groupBy { it.tag }
            .filterValues { rows -> rows.sumOf { it.points } > 0 }
            .keys
            .filterNotNull()
            .toSet()

    override suspend fun count(
        prefix: String,
        day: LocalDate,
    ): Long =
        entries.count { it.day == day && it.countsTowardCaps && it.source.startsWith(prefix) }.toLong()
}

/** In-memory [InventoryStore]. Tests, simulator, and demos — not production. */
class InMemoryInventoryStore : InventoryStore {
    private val owned = mutableSetOf<String>()
    private val pity = mutableMapOf<String, Int>()

    override suspend fun ownedIds(): Set<String> = owned.toSet()

    override suspend fun add(id: String) {
        owned.add(id)
    }

    override suspend fun pityCounter(bannerId: String): Int = pity[bannerId] ?: 0

    override suspend fun setPityCounter(
        bannerId: String,
        n: Int,
    ) {
        pity[bannerId] = n
    }
}

/** In-memory [ProfileStore]. Tests, simulator, and demos — not production. */
class InMemoryProfileStore(
    initial: ProfileRow = ProfileRow(),
) : ProfileStore {
    private var row: ProfileRow = initial

    override suspend fun get(): ProfileRow = row

    override suspend fun put(row: ProfileRow) {
        this.row = row
    }
}

/** In-memory [QuestStore]. Tests, simulator, and demos — not production. */
class InMemoryQuestStore : QuestStore {
    private val progress = mutableMapOf<String, Long>()
    private val claimed = mutableSetOf<String>()

    private fun key(
        questId: String,
        windowKey: String,
    ): String = "$questId|$windowKey"

    override suspend fun progress(
        questId: String,
        windowKey: String,
    ): Long = progress[key(questId, windowKey)] ?: 0

    override suspend fun setProgress(
        questId: String,
        windowKey: String,
        value: Long,
    ) {
        progress[key(questId, windowKey)] = value
    }

    override suspend fun claimed(
        questId: String,
        windowKey: String,
    ): Boolean = claimed.contains(key(questId, windowKey))

    override suspend fun setClaimed(
        questId: String,
        windowKey: String,
    ) {
        claimed.add(key(questId, windowKey))
    }
}
