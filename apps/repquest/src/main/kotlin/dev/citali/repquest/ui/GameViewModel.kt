// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.repquest.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.citali.gami.AdRewardEarned
import dev.citali.gami.AlreadyOwned
import dev.citali.gami.EconomyConfig
import dev.citali.gami.HabitCompleted
import dev.citali.gami.HabitUncompleted
import dev.citali.gami.InsufficientPoints
import dev.citali.gami.InsufficientShards
import dev.citali.gami.PullResult
import dev.citali.gami.RepairOnCooldown
import dev.citali.gami.RepsLogged
import dev.citali.gami.StreakState
import dev.citali.gami.WorkoutCompleted
import dev.citali.repquest.data.GameRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DemoHabit(
    val id: String,
    val title: String,
)

class GameViewModel : ViewModel() {
    private val repo = GameRepository()

    val state = repo.state
    val quests = repo.quests
    val adsWatched = repo.adsWatched
    val rates = repo.rates
    val catalog = repo.catalog
    val adCap = repo.adCap
    val xpCurve = EconomyConfig.V1.xpCurve
    val repairCost = EconomyConfig.V1.streak.repairCost
    val shardShop = EconomyConfig.V1.shardShop

    val habits =
        listOf(
            DemoHabit("water", "Drink 2L water"),
            DemoHabit("read", "Read 10 pages"),
            DemoHabit("stretch", "Stretch 5 min"),
        )

    val exercises =
        listOf(
            Triple("squat", "Squats", 2),
            Triple("pushup", "Push-ups", 1),
            Triple("jack", "Jumping jacks", 1),
        )

    private val _lastPull = MutableStateFlow<PullResult?>(null)
    val lastPull: StateFlow<PullResult?> = _lastPull

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    private val _checkedHabits = MutableStateFlow<Set<String>>(emptySet())
    val checkedHabits: StateFlow<Set<String>> = _checkedHabits
    private var habitDay: LocalDate? = null

    init {
        viewModelScope.launch { repo.observeToday() }
    }

    fun logReps(
        exerciseId: String,
        reps: Int,
    ) = viewModelScope.launch {
        val out = repo.process(RepsLogged(repo.now(), repo.uuid(), exerciseId, reps))
        val quest = out.questsCompleted.firstOrNull()?.let { " · quest done: $it" } ?: ""
        val capped = if (out.awards.any { it.capped }) " · daily cap hit" else ""
        say("+$reps ${exerciseLabel(exerciseId)}$quest$capped")
    }

    fun logWorkout() =
        viewModelScope.launch {
            repo.process(WorkoutCompleted(repo.now(), repo.uuid(), "mixed", 60, 900))
            say("Workout logged · +25 pts")
        }

    fun toggleHabit(id: String) =
        viewModelScope.launch {
            val today = repo.today()
            if (habitDay != today) {
                habitDay = today
                _checkedHabits.value = emptySet()
            }
            if (_checkedHabits.value.contains(id)) {
                repo.process(HabitUncompleted(repo.now(), repo.uuid(), id, today))
                _checkedHabits.value -= id
            } else {
                val out =
                    repo.process(HabitCompleted(repo.now(), repo.uuid(), id, today, habits.size))
                _checkedHabits.value += id
                if (out.awards.any { it.source == "day.perfect" }) say("Perfect day! +50 bonus")
            }
        }

    /** Simulated rewarded ad — real MAX SDK lands later. */
    fun watchAd() =
        viewModelScope.launch {
            repo.process(AdRewardEarned(repo.now(), repo.uuid(), "topup-demo"))
            say("+50 pts · thanks for supporting (demo ad)")
        }

    fun pull(count: Int) =
        viewModelScope.launch {
            try {
                _lastPull.value = repo.pull(count)
            } catch (_: InsufficientPoints) {
                say("Not enough points — go move!")
            }
        }

    fun clearPull() {
        _lastPull.value = null
    }

    fun redeem(itemId: String) =
        viewModelScope.launch {
            try {
                val item = repo.redeem(itemId)
                say("${item.name} redeemed!")
            } catch (_: AlreadyOwned) {
                say("Already owned")
            } catch (_: InsufficientShards) {
                say("Not enough shards — dupes salvage into shards")
            }
        }

    fun repairWithPoints() =
        viewModelScope.launch {
            if (repo.spend(repairCost, "streak.repair")) {
                repo.repairStreak()
                say("Streak repaired · streak continues today")
            } else {
                say("Need ${repairCost.amount} pts to repair")
            }
        }

    /** Simulated ad-paid repair — no points charged, cooldown still enforced. */
    fun repairWithAd() =
        viewModelScope.launch {
            try {
                if (repo.repairStreak() == StreakState.ACTIVE) say("Streak repaired (demo ad)")
            } catch (_: RepairOnCooldown) {
                say("Repair on cooldown — one per week")
            }
        }

    private fun exerciseLabel(id: String): String = exercises.firstOrNull { it.first == id }?.second ?: id

    private fun say(msg: String) {
        _notice.value = msg
        viewModelScope.launch {
            delay(3_000)
            if (_notice.value == msg) _notice.value = null
        }
    }
}
