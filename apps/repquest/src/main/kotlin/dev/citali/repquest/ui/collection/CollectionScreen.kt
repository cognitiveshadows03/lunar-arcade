// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.repquest.ui.collection

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.citali.gami.Collectible
import dev.citali.repquest.ui.GameViewModel
import dev.citali.repquest.ui.components.DelayedReveal
import dev.citali.repquest.ui.components.ItemMedallion
import dev.citali.repquest.ui.components.bounceClick
import dev.citali.repquest.ui.theme.RarityTheme

@Composable
fun CollectionScreen(vm: GameViewModel) {
    val state by vm.state.collectAsState()
    var selected by remember { mutableStateOf<Collectible?>(null) }
    val owned = state?.ownedIds ?: emptySet()
    val frac = if (vm.catalog.isEmpty()) 0f else owned.size.toFloat() / vm.catalog.size
    val animated by animateFloatAsState(frac, label = "album")

    Column(Modifier.fillMaxSize().padding(20.dp), Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Beasts of Discipline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${owned.size}/${vm.catalog.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
                LinearProgressIndicator(progress = animated, modifier = Modifier.fillMaxWidth())
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(vm.catalog) { item ->
                DelayedReveal(delayMs = vm.catalog.indexOf(item) * 60) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.bounceClick { selected = item },
                    ) {
                        ItemMedallion(item, owned = owned.contains(item.id), size = 72.dp)
                        Text(
                            if (owned.contains(item.id)) item.name else "???",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            item.rarity.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (owned.contains(item.id)) RarityTheme.chip(item.rarity) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }

    selected?.let { item ->
        val isOwned = owned.contains(item.id)
        val shopPrice = vm.shardShop[item.id]?.amount
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(if (isOwned) item.name else "???") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ItemMedallion(item, owned = isOwned, size = 88.dp)
                    Text(item.rarity.name, color = RarityTheme.chip(item.rarity), fontWeight = FontWeight.Bold)
                    Text(
                        if (isOwned) item.flavor else "Not discovered yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    if (!isOwned) {
                        Text(
                            if (shopPrice != null) "Pull the Starter banner or redeem for $shopPrice shards." else "Pull the Starter banner to find it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
        )
    }
}
