// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.gami

/** Points + XP granted per [GameEvent.units] of a faucet source key. */
data class Faucet(
    val pointsPerUnit: Long,
    val xpPerUnit: Long,
) {
    init {
        require(pointsPerUnit >= 0 && xpPerUnit >= 0) { "faucet rates must be non-negative" }
    }
}

data class AdRule(
    val pointsPerAd: Long,
    val maxPerDay: Int,
)

enum class QuestWindow {
    DAILY,
    WEEKLY,
}

data class QuestMatcher(
    /** Simple class name of the event, e.g. "RepsLogged". */
    val eventType: String,
    /** Optional [GameEvent.sourceKey] prefix filter. */
    val keyPrefix: String? = null,
)

data class QuestDef(
    val id: String,
    val title: String,
    val window: QuestWindow,
    val matcher: QuestMatcher,
    val targetUnits: Long,
    val rewardPoints: Long,
    val rewardXp: Long,
)

data class BannerConfig(
    val id: String,
    val cost: Points,
    /** Rarity weights in basis points; must sum to exactly 10_000. */
    val weights: Map<Rarity, Int>,
    /** Rarity → item ids (uniform roll within the rarity). */
    val pools: Map<Rarity, List<String>>,
    /** Guaranteed EPIC-or-better every N pulls. */
    val pityEvery: Int,
    /** Shards granted per duplicate of each rarity. */
    val dupeShards: Map<Rarity, Long>,
)

data class MilestoneReward(
    val points: Long,
    val xp: Long,
)

data class StreakConfig(
    /** Source-key prefixes whose events count as daily activity. */
    val qualifyingPrefixes: List<String>,
    /** Minimum days between repairs (rolling). */
    val repairCooldownDays: Int,
    /** What the host charges (points) or bypasses (ad) before [GamificationEngine.repairStreak]. */
    val repairCost: Points,
    /** streakDays → reward, granted on reaching. */
    val milestones: Map<Int, MilestoneReward>,
)

/**
 * The entire economy in one object. Validated on construction — a typo'd
 * economy fails fast instead of shipping broken odds. Bump [version] on
 * every tuning change and commit simulator output alongside.
 */
