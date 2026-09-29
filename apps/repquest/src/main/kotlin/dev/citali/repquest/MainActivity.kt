// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.repquest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.citali.repquest.ui.GameViewModel
import dev.citali.repquest.ui.collection.CollectionScreen
import dev.citali.repquest.ui.home.HomeScreen
import dev.citali.repquest.ui.nav.CapsuleNavBar
import dev.citali.repquest.ui.nav.Tab
import dev.citali.repquest.ui.pull.PullScreen
import dev.citali.repquest.ui.theme.RepQuestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RepQuestTheme {
                val vm: GameViewModel = viewModel()
                var tab by remember { mutableStateOf(Tab.HOME) }
                val notice by vm.notice.collectAsState()

                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding(),
                ) {
                    AnimatedVisibility(visible = notice != null) {
                        NoticeBar(notice ?: "")
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(
                            targetState = tab,
                            label = "tab",
                            transitionSpec = {
                                (fadeIn() + scaleIn(initialScale = 0.97f)) togetherWith
                                    (fadeOut() + scaleOut(targetScale = 0.97f))
                            },
                        ) { current ->
                            when (current) {
                                Tab.HOME -> HomeScreen(vm)
                                Tab.PULL -> PullScreen(vm)
                                Tab.COLLECTION -> CollectionScreen(vm)
                            }
                        }
                    }
                    CapsuleNavBar(
                        selected = tab,
                        onSelect = { tab = it },
                        modifier =
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .navigationBarsPadding()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeBar(text: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
