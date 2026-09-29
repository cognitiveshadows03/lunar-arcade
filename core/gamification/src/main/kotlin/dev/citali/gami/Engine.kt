// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.gami

import dev.citali.gami.stores.InventoryStore
import dev.citali.gami.stores.LedgerEntry
import dev.citali.gami.stores.LedgerStore
import dev.citali.gami.stores.ProfileRow
import dev.citali.gami.stores.ProfileStore
import dev.citali.gami.stores.QuestStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

class InsufficientPoints(
    val required: Points,
    val balance: Points,
) : IllegalStateException("Need ${required.amount} points, have ${balance.amount}")

class InsufficientShards(
    val required: Shards,
    val balance: Shards,
) : IllegalStateException("Need ${required.amount} shards, have ${balance.amount}")

class AlreadyOwned(
    itemId: String,
) : IllegalStateException("Already own $itemId")

class RepairOnCooldown(
    val until: LocalDate,
) : IllegalStateException("Streak repair on cooldown until $until")

class DuplicatePull(
    refId: String,
) : IllegalStateException("Pull $refId was already recorded")

data class Award(
    val source: String,
    val points: Long,
    val xp: Long,
    val capped: Boolean = false,
)

data class EngineOutcome(
    val awards: List<Award>,
    val levelUpTo: Int?,
    val questsCompleted: List<String>,
    val streakDays: Int,
    val streakState: StreakState,
    val duplicate: Boolean = false,
)

data class PulledItem(
    val item: Collectible,
    val isNew: Boolean,
    /** Shards salvaged (0 when new). */
    val shardsGained: Long,
)

data class PullResult(
    val items: List<PulledItem>,
    /** True when pity forced at least one roll this pull. */
    val pityTriggered: Boolean,
)

data class QuestProgressView(
    val quest: QuestDef,
    val units: Long,
    val claimed: Boolean,
)

data class RatesView(
    val bannerId: String,
    val cost: Points,
    /** Rarity → percent, derived from the live config weights. */
    val percents: Map<Rarity, Double>,
    val pityEvery: Int,
    val poolSizes: Map<Rarity, Int>,
)

/**
 * The gamification engine. Event-sourced: hosts emit [GameEvent]s, the
 * engine turns them into points/XP/quests/pulls per [EconomyConfig].
 *
 * Threading: NOT internally synchronized. Hosts must serialize calls
 * (e.g. a Mutex in the repository) — balance checks and ledger appends
 * assume no concurrent mutation.
 *
 * Failure direction: every multi-step flow is ordered so a crash
 * under-awards (safe) rather than double-awards. Retries converge via
 * idempotent refIds — except [pull], whose crash window is ~1ms of pure
 * CPU after the spend (documented; accepted for v1).
 */
