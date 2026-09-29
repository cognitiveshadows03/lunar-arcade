// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.repquest.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.citali.gami.QuestProgressView
import dev.citali.gami.StreakState
import dev.citali.repquest.ui.GameViewModel
import dev.citali.repquest.ui.components.bounceClick
import java.time.LocalTime

@Composable
fun HomeScreen(vm: GameViewModel) {
    val state by vm.state.collectAsState()
    val quests by vm.quests.collectAsState()
    val adsWatched by vm.adsWatched.collectAsState()
    val checked by vm.checkedHabits.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = greeting(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Move. Earn. Collect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StreakPill(vm)
        }

        // Balances.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BalanceCard(
                icon = { Icon(Icons.Filled.Bolt, null, tint = MaterialTheme.colorScheme.primary) },
                value = state?.points?.amount?.toInt() ?: 0,
                label = "Points",
                modifier = Modifier.weight(1f),
            )
            BalanceCard(
                icon = { Icon(Icons.Filled.Diamond, null, tint = MaterialTheme.colorScheme.secondary) },
                value = state?.shards?.amount?.toInt() ?: 0,
                label = "Shards",
                modifier = Modifier.weight(1f),
            )
        }

        // Level.
        state?.let {
            val cur = vm.xpCurve.xpForLevel(it.level)
            val next = vm.xpCurve.xpForLevel(it.level + 1)
            val frac = if (next > cur) ((it.xp.amount - cur).toFloat() / (next - cur)).coerceIn(0f, 1f) else 1f
            val animated by animateFloatAsState(frac, label = "xp")
            val xpNow by animateIntAsState(it.xp.amount.toInt(), label = "xpnum")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Level ${it.level}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("$xpNow / $next XP", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LinearProgressIndicator(progress = animated, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Streak repair (only when broken).
        if (state?.streakState == StreakState.BROKEN) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Shield, null, tint = MaterialTheme.colorScheme.error)
                        Text("Streak broken!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Repair to keep your ${state?.streakDays}-day streak alive. One repair per week.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.repairWithPoints() }, modifier = Modifier.weight(1f)) {
                            Text("${vm.repairCost.amount} pts")
                        }
                        OutlinedButton(onClick = { vm.repairWithAd() }, modifier = Modifier.weight(1f)) {
                            Text("Watch ad")
                        }
                    }
                }
            }
        }

        // Log reps.
        LogRepsCard(vm)

        // Habits.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
                Text(
                    "Today's habits",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp),
                )
                vm.habits.forEach { habit ->
                    val done = checked.contains(habit.id)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .bounceClick { vm.toggleHabit(habit.id) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = done, onCheckedChange = { vm.toggleHabit(habit.id) })
                        Text(habit.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("+10", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Quests.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(12.dp)) {
                Text("Quests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                quests.forEach { QuestRow(it) }
            }
        }

        // Demo ad top-up.
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f)) {
                    Text("Need a boost?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "+50 pts per ad · $adsWatched/${vm.adCap} today (demo)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { vm.watchAd() }, enabled = adsWatched < vm.adCap) {
                    Text("Watch")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StreakPill(vm: GameViewModel) {
    val state by vm.state.collectAsState()
    val days by animateIntAsState(state?.streakDays ?: 0, label = "streak")
    Card {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.LocalFireDepartment,
                null,
                tint = if (state?.streakState == StreakState.BROKEN) MaterialTheme.colorScheme.error else ColorFlame,
            )
            Text("$days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        }
    }
}

private val ColorFlame = androidx.compose.ui.graphics.Color(0xFFFF6D3A)

@Composable
private fun BalanceCard(
    icon: @Composable () -> Unit,
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val animated by animateIntAsState(value, label = "bal-$label")
    Card(modifier) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            icon()
            Column {
                Text("%,d".format(animated), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LogRepsCard(vm: GameViewModel) {
    var exercise by remember { mutableStateOf(vm.exercises[0].first) }
    var reps by remember { mutableStateOf(10) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary)
                Text("Log reps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.exercises.forEach { (id, label, rate) ->
                    FilterChip(
                        selected = exercise == id,
                        onClick = { exercise = id },
                        label = { Text("$label +$rate") },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 25, 50).forEach { n ->
                    FilterChip(
                        selected = reps == n,
                        onClick = { reps = n },
                        label = { Text("×$n") },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.logReps(exercise, reps) }, modifier = Modifier.weight(1f)) {
                    Text("Log $reps")
                }
                OutlinedButton(onClick = { vm.logWorkout() }, modifier = Modifier.weight(1f)) {
                    Text("Finish workout")
                }
            }
        }
    }
}

@Composable
private fun QuestRow(view: QuestProgressView) {
    val frac = (view.units.toFloat() / view.quest.targetUnits).coerceIn(0f, 1f)
    val animated by animateFloatAsState(frac, label = "quest-${view.quest.id}")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), Arrangement.spacedBy(6.dp)) {
            Text(view.quest.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = animated, modifier = Modifier.fillMaxWidth())
            Text(
                "${view.units.coerceAtMost(view.quest.targetUnits)}/${view.quest.targetUnits} · +${view.quest.rewardPoints} pts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (view.claimed) {
            Icon(Icons.Filled.CheckCircle, "Done", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun greeting(): String =
    when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Night shift"
    }
