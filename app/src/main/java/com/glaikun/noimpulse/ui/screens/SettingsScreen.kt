package com.glaikun.noimpulse.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.glaikun.noimpulse.model.TimeWindow
import com.glaikun.noimpulse.ui.AccessibilityPermissionStep
import com.glaikun.noimpulse.ui.AccessibilityPrePromptDialog
import com.glaikun.noimpulse.ui.HomeViewModel
import com.glaikun.noimpulse.ui.PermissionStep
import com.glaikun.noimpulse.ui.UuidChallengeDialog
import com.glaikun.noimpulse.ui.theme.NoImpulseTheme

@Composable
fun SettingsScreen(
    state: HomeViewModel.UiState,
    onGrantUsageAccess: () -> Unit = {},
    onSetDefaultHome: () -> Unit = {},
    onGrantAccessibility: () -> Unit = {},
    onSwitchLauncher: () -> Unit = {},
    onSetRestrictedModeEnabled: (Boolean) -> Unit = {},
    onAddAllowedWindow: (TimeWindow) -> Unit = {},
    onRemoveAllowedWindow: (TimeWindow) -> Unit = {},
) {
    var showAccessibilityPrompt by remember { mutableStateOf(false) }
    var showSwitchLauncherGate by remember { mutableStateOf(false) }
    var showAddWindow by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 24.dp),
            )

            // ── Permissions ──
            Text(text = "Permissions", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            PermissionStep(
                title = "Usage access",
                subtitle = "Track screen time and unlocks",
                granted = state.usageAccessGranted,
                actionLabel = "Grant",
                onAction = onGrantUsageAccess,
            )
            Spacer(Modifier.height(16.dp))
            PermissionStep(
                title = "Default home",
                subtitle = "Make NoImpulse your launcher",
                granted = state.isDefaultHome,
                actionLabel = "Set",
                onAction = onSetDefaultHome,
            )
            Spacer(Modifier.height(16.dp))
            AccessibilityPermissionStep(
                granted = state.accessibilityGranted,
                onTapGrant = { showAccessibilityPrompt = true },
                onTapWhy = { showAccessibilityPrompt = true },
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // ── Switch launcher ──
            Text(text = "Switch launcher", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Re-prompt the system to make NoImpulse your default home app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showSwitchLauncherGate = true },
                modifier = Modifier.testTag("switchLauncher"),
            ) { Text("Switch launcher") }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // ── Restricted Mode ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Restricted Mode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Outside your allowed times, apps can't be used at all.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.restrictedModeEnabled,
                    onCheckedChange = onSetRestrictedModeEnabled,
                    modifier = Modifier.testTag("restrictedModeSwitch"),
                )
            }

            if (state.restrictedModeEnabled) {
                Spacer(Modifier.height(16.dp))
                Text(text = "Allowed times", style = MaterialTheme.typography.titleSmall)
                if (state.allowedWindows.isEmpty()) {
                    Text(
                        text = "No allowed times yet — every hour is restricted until you add one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.allowedWindows.forEach { window ->
                    AllowedWindowRow(
                        window = window,
                        onRemove = { onRemoveAllowedWindow(window) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showAddWindow = true },
                    modifier = Modifier.testTag("addAllowedWindow"),
                ) { Text("Add allowed time") }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAccessibilityPrompt) {
        AccessibilityPrePromptDialog(
            onDismiss = { showAccessibilityPrompt = false },
            onConfirm = {
                showAccessibilityPrompt = false
                onGrantAccessibility()
            },
        )
    }

    if (showSwitchLauncherGate) {
        UuidChallengeDialog(
            title = "Switch launcher?",
            message = "This re-opens the system home-app prompt. Type the code below exactly to confirm.",
            confirmLabel = "Continue",
            onDismiss = { showSwitchLauncherGate = false },
            onConfirmed = {
                showSwitchLauncherGate = false
                onSwitchLauncher()
            },
        )
    }

    if (showAddWindow) {
        AddWindowDialog(
            onDismiss = { showAddWindow = false },
            onAdd = { window ->
                showAddWindow = false
                onAddAllowedWindow(window)
            },
        )
    }
}

@Composable
private fun AllowedWindowRow(window: TimeWindow, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("allowedWindow_${window.startMinute}_${window.endMinute}")
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "${formatMinute(window.startMinute)} – ${formatMinute(window.endMinute)}",
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWindowDialog(
    onDismiss: () -> Unit,
    onAdd: (TimeWindow) -> Unit,
) {
    // Sensible default: a 9–5 window the user can adjust before adding.
    val startState = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)
    val endState = rememberTimePickerState(initialHour = 17, initialMinute = 0, is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add allowed time") },
        text = {
            Column {
                Text(text = "Start", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                TimeInput(state = startState)
                Spacer(Modifier.height(16.dp))
                Text(text = "End", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                TimeInput(state = endState)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(
                        TimeWindow(
                            startMinute = startState.hour * 60 + startState.minute,
                            endMinute = endState.hour * 60 + endState.minute,
                        ),
                    )
                },
                modifier = Modifier.testTag("confirmAddWindow"),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Formats minutes-since-midnight as "HH:mm". */
private fun formatMinute(minuteOfDay: Int): String =
    "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    NoImpulseTheme {
        SettingsScreen(
            state = HomeViewModel.UiState(
                usageAccessGranted = true,
                isDefaultHome = false,
                accessibilityGranted = true,
                restrictedModeEnabled = true,
                allowedWindows = listOf(TimeWindow(9 * 60, 17 * 60)),
            ),
        )
    }
}
