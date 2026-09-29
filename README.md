# Lunar Arcade

Monorepo for the Lunar Labs app suite: gamified, local-first Android apps
sharing one rewards engine. No backends, no accounts, no real-money anything.

```
lunar-arcade/
├── core/gamification/   # shared engine: points, pulls, pity, streaks, quests (pure Kotlin)
├── simulator/           # JVM economy tuner: replays player archetypes, prints stats
├── apps/repquest/       # fitness app: camera rep-counting + habits + collections (in scaffold)
├── apps/focusquest/     # focus app: reuses the engine (later)
└── docs/SPEC.md         # engine specification (start here)
```

## Quickstart

Prereqs: JDK 17, Android Studio Koala+.

```sh
git clone https://github.com/cognitiveshadows03/lunar-arcade.git
cd lunar-arcade
./gradlew :core:gamification:test   # engine unit tests (no SDK needed)
./gradlew :simulator:run            # economy simulation (tune drop rates with data)
./gradlew :apps:repquest:assembleDebug
```

Or open the project root in Android Studio and sync.

## The engine

`:core:gamification` is event-sourced and config-driven: host apps emit
domain events (`RepsLogged`, `HabitCompleted`, `FocusSessionCompleted`,
`AdRewardEarned`…), the engine turns them into points, XP, quests, and
gacha pulls. Every rate, cap, odd, and pity value lives in one
`EconomyConfig` — the in-app Rates page renders from the same object pulls
roll against, so odds can never drift from what's displayed.

Full contract: [`docs/SPEC.md`](docs/SPEC.md).

## Economy tuning

Never hand-tune drop rates. Change `EconomyConfig`, run the simulator, read
the rarity histogram and days-to-first-epic, commit the output next to the
change.

## License

GPL-3.0-only — see [LICENSE](LICENSE). Same as LunarTune: open development,
no closed-source reskin clones. (Proprietary ad SDKs, when added, live in a
`gms` flavor only; the `foss` flavor stays clean for FOSS stores.)
