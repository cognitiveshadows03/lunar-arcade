// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.repquest.ui.pull

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.citali.gami.PulledItem
import dev.citali.gami.Rarity
import dev.citali.repquest.ui.GameViewModel
import dev.citali.repquest.ui.components.DelayedReveal
import dev.citali.repquest.ui.components.ItemMedallion
import dev.citali.repquest.ui.theme.RarityTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullScreen(vm: GameViewModel) {
    val state by vm.state.collectAsState()
    val lastPull by vm.lastPull.collectAsState()
    var showRates by remember { mutableStateOf(false) }
    val points by animateIntAsState(state?.points?.amount?.toInt() ?: 0, label = "pullpts")

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Banner art (placeholder gradient until real banner art lands).
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF312E81), Color(0xFF7C3AED), Color(0xFFDB2777)),
                        ),
                    )
                    .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Color.White)
                    Text("STARTER BANNER", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.weight(1f))
                Text("Beasts of Discipline", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("8 collectibles · pity every 20 pulls", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            }
        }

        // Balance + pull buttons.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(12.dp), Alignment.CenterHorizontally) {
                Text("%,d points".format(points), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.pull(1) }, modifier = Modifier.weight(1f)) {
                        Text("Pull ×1 · 100")
                    }
                    Button(onClick = { vm.pull(10) }, modifier = Modifier.weight(1f)) {
                        Text("Pull ×10 · 1,000")
                    }
                }
                OutlinedButton(onClick = { showRates = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Info, null)
                    Spacer(Modifier.padding(2.dp))
                    Text("Rates (live from engine)")
                }
            }
        }

        // Shard shop.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Shard shop", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${state?.shards?.amount ?: 0} shards", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "Dupes salvage into shards. Bad luck protection, deterministic.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                vm.shardShop.entries.sortedBy { it.value.amount }.forEach { (id, price) ->
                    val item = vm.catalog.first { it.id == id }
                    val owned = state?.ownedIds?.contains(id) == true
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ItemMedallion(item, owned = true, size = 44.dp)
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(item.rarity.name, style = MaterialTheme.typography.bodySmall, color = RarityTheme.chip(item.rarity))
                        }
                        if (owned) {
                            Text("Owned", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            OutlinedButton(onClick = { vm.redeem(id) }) {
                                Text("${price.amount} ◆")
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showRates) {
        RatesDialog(onDismiss = { showRates = false }, vm = vm)
    }
    if (lastPull != null) {
        PullResultSheet(pull = lastPull!!.items, pity = lastPull!!.pityTriggered, onDismiss = { vm.clearPull() }, onAgain = { vm.clearPull(); vm.pull(1) })
    }
}

@Composable
private fun RatesDialog(
    onDismiss: () -> Unit,
    vm: GameViewModel,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Drop rates") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                vm.rates.percents.entries.sortedBy { it.key }.forEach { (rarity, pct) ->
                    Row {
                        Text(rarity.name, modifier = Modifier.weight(1f), color = RarityTheme.chip(rarity), fontWeight = FontWeight.SemiBold)
                        Text("%.1f%%".format(pct))
                    }
                }
                Text(
                    "Guaranteed EPIC+ every ${vm.rates.pityEvery} pulls. Rendered live from the engine config — these numbers ARE the roll.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullResultSheet(
    pull: List<PulledItem>,
    pity: Boolean,
    onDismiss: () -> Unit,
    onAgain: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pull) {
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            Arrangement.spacedBy(16.dp),
            Alignment.CenterHorizontally,
        ) {
            Text(
                if (pull.size == 1) "Your pull" else "${pull.size}-pull",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            if (pity) {
                Text("✨ Pity blessed this pull", color = RarityTheme.chip(Rarity.EPIC), style = MaterialTheme.typography.bodyMedium)
            }
            pull.chunked(3).forEachIndexed { row, items ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items.forEachIndexed { col, p ->
                        DelayedReveal(delayMs = (row * 3 + col) * 140) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                ItemMedallion(p.item, owned = true, size = 76.dp)
                                Text(
                                    (if (p.isNew) "NEW " else "") + p.item.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                if (!p.isNew) {
                                    Text("+${p.shardsGained} ◆", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAgain, modifier = Modifier.weight(1f)) { Text("Again · 100") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Collect") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
