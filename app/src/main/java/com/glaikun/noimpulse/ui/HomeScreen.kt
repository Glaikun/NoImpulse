package com.glaikun.noimpulse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.glaikun.noimpulse.api.AppEntry
import com.glaikun.noimpulse.ui.theme.NoImpulseTheme

@Composable
fun HomeScreen(
    state: HomeViewModel.UiState,
    onGrantUsageAccess: () -> Unit = {},
    onLaunchApp: (String) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    loadIcon: (String) -> android.graphics.drawable.Drawable? = { null },
) {
    val openThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    var dragAccum by remember { mutableStateOf(0f) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            // Detects swipe-up on the home surface. The OS reserves the very bottom
            // gesture-inset band for Recents/Home when we hold the home role, so users
            // whose swipe starts inside that band won't trigger this — the chevron
            // affordance below covers them.
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onDragCancel = { dragAccum = 0f },
                    onDragEnd = { dragAccum = 0f },
                    onVerticalDrag = { _, delta ->
                        dragAccum += delta
                        if (dragAccum < -openThresholdPx) {
                            dragAccum = 0f
                            onOpenDrawer()
                        }
                    },
                )
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // ── Time & date ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = state.time,
                    style = MaterialTheme.typography.displayLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.date,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    style = MaterialTheme.typography.titleMedium,
                    text = if (state.batteryPercent >= 0) "${state.batteryPercent}%" else "--",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Stats row (or usage-access prompt) ───────────────
            if (state.usageAccessGranted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatChip(
                        label = "Today's Pickups",
                        value = state.pickupCount?.toString() ?: "--",
                    )
                    StatChip(
                        label = "Today's Screen Time",
                        value = state.screenOnMinutes?.let { formatHours(it) } ?: "--",
                    )
                }
            } else {
                UsageAccessPrompt(onGrantUsageAccess)
            }

            // ── Chevron affordance + app grid ────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Open app drawer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("openDrawer")
                        .clickable(onClick = onOpenDrawer),
                )
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.homeApps) { app ->
                        AppIconItem(app, loadIcon, onClick = { onLaunchApp(app.packageName) })
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageAccessPrompt(onGrantUsageAccess: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Grant usage access to track screen time",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onGrantUsageAccess) {
            Text("Grant access")
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppIconItem(
    app: AppEntry,
    loadIcon: (String) -> android.graphics.drawable.Drawable?,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .testTag("appIcon_${app.packageName}")
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        AppIcon(app = app, loadIcon = loadIcon)
        Spacer(Modifier.height(4.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Preview(name = "Granted", showBackground = true)
@Composable
private fun HomeScreenPreview() {
    NoImpulseTheme {
        HomeScreen(
            state = HomeViewModel.UiState(
                time = "14:35",
                date = "Saturday, 21 June",
                batteryPercent = 82,
                usageAccessGranted = true,
                pickupCount = 14,
                screenOnMinutes = 137,
                homeApps = listOf(
                    AppEntry("Phone", "com.android.dialer"),
                    AppEntry("Messages", "com.android.messaging"),
                    AppEntry("Camera", "com.android.camera2"),
                    AppEntry("Maps", "com.google.android.apps.maps"),
                ),
            )
        )
    }
}

@Preview(name = "Usage access not granted", showBackground = true)
@Composable
private fun HomeScreenNoAccessPreview() {
    NoImpulseTheme {
        HomeScreen(
            state = HomeViewModel.UiState(
                time = "14:35",
                date = "Saturday, 21 June",
                batteryPercent = 82,
                usageAccessGranted = false,
            )
        )
    }
}
