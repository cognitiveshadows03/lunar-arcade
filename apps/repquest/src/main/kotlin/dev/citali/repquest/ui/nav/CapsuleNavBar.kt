// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.repquest.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.citali.repquest.ui.components.bounceClick

enum class Tab {
    HOME,
    PULL,
    COLLECTION,
}

/** LunarTune-style capsule navbar: floating pill, animated indicator + labels. */
@Composable
fun CapsuleNavBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
    ) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NavItem(Tab.HOME, Icons.Filled.Home, "Home", selected, onSelect)
            NavItem(Tab.PULL, Icons.Filled.Star, "Pull", selected, onSelect)
            NavItem(Tab.COLLECTION, Icons.Filled.GridView, "Album", selected, onSelect)
        }
    }
}

@Composable
private fun RowScope.NavItem(
    tab: Tab,
    icon: ImageVector,
    label: String,
    selected: Tab,
    onSelect: (Tab) -> Unit,
) {
    val sel = tab == selected
    val bg by animateColorAsState(
        if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "navbg-$label",
    )
    val scale by animateFloatAsState(if (sel) 1.12f else 1f, label = "navscale-$label")
    Box(
        modifier =
            Modifier
                .weight(1f)
                .clip(CircleShape)
                .background(bg)
                .bounceClick { onSelect(tab) }
                .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
                tint = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedVisibility(visible = sel) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
