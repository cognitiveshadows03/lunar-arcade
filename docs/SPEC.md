# Gami Core — shared gamification engine spec (v1)

Engine-agnostic rewards module consumed by the fitness app (v1) and the focus
app (v2). Pure Kotlin, zero Android dependencies, fully unit-testable.

Design principles:

1. **Event-sourced** — host apps emit domain events; the engine turns them
   into points/XP/quests/pulls. The engine never knows what a squat is.
2. **Config-driven economy** — every rate, cap, odd, and pity value lives in
   one `EconomyConfig`. The in-app "Rates" page renders from the same object
   players pull against. Single source of truth, no drift.
3. **Host owns storage & UI** — the engine defines store *interfaces*; hosts
   implement them (Room). The engine ships in-memory fakes for tests.
4. **No money, no ads, no network inside the module.** The host shows the ad,
   then emits `AdRewardEarned`. The engine only counts it against caps.

---

## 1. Module layout

```
gami-core/
  core/gamification/          # THE module (pure Kotlin + kotlinx-datetime? no —
                              # java.time only; minSdk irrelevant, it's JVM)
    Domain.kt                 # value types, Collectible, Rarity, PlayerState
    Events.kt                 # GameEvent sealed hierarchy
    EconomyConfig.kt          # config schema + validation
    Engine.kt                 # GamificationEngine facade
    Ledger.kt                 # append-only award log logic
    PullEngine.kt             # weighted rolls + pity + dupe→shard
    StreakEngine.kt           # daily streaks + repair rules
    QuestEngine.kt            # daily/weekly count quests
    LevelCurve.kt             # XP → level math
    stores/
      Stores.kt               # LedgerStore, InventoryStore, ProfileStore interfaces
      InMemoryStores.kt       # fakes for tests + simulator
  simulator/                  # JVM CLI: replays player archetypes × N days,
                              # prints earn/pull/pity stats. THE tuning tool.
  docs/ECONOMY.md             # human-readable economy (generated from config)
```

Package: `dev.citali.gami` (matches your LunarTune namespace).

---

## 2. Domain model

```kotlin
@JvmInline value class Points(val amount: Long)   // spendable
@JvmInline value class Shards(val amount: Long)   // dupe salvage, shard-shop only
@JvmInline value class Xp(val amount: Long)       // lifetime, never spent

enum class Rarity { COMMON, RARE, EPIC, LEGENDARY }

data class Collectible(
    val id: String,            // "phoenix"
    val collectionId: String,  // "beasts-01"
    val rarity: Rarity,
    val name: String,
    val flavor: String,
    val artRef: String,        // host maps "beast/phoenix" → drawable. Engine holds no art.
)

data class PlayerState(
    val points: Points,
    val shards: Shards,
    val xp: Xp,
    val level: Int,
    val streakDays: Int,
    val ownedIds: Set<String>,
)
```

---

## 3. Events (host → engine)

Every event carries `at: Instant` and a host-generated `refId` (UUID per
real-world action). The ledger dedupes on `refId` → crash/retry safe, no
double awards.

```kotlin
sealed interface GameEvent {
    val at: Instant
    val refId: String
}

// Fitness host
data class RepsLogged(val exerciseId: String, val reps: Int, ...)
data class WorkoutCompleted(val exerciseId: String, val totalReps: Int, val durationSec: Long, ...)
// Habit host (same app, phase 1)
data class HabitCompleted(val habitId: String, val day: LocalDate, ...)
data class HabitUncompleted(val habitId: String, val day: LocalDate, ...) // same-day undo → reversal entry
// Focus host (app 2 — engine already supports it)
data class FocusSessionCompleted(val minutes: Int, val verified: Boolean, ...)
// Shared
data class AdRewardEarned(val placement: String, ...)  // ONLY after ad SDK confirms reward
data class DayObserved(val day: LocalDate, ...)        // host emits on app open; drives streak eval
```

Source keys are strings (`"rep.squat"`, `"habit.complete"`, `"ad.topup"`)
so new hosts add faucets without touching the engine.

---

## 4. EconomyConfig (v1 numbers — tune via simulator)

```kotlin
data class EconomyConfig(
    val version: Int = 1,
    val faucets: Map<String, Faucet>,       // source key → points + xp per unit
    val dailyCaps: Map<String, Long>,       // source key → max points/day ("*" = global)
    val adTopUp: AdRule,                    // points per ad, max ads/day
    val xp: XpCurve,                        // xpForLevel(n) = (100 * n^1.5).toLong()
    val banners: Map<String, BannerConfig>,
    val shardShop: Map<String, Shards>,     // itemId → price (deterministic pity outlet)
    val streak: StreakConfig,
)

data class BannerConfig(
    val id: String,
    val cost: Points,
    val weights: Map<Rarity, Int>,          // e.g. 7000/2500/450/50 (basis points of 10000)
    val pools: Map<Rarity, List<String>>,   // rarity → item ids
    val pityEvery: Int,                     // guaranteed EPIC+ every N pulls
    val dupeShards: Map<Rarity, Long>,      // salvage per dupe rarity
)
```

### v1 faucet table (starter values)

| Source key | Points | XP | Daily cap |
|---|---|---|---|
| `rep.pushup`, `rep.jack` | 1/rep | 0 | `rep.*` → 300 |
| `rep.squat`, `rep.lunge` | 2/rep | 0 | (shared 300) |
| `workout.complete` | 25 | 20 | — |
| `habit.complete` | 10 | 10 | `habit.*` → 100 |
| `day.perfect` (engine-derived) | 50 | 30 | — |
| `focus.session.verified` | 30 / 25 min | 25 | `focus.*` → 150 |
| `focus.session.plain` | 15 / 25 min | 10 | (shared 150) |
| `streak.milestone.7/30/100` | 100/400/1500 | 50/200/500 | — |
| `ad.topup` | 50/ad | 0 | 5 ads/day |
| `*` (global safety net) | — | — | 600/day |

