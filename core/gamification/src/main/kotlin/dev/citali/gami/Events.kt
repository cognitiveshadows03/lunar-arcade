// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.gami

import java.time.Instant
import java.time.LocalDate

/**
 * Domain events, host → engine. Every event carries the moment it happened
 * ([at]) and a host-generated idempotency key ([refId]: one UUID per
 * real-world action). Reprocessing the same [refId] is a no-op returning
 * [EngineOutcome.duplicate], so hosts can retry across process death.
 */
sealed interface GameEvent {
    val at: Instant
    val refId: String
}

data class RepsLogged(
    override val at: Instant,
    override val refId: String,
    val exerciseId: String,
    val reps: Int,
) : GameEvent {
    init {
        require(reps > 0) { "reps must be positive" }
    }
}

data class WorkoutCompleted(
    override val at: Instant,
    override val refId: String,
    val exerciseId: String,
    val totalReps: Int,
    val durationSec: Long,
) : GameEvent

data class HabitCompleted(
    override val at: Instant,
    override val refId: String,
    val habitId: String,
    val day: LocalDate,
    val dayTotalHabits: Int,
) : GameEvent {
    init {
        require(dayTotalHabits > 0) { "dayTotalHabits must be positive" }
    }
}

data class HabitUncompleted(
    override val at: Instant,
    override val refId: String,
    val habitId: String,
    val day: LocalDate,
) : GameEvent

data class FocusSessionCompleted(
    override val at: Instant,
    override val refId: String,
    val minutes: Int,
    val verified: Boolean,
) : GameEvent {
    init {
        require(minutes > 0) { "minutes must be positive" }
    }
}

/**
 * Emitted by the host ONLY after the ad SDK confirms the reward callback.
 * Never emit for skipped/failed ads.
 */
data class AdRewardEarned(
    override val at: Instant,
    override val refId: String,
    val placement: String,
) : GameEvent

/** Emitted by the host on app open (and day rollover while open). */
data class DayObserved(
    override val at: Instant,
    override val refId: String,
    val day: LocalDate,
) : GameEvent

/** Stable faucet/cap key. Events without a faucet still flow to quests. */
fun GameEvent.sourceKey(): String =
    when (this) {
        is RepsLogged -> "rep.$exerciseId"
        is WorkoutCompleted -> "workout.complete"
        is HabitCompleted -> "habit.complete"
        is HabitUncompleted -> "habit.uncomplete"
        is FocusSessionCompleted -> if (verified) "focus.session.verified" else "focus.session.plain"
        is AdRewardEarned -> "ad.topup"
        is DayObserved -> "day.observed"
    }

/**
 * Earning units: raw reps, completed 25-minute focus blocks (no partial
 * credit — standard Pomodoro), or 1.
 */
fun GameEvent.units(): Long =
    when (this) {
        is RepsLogged -> reps.toLong()
        is FocusSessionCompleted -> (minutes / 25).toLong()
        else -> 1L
    }

/** Ledger tag for per-entity grouping (habit ids drive perfect-day detection). */
fun GameEvent.tag(): String? =
    when (this) {
        is HabitCompleted -> habitId
        is HabitUncompleted -> habitId
        else -> null
    }