data class EconomyConfig(
    val version: Int,
    val faucets: Map<String, Faucet>,
    /** Prefix → max points/day. `"*"` is the global net over cap-counted entries. */
    val dailyCaps: Map<String, Long>,
    val adTopUp: AdRule,
    val xpCurve: XpCurve,
    val banners: Map<String, BannerConfig>,
    /** Full item catalog (host content — V1 ships samples; hosts replace). */
    val items: Map<String, Collectible>,
    /** Deterministic pity outlet: itemId → shard price. */
    val shardShop: Map<String, Shards>,
    val streak: StreakConfig,
    val quests: List<QuestDef>,
) {
    init {
        validate()
    }

    private fun validate() {
        require(dailyCaps.values.all { it >= 0 }) { "daily caps must be non-negative" }
        require(quests.map { it.id }.toSet().size == quests.size) { "quest ids must be unique" }
        for ((id, banner) in banners) {
            require(banner.weights.values.sum() == 10_000) {
                "banner $id weights must sum to 10000, was ${banner.weights.values.sum()}"
            }
            require(banner.pityEvery > 0) { "banner $id pityEvery must be positive" }
            for ((rarity, weight) in banner.weights) {
                if (weight > 0) {
                    require(!banner.pools[rarity].isNullOrEmpty()) {
                        "banner $id has weight for $rarity but an empty pool"
                    }
                }
            }
            val poolIds = banner.pools.values.flatten()
            require(poolIds.all { items.containsKey(it) }) {
                "banner $id pools reference unknown items"
            }
            require(banner.dupeShards.keys.containsAll(Rarity.entries)) {
                "banner $id dupeShards must cover every rarity"
            }
        }
        require(shardShop.keys.all { items.containsKey(it) }) {
            "shardShop references unknown items"
        }
    }

    companion object {
        /** Starter economy. Tune only via the simulator (see :simulator). */
        val V1: EconomyConfig = EconomyConfig(
            version = 1,
            faucets = mapOf(
                "rep.pushup" to Faucet(pointsPerUnit = 1, xpPerUnit = 0),
                "rep.jack" to Faucet(pointsPerUnit = 1, xpPerUnit = 0),
                "rep.squat" to Faucet(pointsPerUnit = 2, xpPerUnit = 0),
                "rep.lunge" to Faucet(pointsPerUnit = 2, xpPerUnit = 0),
                "workout.complete" to Faucet(pointsPerUnit = 25, xpPerUnit = 20),
                "habit.complete" to Faucet(pointsPerUnit = 10, xpPerUnit = 10),
                "focus.session.verified" to Faucet(pointsPerUnit = 30, xpPerUnit = 25),
                "focus.session.plain" to Faucet(pointsPerUnit = 15, xpPerUnit = 10),
                "ad.topup" to Faucet(pointsPerUnit = 50, xpPerUnit = 0),
            ),
            dailyCaps = mapOf(
                "rep." to 300,
                "habit." to 100,
                "focus." to 150,
                "ad.topup" to 250,
                "*" to 600,
            ),
            adTopUp = AdRule(pointsPerAd = 50, maxPerDay = 5),
            xpCurve = XpCurve(),
            banners = mapOf(
                "starter" to BannerConfig(
                    id = "starter",
                    cost = Points(100),
                    weights = mapOf(
                        Rarity.COMMON to 7000,
                        Rarity.RARE to 2500,
                        Rarity.EPIC to 450,
                        Rarity.LEGENDARY to 50,
                    ),
                    pools = mapOf(
                        Rarity.COMMON to listOf("pebble", "sprout"),
                        Rarity.RARE to listOf("ember", "tide"),
                        Rarity.EPIC to listOf("gale", "quartz"),
                        Rarity.LEGENDARY to listOf("phoenix", "leviathan"),
                    ),
                    pityEvery = 20,
                    dupeShards = mapOf(
                        Rarity.COMMON to 1,
                        Rarity.RARE to 3,
                        Rarity.EPIC to 12,
                        Rarity.LEGENDARY to 50,
                    ),
                ),
            ),
            items = mapOf(
                "pebble" to Collectible("pebble", "starter-01", Rarity.COMMON, "Pebble", "Every mountain starts here.", "beast/pebble"),
                "sprout" to Collectible("sprout", "starter-01", Rarity.COMMON, "Sprout", "Water daily. You too.", "beast/sprout"),
                "ember" to Collectible("ember", "starter-01", Rarity.RARE, "Ember", "Still warm from yesterday's set.", "beast/ember"),
                "tide" to Collectible("tide", "starter-01", Rarity.RARE, "Tide", "Consistency compounds.", "beast/tide"),
                "gale" to Collectible("gale", "starter-01", Rarity.EPIC, "Gale", "Faster than your excuses.", "beast/gale"),
                "quartz" to Collectible("quartz", "starter-01", Rarity.EPIC, "Quartz", "Pressure makes favorites.", "beast/quartz"),
                "phoenix" to Collectible("phoenix", "starter-01", Rarity.LEGENDARY, "Phoenix", "Every streak-breaker rises again.", "beast/phoenix"),
                "leviathan" to Collectible("leviathan", "starter-01", Rarity.LEGENDARY, "Leviathan", "Lurks in the deep end of discipline.", "beast/leviathan"),
            ),
            shardShop = mapOf(
                "ember" to Shards(40),
                "tide" to Shards(40),
                "gale" to Shards(150),
                "quartz" to Shards(150),
                "phoenix" to Shards(200),
                "leviathan" to Shards(200),
            ),
            streak = StreakConfig(
                qualifyingPrefixes = listOf("rep.", "habit.complete", "workout.complete", "focus.session."),
                repairCooldownDays = 7,
                repairCost = Points(100),
                milestones = mapOf(
                    7 to MilestoneReward(points = 100, xp = 50),
                    30 to MilestoneReward(points = 400, xp = 200),
                    100 to MilestoneReward(points = 1500, xp = 500),
                ),
            ),
            quests = listOf(
                QuestDef(
                    id = "mover",
                    title = "Mover — log 100 reps",
                    window = QuestWindow.DAILY,
                    matcher = QuestMatcher(eventType = "RepsLogged"),
                    targetUnits = 100,
                    rewardPoints = 30,
                    rewardXp = 20,
                ),
                QuestDef(
                    id = "steady",
                    title = "Steady — check 3 habits",
                    window = QuestWindow.DAILY,
                    matcher = QuestMatcher(eventType = "HabitCompleted"),
                    targetUnits = 3,
                    rewardPoints = 20,
                    rewardXp = 15,
                ),
                QuestDef(
                    id = "grinder",
                    title = "Grinder — 500 reps this week",
                    window = QuestWindow.WEEKLY,
                    matcher = QuestMatcher(eventType = "RepsLogged"),
                    targetUnits = 500,
                    rewardPoints = 100,
                    rewardXp = 60,
                ),
            ),
        )
    }
}
