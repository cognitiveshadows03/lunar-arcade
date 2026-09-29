// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.repquest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.citali.gami.DayObserved
import dev.citali.gami.EconomyConfig
import dev.citali.gami.GamificationEngine
import dev.citali.gami.InsufficientPoints
import dev.citali.gami.PlayerState
import dev.citali.gami.RepsLogged
import dev.citali.gami.WorkoutCompleted
import dev.citali.gami.stores.InMemoryInventoryStore
import dev.citali.gami.stores.InMemoryLedgerStore
import dev.citali.gami.stores.InMemoryProfileStore
import dev.citali.gami.stores.InMemoryQuestStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DemoScreen() }
    }
}

/**
 * Temporary engine demo: proves :core:gamification wiring end-to-end with
 * in-memory stores. Replaced by the real UI + Room stores in the next step.
 */
@Composable
private fun DemoScreen() {
    val engine =
        remember {
            GamificationEngine(
                EconomyConfig.V1,
                InMemoryLedgerStore(),
                InMemoryInventoryStore(),
                InMemoryProfileStore(),
                InMemoryQuestStore(),
            )
        }
    var state by remember { mutableStateOf<PlayerState?>(null) }
    var log by remember { mutableStateOf("Engine ready. Tap a button to earn.") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        engine.process(DayObserved(Instant.now(), UUID.randomUUID().toString(), LocalDate.now()))
        state = engine.state()
    }

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("RepQuest · engine demo", style = MaterialTheme.typography.headlineSmall)
            Text(
                state?.let {
                    "${it.points.amount} pts · Lv ${it.level} · \uD83D\uDD25 ${it.streakDays} · ${it.ownedIds.size} items"
                } ?: "loading…",
            )
            Button(onClick = {
                scope.launch {
                    val out =
                        engine.process(RepsLogged(Instant.now(), UUID.randomUUID().toString(), "squat", 10))
                    state = engine.state()
                    val quest = if (out.questsCompleted.isNotEmpty()) " · quest: ${out.questsCompleted}" else ""
                    log = "+10 squats → ${out.awards.sumOf { it.points }} pts$quest"
                }
            }) {
                Text("+10 squats")
            }
            Button(onClick = {
                scope.launch {
                    engine.process(WorkoutCompleted(Instant.now(), UUID.randomUUID().toString(), "squat", 30, 120))
                    state = engine.state()
                    log = "Workout logged (+25 pts, +20 xp)."
                }
            }) {
                Text("Log workout")
            }
            Button(onClick = {
                scope.launch {
                    try {
                        val pull = engine.pull("starter")
                        state = engine.state()
                        log =
                            pull.items.joinToString {
                                (if (it.isNew) "NEW " else "") +
                                    "${it.item.name} [${it.item.rarity}]" +
                                    (if (!it.isNew) " (+${it.shardsGained} shards)" else "")
                            }
                    } catch (e: InsufficientPoints) {
                        log = "Need ${e.required.amount} pts, have ${e.balance.amount}."
                    }
                }
            }) {
                Text("Pull (100 pts)")
            }
            Text(log, style = MaterialTheme.typography.bodySmall)
        }
    }
}
