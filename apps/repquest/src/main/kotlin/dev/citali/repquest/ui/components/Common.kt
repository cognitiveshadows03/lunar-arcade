// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.repquest.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.citali.gami.Collectible
import dev.citali.repquest.ui.theme.RarityTheme
import kotlinx.coroutines.delay

/**
 * Press physics: the whole app's "smooth" feel. 0.95 squash on press with a
 * medium spring back. Apply to every tappable card/row.
 */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier =
    composed {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.95f else 1f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "press",
        )
        graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
    }

/**
 * Item medallion: rarity gradient + initial when owned, dark + lock when not.
 * Real art replaces the letter later; layout stays identical.
 */
@Composable
fun ItemMedallion(
    item: Collectible,
    owned: Boolean,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier,
) {
    val (top, bottom) = RarityTheme.gradient(item.rarity)
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    if (owned) {
                        Brush.verticalGradient(listOf(top, bottom))
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        )
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (owned) {
            Text(
                text = item.name.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Locked",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

/** Staggered entrance wrapper (pull reveals, grid pop-ins). */
@Composable
fun DelayedReveal(
    delayMs: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
    ) {
        content()
    }
}
