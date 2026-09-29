// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.gami

import dev.citali.gami.stores.InMemoryInventoryStore
import dev.citali.gami.stores.InMemoryLedgerStore
import dev.citali.gami.stores.InMemoryProfileStore
import dev.citali.gami.stores.InMemoryQuestStore
import dev.citali.gami.stores.ProfileRow
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class EngineTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val day1 = LocalDate.of(2026, 1, 1)

    private class Rig(
        val engine: GamificationEngine,
        val profile: InMemoryProfileStore,
        val inventory: InMemoryInventoryStore,
    )

    private fun rig(
        seed: ProfileRow = ProfileRow(),
        rng: Random = Random(42),
    ): Rig {
        val profile = InMemoryProfileStore(seed)
        val inventory = InMemoryInventoryStore()
        val engine =
            GamificationEngine(
                config = EconomyConfig.V1,
                ledger = InMemoryLedgerStore(),
                inventory = inventory,
                profile = profile,
                quests = InMemoryQuestStore(),
                zoneId = zone,
                rng = rng,
            )
        return Rig(engine, profile, inventory)
    }

    private fun uuid(): String = UUID.randomUUID().toString()

    private fun at(day: LocalDate) = day.atStartOfDay(zone).toInstant()

    // ---------- faucets ----------

    @Test
    fun `reps earn faucet rate`() =
        runBlocking {
            val (engine, _, _) = rig()
            val out = engine.process(RepsLogged(at(day1), uuid(), "squat", 10))
            assertFalse(out.duplicate)
            assertEquals(20L, out.awards.first { it.source == "rep.squat" }.points)
            assertEquals(20L, engine.state().points.amount)
        }

    @Test
    fun `reprocessing same refId is a no-op`() =
        runBlocking {
            val (engine, _, _) = rig()
            val ref = uuid()
            engine.process(RepsLogged(at(day1), ref, "squat", 10))
            val dup = engine.process(RepsLogged(at(day1), ref, "squat", 10))
            assertTrue(dup.duplicate)
            assertEquals(20L, engine.state().points.amount)
        }

    @Test
    fun `daily prefix cap binds and flags`() =
        runBlocking {
            val (engine, _, _) = rig()
            // 500 squats × 2 = 1000 requested; rep.* cap is 300.
            val out = engine.process(RepsLogged(at(day1), uuid(), "squat", 500))
            val award = out.awards.first { it.source == "rep.squat" }
            assertEquals(300L, award.points)
            assertTrue(award.capped)
        }

    // ---------- habits ----------

    @Test
    fun `perfect day bonus fires once`() =
        runBlocking {
            val (engine, _, _) = rig()
            val o1 = engine.process(HabitCompleted(at(day1), uuid(), "water", day1, 2))
            assertTrue(o1.awards.none { it.source == "day.perfect" })
            val o2 = engine.process(HabitCompleted(at(day1), uuid(), "read", day1, 2))
            assertTrue(o2.awards.any { it.source == "day.perfect" })
            // 10 + 10 habits + 50 perfect.
            assertEquals(70L, engine.state().points.amount)
        }

    @Test
    fun `undo reverses but does not claw back fired perfect-day`() =
        runBlocking {
            val (engine, _, _) = rig()
            engine.process(HabitCompleted(at(day1), uuid(), "water", day1, 1))
            // dayTotalHabits=1 → perfect already fired (10 + 50 = 60).
            assertEquals(60L, engine.state().points.amount)
            engine.process(HabitUncompleted(at(day1), uuid(), "water", day1))
            // -10 reversal; the fired perfect-day bonus is NOT clawed back (by design).
            assertEquals(50L, engine.state().points.amount)
        }

    // ---------- streaks ----------

    @Test
    fun `streak grows on consecutive activity days`() =
        runBlocking {
            val (engine, _, _) = rig()
            engine.process(RepsLogged(at(day1), uuid(), "pushup", 10))
            assertEquals(1, engine.state().streakDays)
            engine.process(RepsLogged(at(day1.plusDays(1)), uuid(), "pushup", 10))
            assertEquals(2, engine.state().streakDays)
        }

    @Test
    fun `skipped day breaks streak on next observe`() =
        runBlocking {
            val (engine, _, _) = rig()
            engine.process(RepsLogged(at(day1), uuid(), "pushup", 10))
            engine.process(DayObserved(at(day1.plusDays(3)), uuid(), day1.plusDays(3)))
            val state = engine.state()
            assertEquals(StreakState.BROKEN, state.streakState)
            assertEquals(1, state.streakDays) // count preserved for repair.
        }

    @Test
    fun `repair continues streak and enforces cooldown`() =
        runBlocking {
            val (engine, _, _) = rig(seed = ProfileRow(points = 1000))
            val d1 = day1
            engine.process(RepsLogged(at(d1), uuid(), "pushup", 10))
            val d4 = d1.plusDays(3)
            engine.process(DayObserved(at(d4), uuid(), d4))
            assertTrue(engine.spend(Points(100), "streak.repair", uuid(), d4))
            assertEquals(StreakState.ACTIVE, engine.repairStreak(d4))
            engine.process(RepsLogged(at(d4), uuid(), "pushup", 10))
            assertEquals(2, engine.state().streakDays)

            // Break again immediately → repair must refuse (7-day cooldown).
            val d9 = d1.plusDays(8)
            engine.process(DayObserved(at(d9), uuid(), d9))
            assertEquals(StreakState.BROKEN, engine.state().streakState)
            try {
                engine.repairStreak(d9)
                fail("expected RepairOnCooldown")
            } catch (expected: RepairOnCooldown) {
                assertEquals(d4.plusDays(7), expected.until)
            }
        }

    @Test
    fun `backward dates never regress streak`() =
        runBlocking {
            val (engine, _, _) = rig()
            engine.process(RepsLogged(at(day1.plusDays(5)), uuid(), "pushup", 10))
            engine.process(RepsLogged(at(day1), uuid(), "pushup", 10))
            val state = engine.state()
            assertEquals(1, state.streakDays)
            assertEquals(StreakState.ACTIVE, state.streakState)
        }

    // ---------- pulls ----------

    @Test
    fun `pull spends and grants items`() =
        runBlocking {
            val (engine, _, inventory) = rig(seed = ProfileRow(points = 1000), rng = Random(7))
            val result = engine.pull("starter", count = 3)
            assertEquals(3, result.items.size)
            assertEquals(700L, engine.state().points.amount)
            // Owned set always mirrors pulled ids exactly (dupes collapse).
            assertEquals(result.items.map { it.item.id }.toSet(), inventory.ownedIds())
        }

    @Test
    fun `broke pull throws without spending`() =
        runBlocking {
            val (engine, _, _) = rig(seed = ProfileRow(points = 50))
            try {
                engine.pull("starter")
                fail("expected InsufficientPoints")
            } catch (expected: InsufficientPoints) {
                assertEquals(100L, expected.required.amount)
            }
            assertEquals(50L, engine.state().points.amount)
        }

    @Test
    fun `pity guarantees epic or better`() =
        runBlocking {
            val (engine, _, inventory) = rig(seed = ProfileRow(points = 1000), rng = Random(1))
            inventory.setPityCounter("starter", 19)
            val result = engine.pull("starter")
            assertTrue(result.pityTriggered)
            assertTrue(result.items.single().item.rarity >= Rarity.EPIC)
        }

    @Test
    fun `dupes salvage to shards`() =
        runBlocking {
            val (engine, _, inventory) = rig(seed = ProfileRow(points = 1000), rng = Random(3))
            for (id in EconomyConfig.V1.items.keys) inventory.add(id)
            val before = engine.state().shards.amount
            val result = engine.pull("starter")
            assertFalse(result.items.single().isNew)
            assertTrue(engine.state().shards.amount > before)
        }

    @Test
    fun `odds roughly match config over volume`() =
        runBlocking {
            val (engine, _, _) = rig(seed = ProfileRow(points = 10_000_000), rng = Random(11))
            val counts = mutableMapOf<Rarity, Int>()
            repeat(2000) {
                val rarity = engine.pull("starter").items.single().item.rarity
                counts[rarity] = (counts[rarity] ?: 0) + 1
            }
            val legendary = counts[Rarity.LEGENDARY] ?: 0
            val epic = counts[Rarity.EPIC] ?: 0
            // Mean ≈ 20 (natural + 10%-of-pity); bounds are >3σ either side.
            assertTrue(legendary in 5..40, "legendary=$legendary")
            // Pity guarantees ≥1 epic+ per 20 pulls → ≥100 over 2000.
            assertTrue(epic + legendary >= 100, "epic+legendary=${epic + legendary}")
        }

    // ---------- quests / levels / shop ----------

    @Test
    fun `daily quest pays once per window`() =
        runBlocking {
            val (engine, _, _) = rig()
            // "mover": 100 reps/day → 30 pts. 60 pushups twice (120 total).
            val o1 = engine.process(RepsLogged(at(day1), uuid(), "pushup", 60))
            assertTrue(o1.questsCompleted.isEmpty())
            val o2 = engine.process(RepsLogged(at(day1), uuid(), "pushup", 60))
            assertEquals(listOf("mover"), o2.questsCompleted)
            // 120 faucet + 30 quest.
            assertEquals(150L, engine.state().points.amount)
            // More reps same day: no second payout.
            val o3 = engine.process(RepsLogged(at(day1), uuid(), "pushup", 100))
            assertTrue(o3.questsCompleted.isEmpty())
        }

    @Test
    fun `level up is reported`() =
        runBlocking {
            val curve = EconomyConfig.V1.xpCurve
            val seed = ProfileRow(xp = curve.xpForLevel(2) - 1)
            val (engine, _, _) = rig(seed = seed)
            assertEquals(1, engine.state().level)
            val out = engine.process(WorkoutCompleted(at(day1), uuid(), "pushup", 50, 300))
            assertEquals(2, out.levelUpTo)
        }

    @Test
    fun `shard shop redeem works and rejects double-buy`() =
        runBlocking {
            val (engine, _, _) = rig(seed = ProfileRow(shards = 100))
            val item = engine.redeem("ember")
            assertEquals("ember", item.id)
            assertEquals(60L, engine.state().shards.amount)
            try {
                engine.redeem("ember")
                fail("expected AlreadyOwned")
            } catch (_: AlreadyOwned) {
                // expected
            }
        }

    @Test
    fun `rates view matches config`() =
        runBlocking {
            val (engine, _, _) = rig()
            val rates = engine.rates("starter")
            assertEquals(100, rates.cost.amount)
            assertEquals(70.0, rates.percents.getValue(Rarity.COMMON))
            assertEquals(0.5, rates.percents.getValue(Rarity.LEGENDARY))
            assertEquals(20, rates.pityEvery)
        }

    @Test
    fun `config validation rejects broken odds`() {
        // copy() re-runs init{validate()} — the throw happens inside the block.
        assertFailsWith<IllegalArgumentException> {
            EconomyConfig.V1.copy(
                banners = mapOf(
                    "bad" to EconomyConfig.V1.banners.getValue("starter").copy(
                        weights = mapOf(Rarity.COMMON to 9999),
                    ),
                ),
            )
        }
    }
}
