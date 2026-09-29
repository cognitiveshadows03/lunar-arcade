// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
//
// Economy tuner: replays scripted player archetypes day-by-day against
// EconomyConfig and prints earn/pull/pity stats. Run after EVERY economy
// change and commit the output next to it:
//
//   ./gradlew :simulator:run --args="90"
package dev.citali.sim

import dev.citali.gami.AdRewardEarned
import dev.citali.gami.Collectible
import dev.citali.gami.DayObserved
import dev.citali.gami.EconomyConfig
import dev.citali.gami.FocusSessionCompleted
import dev.citali.gami.GamificationEngine
import dev.citali.gami.GameEvent
import dev.citali.gami.HabitCompleted
import dev.citali.gami.PlayerState
import dev.citali.gami.Rarity
import dev.citali.gami.RepsLogged
import dev.citali.gami.WorkoutCompleted
import dev.citali.gami.stores.InMemoryInventoryStore
import dev.citali.gami.stores.InMemoryLedgerStore
import dev.citali.gami.stores.InMemoryProfileStore
import dev.citali.gami.stores.InMemoryQuestStore
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

private val ZONE = ZoneOffset.UTC
private val START = LocalDate.of(2026, 1, 5) // a Monday.

private data class DayScript(
    val habitsDone: Int,
    val habitsTotal: Int,
    val reps: List<Pair<String, Int>>,
    val logWorkout: Boolean,
    val focusBlocks: Int,
    val ads: Int,
)

private data class Report(
    val name: String,
    val days: Int,
    val pulls: Int,
    val rarityCounts: Map<Rarity, Int>,
    val pityTriggers: Int,
    val firstEpicDay: Int?,
    val firstLegendaryDay: Int?,
    val capHitDays: Int,
    val final: PlayerState,
)

private fun uuid(): String = UUID.randomUUID().toString()

private suspend fun playDay(
    engine: GamificationEngine,
    day: LocalDate,
    script: DayScript,
): Boolean {
    var capped = false
    suspend fun emit(event: GameEvent) {
        if (engine.process(event).awards.any { it.capped }) capped = true
    }
    val at = day.atStartOfDay(ZONE).toInstant()
    emit(DayObserved(at, uuid(), day))
    repeat(script.habitsDone) { i ->
        emit(HabitCompleted(at, uuid(), "habit-$i", day, script.habitsTotal))
    }
    for ((exercise, reps) in script.reps) {
        emit(RepsLogged(at, uuid(), exercise, reps))
    }
    if (script.logWorkout) {
        emit(WorkoutCompleted(at, uuid(), "mixed", script.reps.sumOf { it.second }, 900))
    }
    repeat(script.focusBlocks) {
        emit(FocusSessionCompleted(at, uuid(), 25, verified = true))
    }
    repeat(script.ads) {
        emit(AdRewardEarned(at, uuid(), "topup"))
    }
    return capped
}

private suspend fun drainPulls(
    engine: GamificationEngine,
    dayIndex: Int,
    rarityCounts: MutableMap<Rarity, Int>,
    onPull: (pulled: List<Collectible>, pity: Boolean) -> Unit,
): Int {
    var pulls = 0
    while (engine.state().points.amount >= 100) {
        val result = engine.pull("starter")
        pulls++
        for (item in result.items) {
            rarityCounts[item.item.rarity] = (rarityCounts[item.item.rarity] ?: 0) + 1
        }
        onPull(result.items.map { it.item }, result.pityTriggered)
    }
    return pulls
}

private fun simulate(
    name: String,
    days: Int,
    seed: Long,
    scriptFor: (day: LocalDate) -> DayScript?,
): Report =
    runBlocking {
        val engine =
            GamificationEngine(
                config = EconomyConfig.V1,
                ledger = InMemoryLedgerStore(),
                inventory = InMemoryInventoryStore(),
                profile = InMemoryProfileStore(),
                quests = InMemoryQuestStore(),
                zoneId = ZONE,
                rng = Random(seed),
            )
        val rarityCounts = mutableMapOf<Rarity, Int>()
        var pulls = 0
        var pityTriggers = 0
        var firstEpicDay: Int? = null
        var firstLegendaryDay: Int? = null
        var capHitDays = 0
        repeat(days) { i ->
            val day = START.plusDays(i.toLong())
            val script = scriptFor(day) ?: return@repeat
            if (playDay(engine, day, script)) capHitDays++
            pulls += drainPulls(engine, i + 1, rarityCounts) { items, pity ->
                if (pity) pityTriggers++
                if (firstEpicDay == null && items.any { it.rarity >= Rarity.EPIC }) firstEpicDay = i + 1
                if (firstLegendaryDay == null && items.any { it.rarity == Rarity.LEGENDARY }) firstLegendaryDay = i + 1
            }
        }
        Report(name, days, pulls, rarityCounts, pityTriggers, firstEpicDay, firstLegendaryDay, capHitDays, engine.state())
    }

private fun Report.print() {
    val total = rarityCounts.values.sum().coerceAtLeast(1)
    println("== $name ($days days) ==")
    println("  pulls=$pulls pityTriggers=$pityTriggers capHitDays=$capHitDays")
    println("  firstEpicDay=${firstEpicDay ?: "-"} firstLegendaryDay=${firstLegendaryDay ?: "-"}")
    for (rarity in Rarity.entries) {
        val n = rarityCounts[rarity] ?: 0
        println("  ${rarity.name.padEnd(9)} $n (${"%.1f".format(n * 100.0 / total)}%)")
    }
    println("  final: Lv ${final.level} · ${final.xp.amount} xp · ${final.shards.amount} shards · streak ${final.streakDays} · owned ${final.ownedIds.size}")
    println()
}

fun main(args: Array<String>) {
    val days = args.firstOrNull()?.toIntOrNull() ?: 90
    println("lunar-arcade economy sim · config v${EconomyConfig.V1.version} · $days days\n")

    simulate("casual (3d/wk, 1/2 habits, 20 reps)", days, 1) { day ->
        if (day.dayOfWeek == DayOfWeek.MONDAY || day.dayOfWeek == DayOfWeek.WEDNESDAY || day.dayOfWeek == DayOfWeek.FRIDAY) {
            DayScript(habitsDone = 1, habitsTotal = 2, reps = listOf("pushup" to 20), logWorkout = false, focusBlocks = 0, ads = 0)
        } else {
            null
        }
    }.print()

    simulate("daily (3/3 habits, 60 reps, workout, 3 ads)", days, 2) { _ ->
        DayScript(habitsDone = 3, habitsTotal = 3, reps = listOf("pushup" to 40, "squat" to 20), logWorkout = true, focusBlocks = 0, ads = 3)
    }.print()

    simulate("grinder (5/5 habits, caps, workout, 5 ads)", days, 3) { _ ->
        DayScript(habitsDone = 5, habitsTotal = 5, reps = listOf("squat" to 200, "pushup" to 100), logWorkout = true, focusBlocks = 2, ads = 5)
    }.print()
}