class GamificationEngine(
    private val config: EconomyConfig,
    private val ledger: LedgerStore,
    private val inventory: InventoryStore,
    private val profile: ProfileStore,
    private val quests: QuestStore,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val rng: Random = Random,
) {
    private val questEngine = QuestEngine(config, quests)

    // ---------- events ----------

    suspend fun process(event: GameEvent): EngineOutcome {
        val day = eventDay(event)
        val key = event.sourceKey()

        // 1. Faucet grant with daily caps (reversals carry negative grants).
        val (grantPoints, grantXp) =
            if (event is HabitUncompleted) {
                val base = config.faucets["habit.complete"]
                -(base?.pointsPerUnit ?: 0) to -(base?.xpPerUnit ?: 0)
            } else {
                val faucet = config.faucets[key]
                val requested = if (faucet == null) 0 else faucet.pointsPerUnit * event.units()
                val granted = applyCaps(key, day, requested)
                granted to (if (faucet == null) 0 else faucet.xpPerUnit * event.units())
            }

        // 2. Idempotency gate: dupes return before ANY side effect.
        val recorded =
            ledger.append(
                LedgerEntry(
                    refId = event.refId,
                    day = day,
                    source = key,
                    tag = event.tag(),
                    points = grantPoints,
                    shards = 0,
                    xp = grantXp,
                    at = event.at,
                ),
            )
        if (!recorded) {
            val row = profile.get()
            return EngineOutcome(
                awards = emptyList(),
                levelUpTo = null,
                questsCompleted = emptyList(),
                streakDays = row.streakDays,
                streakState = if (row.streakBroken) StreakState.BROKEN else StreakState.ACTIVE,
                duplicate = true,
            )
        }

        // 3. Balance + level.
        var row = profile.get()
        val oldLevel = config.xpCurve.levelFor(row.xp)
        row = row.copy(
            points = (row.points + grantPoints).coerceAtLeast(0),
            xp = (row.xp + grantXp).coerceAtLeast(0),
        )
        val newLevel = config.xpCurve.levelFor(row.xp)

        // 4. Streak transitions.
        val oldStreak = row.streakDays
        row =
            when {
                event is DayObserved -> StreakLogic.observe(row, day)
                isQualifying(key) -> StreakLogic.activity(row, day)
                else -> row
            }

        profile.put(row)

        val awards = mutableListOf<Award>()
        if (grantPoints != 0L || grantXp != 0L) {
            val requested = if (event is HabitUncompleted) grantPoints else (config.faucets[key]?.pointsPerUnit ?: 0) * event.units()
            awards.add(Award(source = key, points = grantPoints, xp = grantXp, capped = grantPoints < requested))
        }

        // 5. Derived grants (each idempotent; crash-safe by construction).
        if (event is HabitCompleted) {
            val doneToday = ledger.positiveTags("habit.", day).size
            if (doneToday >= event.dayTotalHabits) {
                grantDerived(
                    source = "day.perfect",
                    refId = "perfect:$day",
                    day = day,
                    at = event.at,
                    points = PERFECT_DAY_POINTS,
                    xp = PERFECT_DAY_XP,
                )?.let { awards.add(it) }
            }
        }
        if (row.streakDays > oldStreak) {
            config.streak.milestones[row.streakDays]?.let { milestone ->
                grantDerived(
                    source = "streak.milestone",
                    refId = "ms:${row.streakDays}:$day",
                    day = day,
                    at = event.at,
                    points = milestone.points,
                    xp = milestone.xp,
                )?.let { awards.add(it) }
            }
        }
        val completedQuests = mutableListOf<String>()
        for (quest in questEngine.progress(event, day)) {
            val windowKey = QuestEngine.windowKey(quest.window, day)
            val credited =
                ledger.append(
                    LedgerEntry(
                        refId = "quest:${quest.id}:$windowKey",
                        day = day,
                        source = "quest.${quest.id}",
                        tag = null,
                        points = quest.rewardPoints,
                        shards = 0,
                        xp = quest.rewardXp,
                        at = event.at,
                    ),
                )
            // Append-false means a crashed retry already paid: converge by claiming.
            quests.setClaimed(quest.id, windowKey)
            if (credited) {
                val current = profile.get()
                profile.put(
                    current.copy(
                        points = current.points + quest.rewardPoints,
                        xp = current.xp + quest.rewardXp,
                    ),
                )
                awards.add(Award(source = "quest.${quest.id}", points = quest.rewardPoints, xp = quest.rewardXp))
                completedQuests.add(quest.id)
            }
        }

        val finalRow = profile.get()
        return EngineOutcome(
            awards = awards,
            levelUpTo = if (newLevel > oldLevel) newLevel else null,
            questsCompleted = completedQuests,
            streakDays = finalRow.streakDays,
            streakState = if (finalRow.streakBroken) StreakState.BROKEN else StreakState.ACTIVE,
        )
    }

    // ---------- pulls ----------

    suspend fun pull(
        bannerId: String,
        count: Int = 1,
        refId: String = "pull-" + UUID.randomUUID(),
    ): PullResult {
        require(count in 1..10) { "count must be 1..10" }
        val banner = config.banners[bannerId] ?: throw IllegalArgumentException("Unknown banner $bannerId")
        val cost = banner.cost.amount * count

        // Balance check first (hosts serialize calls, so no TOCTOU).
        val row = profile.get()
        if (row.points < cost) throw InsufficientPoints(Points(cost), Points(row.points))

        val day = LocalDate.now(zoneId)
        val spent =
            ledger.append(
                LedgerEntry(
                    refId = refId,
                    day = day,
                    source = "pull.$bannerId",
                    tag = null,
                    points = -cost,
                    shards = 0,
                    xp = 0,
                    at = Instant.now(),
                    countsTowardCaps = false,
                ),
            )
        if (!spent) throw DuplicatePull(refId)

        var pityTriggered = false
        var shardsGained = 0L
        val items =
            (1..count).map {
                val pulled = rollOnce(banner)
                if (pulled.forcedPity) pityTriggered = true
                if (pulled.item.isNew) {
                    inventory.add(pulled.item.item.id)
                } else {
                    shardsGained += pulled.item.shardsGained
                }
                pulled.item
            }

        profile.put(row.copy(points = row.points - cost, shards = row.shards + shardsGained))
        return PullResult(items = items, pityTriggered = pityTriggered)
    }

    private data class Roll(
        val item: PulledItem,
        val forcedPity: Boolean,
    )

    private suspend fun rollOnce(banner: BannerConfig): Roll {
        val pity = inventory.pityCounter(banner.id)
        val forced = pity + 1 >= banner.pityEvery
        val rarity = if (forced) rollEpicPlus(banner) else rollWeighted(banner)
        inventory.setPityCounter(banner.id, if (rarity >= Rarity.EPIC) 0 else pity + 1)

        val pool = banner.pools.getValue(rarity)
        val itemId = pool[rng.nextInt(pool.size)]
        val item = config.items.getValue(itemId)
        val owned = inventory.ownedIds().contains(itemId)
        val pulled =
            if (owned) {
                PulledItem(item = item, isNew = false, shardsGained = banner.dupeShards.getValue(rarity))
            } else {
                PulledItem(item = item, isNew = true, shardsGained = 0)
            }
        return Roll(pulled, forced)
    }

    private fun rollWeighted(banner: BannerConfig): Rarity {
        var roll = rng.nextInt(10_000)
        // Rarity declaration order is ascending (see Domain.kt).
        for (rarity in Rarity.entries) {
            roll -= banner.weights.getValue(rarity)
            if (roll < 0) return rarity
        }
        return Rarity.COMMON // unreachable: weights sum to 10000 (validated).
    }

    private fun rollEpicPlus(banner: BannerConfig): Rarity {
        val epic = banner.weights.getValue(Rarity.EPIC)
        val legendary = banner.weights.getValue(Rarity.LEGENDARY)
        if (legendary <= 0) return Rarity.EPIC
        return if (rng.nextInt(epic + legendary) < legendary) Rarity.LEGENDARY else Rarity.EPIC
    }

    // ---------- shard shop ----------

    suspend fun redeem(
        itemId: String,
        refId: String = "redeem-" + UUID.randomUUID(),
    ): Collectible {
        val price = config.shardShop[itemId] ?: throw IllegalArgumentException("Not in shard shop: $itemId")
        if (inventory.ownedIds().contains(itemId)) throw AlreadyOwned(itemId)
        val row = profile.get()
        if (row.shards < price.amount) throw InsufficientShards(price, Shards(row.shards))

        val recorded =
            ledger.append(
                LedgerEntry(
                    refId = refId,
                    day = LocalDate.now(zoneId),
                    source = "redeem",
                    tag = itemId,
                    points = 0,
                    shards = -price.amount,
                    xp = 0,
                    at = Instant.now(),
                    countsTowardCaps = false,
                ),
            )
        if (!recorded) throw DuplicatePull(refId)
        inventory.add(itemId)
        profile.put(row.copy(shards = row.shards - price.amount))
        return config.items.getValue(itemId)
    }

    // ---------- points spending (streak repair payment, future sinks) ----------

    /**
     * Spend [points] for [reason]. Returns false (no entry) when broke.
     * Spends never count toward caps. Host repair flow:
     * `if (spend(cost, "streak.repair", uuid())) repairStreak(today)`.
     */
    suspend fun spend(
        points: Points,
        reason: String,
        refId: String,
        day: LocalDate = LocalDate.now(zoneId),
    ): Boolean {
        val row = profile.get()
        if (row.points < points.amount) return false
        val recorded =
            ledger.append(
                LedgerEntry(
                    refId = refId,
                    day = day,
                    source = reason,
                    tag = null,
                    points = -points.amount,
                    shards = 0,
                    xp = 0,
                    at = Instant.now(),
                    countsTowardCaps = false,
                ),
            )
        if (!recorded) return false
        profile.put(row.copy(points = row.points - points.amount))
        return true
    }

    // ---------- streak repair ----------

    /**
     * Repair a broken streak (no-op when ACTIVE). Payment is the host's
     * concern — call [spend] first for points, or nothing for ad-paid repair.
     * Enforces one repair per [StreakConfig.repairCooldownDays] (rolling).
     */
    suspend fun repairStreak(today: LocalDate): StreakState {
        val repaired = StreakLogic.repair(profile.get(), today, config.streak.repairCooldownDays)
        profile.put(repaired)
        return if (repaired.streakBroken) StreakState.BROKEN else StreakState.ACTIVE
    }

    // ---------- reads ----------

    suspend fun state(): PlayerState {
        val row = profile.get()
        return PlayerState(
            points = Points(row.points),
            shards = Shards(row.shards),
            xp = Xp(row.xp),
            level = config.xpCurve.levelFor(row.xp),
            streakDays = row.streakDays,
            streakState = if (row.streakBroken) StreakState.BROKEN else StreakState.ACTIVE,
            ownedIds = inventory.ownedIds(),
        )
    }

    /** Live quest progress for the current windows (drives progress bars). */
    suspend fun questProgress(today: LocalDate = LocalDate.now(zoneId)): List<QuestProgressView> =
        config.quests.map { quest ->
            val key = QuestEngine.windowKey(quest.window, today)
            QuestProgressView(
                quest = quest,
                units = quests.progress(quest.id, key),
                claimed = quests.claimed(quest.id, key),
            )
        }

    /** Rewarded ads watched today (drives the x/5 cap UI). */
    suspend fun adsWatched(today: LocalDate = LocalDate.now(zoneId)): Long = ledger.count("ad.topup", today)

    /**
     * Live drop rates for the Rates screen. Render this verbatim — never
     * hand-write odds anywhere.
     */
    fun rates(bannerId: String): RatesView {
        val banner = config.banners[bannerId] ?: throw IllegalArgumentException("Unknown banner $bannerId")
        return RatesView(
            bannerId = bannerId,
            cost = banner.cost,
            percents = banner.weights.mapValues { (_, w) -> w / 100.0 },
            pityEvery = banner.pityEvery,
            poolSizes = banner.pools.mapValues { (_, pool) -> pool.size },
        )
    }

    // ---------- internals ----------

    private fun eventDay(event: GameEvent): LocalDate =
        when (event) {
            is HabitCompleted -> event.day
            is HabitUncompleted -> event.day
            is DayObserved -> event.day
            else -> LocalDate.ofInstant(event.at, zoneId)
        }

    private fun isQualifying(key: String): Boolean =
        config.streak.qualifyingPrefixes.any { key.startsWith(it) }

    private suspend fun applyCaps(
        key: String,
        day: LocalDate,
        requested: Long,
    ): Long {
        if (requested <= 0) return 0
        var remaining = requested
        val capKey = config.dailyCaps.keys.filter { it != "*" && key.startsWith(it) }.maxByOrNull { it.length }
        if (capKey != null) {
            val spent = ledger.sumPoints(capKey, day)
            remaining = minOf(remaining, (config.dailyCaps.getValue(capKey) - spent).coerceAtLeast(0))
        }
        val global = config.dailyCaps["*"]
        if (global != null) {
            val spentAll = ledger.sumPoints("", day)
            remaining = minOf(remaining, (global - spentAll).coerceAtLeast(0))
        }
        return remaining
    }

    /** Idempotent derived grant (perfect day, milestones). Returns null when already granted. */
    private suspend fun grantDerived(
        source: String,
        refId: String,
        day: LocalDate,
        at: Instant,
        points: Long,
        xp: Long,
    ): Award? {
        val credited =
            ledger.append(LedgerEntry(refId, day, source, null, points, 0, xp, at))
        if (!credited) return null
        val current = profile.get()
        profile.put(
            current.copy(
                points = (current.points + points).coerceAtLeast(0),
                xp = (current.xp + xp).coerceAtLeast(0),
            ),
        )
        return Award(source = source, points = points, xp = xp)
    }

    companion object {
        // Perfect-day bonus. Promoted to EconomyConfig if hosts ever diverge.
        private const val PERFECT_DAY_POINTS = 50L
        private const val PERFECT_DAY_XP = 30L
    }
}
