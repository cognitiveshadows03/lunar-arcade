// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.gami

import dev.citali.gami.stores.QuestStore
import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * Count-based daily/weekly quests. Progress accrues from [GameEvent.units];
 * each quest pays once per window. Claiming is split from granting (see
 * [GamificationEngine]) so a crash between the two under-awards rather than
 * double-awards, and a retry converges via the idempotent grant refId.
 */
internal class QuestEngine(
    private val config: EconomyConfig,
    private val store: QuestStore,
) {
    /** Advance matching quests; returns defs that newly reached their target. */
    suspend fun progress(
        event: GameEvent,
        day: LocalDate,
    ): List<QuestDef> {
        val type = event::class.simpleName
        val key = event.sourceKey()
        val out = mutableListOf<QuestDef>()
        for (quest in config.quests) {
            if (quest.matcher.eventType != type) continue
            val prefix = quest.matcher.keyPrefix
            if (prefix != null && !key.startsWith(prefix)) continue
            val windowKey = windowKey(quest.window, day)
            if (store.claimed(quest.id, windowKey)) continue
            val now = store.progress(quest.id, windowKey) + event.units()
            store.setProgress(quest.id, windowKey, now)
            if (now >= quest.targetUnits) out.add(quest)
        }
        return out
    }

    companion object {
        fun windowKey(
            window: QuestWindow,
            day: LocalDate,
        ): String =
            when (window) {
                QuestWindow.DAILY -> day.toString()
                QuestWindow.WEEKLY -> "${day.get(IsoFields.WEEK_BASED_YEAR)}-W${day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
            }
    }
}
