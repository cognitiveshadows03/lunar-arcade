// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.gami

import dev.citali.gami.stores.ProfileRow
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure streak transitions. Rules:
 * - Activity on a new consecutive day increments; same-day activity is a no-op.
 * - A fully skipped day breaks the streak (detected on next observe/activity).
 * - Backward dates (time travel) never regress state — they're ignored.
 * - Repair preserves [ProfileRow.streakDays] and pretends yesterday was
 *   active, so today's activity continues the streak.
 */
internal object StreakLogic {
    fun observe(
        row: ProfileRow,
        day: LocalDate,
    ): ProfileRow {
        val last = row.lastActiveDay ?: return row
        return if (day.isAfter(last.plusDays(1))) row.copy(streakBroken = true) else row
    }

    fun activity(
        row: ProfileRow,
        day: LocalDate,
    ): ProfileRow {
        val last = row.lastActiveDay ?: return row.copy(
            streakDays = 1,
            streakBroken = false,
            lastActiveDay = day,
        )
        return when {
            day == last -> row
            day == last.plusDays(1) -> row.copy(
                streakDays = row.streakDays + 1,
                streakBroken = false,
                lastActiveDay = day,
            )
            day.isAfter(last.plusDays(1)) -> row.copy(
                streakDays = 1,
                streakBroken = false,
                lastActiveDay = day,
            )
            else -> row // day < last: device clock went backwards; ignore.
        }
    }

    fun repair(
        row: ProfileRow,
        today: LocalDate,
        cooldownDays: Int,
    ): ProfileRow {
        if (!row.streakBroken) return row
        val lastRepair = row.lastRepairDay
        if (lastRepair != null && ChronoUnit.DAYS.between(lastRepair, today) < cooldownDays) {
            throw RepairOnCooldown(until = lastRepair.plusDays(cooldownDays.toLong()))
        }
        return row.copy(
            streakBroken = false,
            lastActiveDay = today.minusDays(1),
            lastRepairDay = today,
        )
    }
}