### v1 banner "starter"

Cost 100 pts · weights C70/R25/E4.5/L0.5 · pity EPIC+ every 20 ·
shards C1/R3/E12/L50 · shard shop: rare pick 40 · epic pick 150 ·
legendary pick 200.

---

## 5. Engine API

```kotlin
class GamificationEngine(
    private val config: EconomyConfig,   // validated on construction (weights sum, pools non-empty…)
    private val ledger: LedgerStore,
    private val inventory: InventoryStore,
    private val profile: ProfileStore,
    private val quests: QuestStore,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val rng: Random = Random,
) {
    /** Process one event → awards + quest progress + level-ups + streak updates. */
    suspend fun process(event: GameEvent): EngineOutcome

    /** Spend points → roll items → auto-salvage dupes. Throws if broke. */
    suspend fun pull(bannerId: String, count: Int = 1, refId: String = …): PullResult

    /** Deterministic shard-shop purchase. */
    suspend fun redeem(itemId: String, refId: String = …): Collectible

    /** Spend points for a sink (streak-repair payment, …). False when broke. */
    suspend fun spend(points: Points, reason: String, refId: String, day: LocalDate = …): Boolean

    /** Repair a broken streak (host calls spend() first for pts, or nothing for ad-paid). */
    suspend fun repairStreak(today: LocalDate): StreakState

    suspend fun state(): PlayerState
    fun rates(bannerId: String): RatesView  // → rendered verbatim on the Rates screen
}
```

Threading: the engine is NOT internally synchronized — hosts serialize
calls (a Mutex in the repository). Balance checks and ledger appends
assume no concurrent mutation. (All timestamps come from host events, so
no Clock dependency is needed.)

Pull algorithm: weighted rarity roll (basis points) → uniform item within
rarity → new? add to inventory : salvage to shards → increment pity counter
(reset on EPIC+). All landing in the ledger as entries (auditable).

Streak rules: a day counts if any *qualifying* source earned that day
(config list). Missed day → `BROKEN` (repairable once per rolling 7 days).
Anti-time-travel: track max observed date; backward dates never regress state.

---

## 6. Store ports (host implements with Room)

```kotlin
interface LedgerStore {
    suspend fun append(e: LedgerEntry): Boolean  // false if refId already seen
    suspend fun sumPoints(prefix: String, day: LocalDate): Long      // cap-counted rows only
    suspend fun positiveTags(prefix: String, day: LocalDate): Set<String>  // tags with net > 0
    suspend fun count(prefix: String, day: LocalDate): Long
}
```

`LedgerEntry.countsTowardCaps` is false for pure spends (pulls, redeems,
repair payments) so spending can never free up earn-room under the daily
caps. Habit undo entries keep it true, so undoing correctly restores cap
room — and perfect-day detection uses net-positive tags, so undoing can't
fake a perfect day.
interface InventoryStore {
    suspend fun ownedIds(): Set<String>
    suspend fun add(id: String)
    suspend fun pityCounter(bannerId: String): Int
    suspend fun setPityCounter(bannerId: String, n: Int)
}
interface ProfileStore {
    suspend fun get(): ProfileRow      // points, shards, xp, streakDays, streakState, maxDate, lastRepair
    suspend fun put(row: ProfileRow)   // single-row table; hosts can DataStore this instead
}
```

Suggested Room schema: `ledger(refId PK, day, source, points, xp, at)` +
`inventory(itemId PK, obtainedAt)` + `pity(bannerId PK, counter)` +
`profile(id=1, …)`. Indices on `(day, source)`.

---

## 7. Host integration checklist

1. Add `:core:gamification` module dependency.
2. Implement the 3 stores (Room entities above; ~150 lines).
3. Instantiate `EconomyConfig.V1` (copy v1 table; bump `version` when tuning).
4. Emit events from features (workout screen, habit check, focus timer,
   ad callback, app-open `DayObserved`).
5. Build UI: balance bar, pull screen, collection album, **Rates screen
   (render `engine.rates()` — never hand-write odds)**.
6. Run simulator after every economy change; commit the output in `docs/`.

---

## 8. Simulator (don't skip this)

JVM `main()` in `:simulator`. Archetypes: `casual` (3 days/wk, 1 habit),
`daily` (all habits + workout), `grinder` (caps every day). Each run prints:
total pulls @30/90 days · rarity histogram vs configured odds · pity
trigger rate · days-to-first-epic · shard income · cap-hit frequency.
If `grinder` reaches legendary < 30 days or `daily` > 180, retune config.

---

## 9. Testing

- JVM unit tests, fake clock + in-memory stores. No Robolectric, no emulator.
- Must-have cases: idempotent reprocess · daily caps · pity guarantee (force
  counter to 19, assert EPIC+) · odds sanity (10k pulls ≈ weights ± tol) ·
  streak break/repair/1-per-7d · backward-date no-regress · broke pull throws ·
  dupe salvage math.
- Config validation test: weights sum to 10000, pools non-empty, shop items
  exist — fails the build on a typo'd economy.

---

## 10. Explicit non-goals (v1)

No UI · no Android imports · no ads/network · no art assets · no
cross-device sync · no real-money anything · no limited-time banners.
Quests: count-based daily/weekly only (no chains, no branching — v2).
