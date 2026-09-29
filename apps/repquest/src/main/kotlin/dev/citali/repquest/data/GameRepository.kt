// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.repquest.data

import dev.citali.gami.Collectible
import dev.citali.gami.DayObserved
import dev.citali.gami.EconomyConfig
import dev.citali.gami.EngineOutcome
import dev.citali.gami.GamificationEngine
import dev.citali.gami.GameEvent
import dev.citali.gami.PlayerState
import dev.citali.gami.Points
import dev.citali.gami.PullResult
import dev.citali.gami.QuestProgressView
import dev.citali.gami.RatesView
import dev.citali.gami.StreakState
import dev.citali.gami.stores.InMemoryInventoryStore
import dev.citali.gami.stores.InMemoryLedgerStore
import dev.citali.gami.stores.InMemoryProfileStore
import dev.citali.gami.stores.InMemoryQuestStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Owns the engine + stores and serializes every call (the engine is not
 * internally synchronized). In-memory stores for the demo; Room lands next.
 */
class GameRepository {
    private val engine =
        GamificationEngine(
            config = EconomyConfig.V1,
            ledger = InMemoryLedgerStore(),
            inventory = InMemoryInventoryStore(),
            profile = InMemoryProfileStore(),
            quests = InMemoryQuestStore(),
        )
    private val mutex = Mutex()

    private val _state = MutableStateFlow<PlayerState?>(null)
    val state: StateFlow<PlayerState?> = _state

    private val _quests = MutableStateFlow<List<QuestProgressView>>(emptyList())
    val quests: StateFlow<List<QuestProgressView>> = _quests

    private val _adsWatched = MutableStateFlow(0L)
    val adsWatched: StateFlow<Long> = _adsWatched

    val rates: RatesView = engine.rates("starter")
    val catalog: List<Collectible> = EconomyConfig.V1.items.values.sortedBy { it.rarity }
    val adCap: Int = EconomyConfig.V1.adTopUp.maxPerDay

    fun uuid(): String = UUID.randomUUID().toString()

    fun now(): Instant = Instant.now()

    fun today(): LocalDate = LocalDate.now()

    suspend fun refresh() {
        _state.value = engine.state()
        _quests.value = engine.questProgress()
        _adsWatched.value = engine.adsWatched()
    }

    suspend fun observeToday() {
        mutex.withLock {
            engine.process(DayObserved(now(), uuid(), today()))
            refresh()
        }
    }

    suspend fun process(event: GameEvent): EngineOutcome =
        mutex.withLock {
            engine.process(event).also { refresh() }
        }

    suspend fun pull(count: Int): PullResult =
        mutex.withLock {
            engine.pull("starter", count, uuid()).also { refresh() }
        }

    suspend fun redeem(itemId: String): Collectible =
        mutex.withLock {
            engine.redeem(itemId, uuid()).also { refresh() }
        }

    suspend fun spend(
        points: Points,
        reason: String,
    ): Boolean =
        mutex.withLock {
            engine.spend(points, reason, uuid()).also { refresh() }
        }

    suspend fun repairStreak(): StreakState =
        mutex.withLock {
            engine.repairStreak(today()).also { refresh() }
        }
}
