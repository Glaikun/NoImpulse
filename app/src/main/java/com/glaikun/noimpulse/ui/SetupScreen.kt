package com.glaikun.noimpulse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.glaikun.noimpulse.api.AppEntry
import com.glaikun.noimpulse.ui.theme.NoImpulseTheme

@Composable
fun SetupScreen(
    state: HomeViewModel.UiState,
    installedApps: List<AppEntry>,
    onGrantUsageAccess: () -> Unit = {},
    onSetDefaultHome: () -> Unit = {},
    onToggleApp: (String, Boolean) -> Unit = { _, _ -> },
    onFinish: () -> Unit = {},
) {
    val allowed = remember(state.allowedApps) { state.allowedApps.mapTo(HashSet()) { it.packageName } }
    var filterText by rememberSaveable { mutableStateOf("") }
    val filteredApps = remember(filterText, installedApps) {
        if (filterText.isBlank()) installedApps
        else installedApps.filter { it.label.contains(filterText, ignoreCase = true) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = "Set Up",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 24.dp),
            )

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

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Allowed apps",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Choose which apps you can reach from the home screen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            TextField(
                value = filterText,
                onValueChange = { filterText = it },
                placeholder = { Text("Enter filter") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppToggleRow(
                        app = app,
                        checked = app.packageName in allowed,
                        onCheckedChange = { onToggleApp(app.packageName, it) },
                    )
                    HorizontalDivider()
                }
            }

            Button(
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                Text("Finish")
            }
        }
    }
}

@Composable
private fun PermissionStep(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (granted) {
            Text(
                text = "✓ Granted",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun AppToggleRow(
    app: AppEntry,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("appToggle_${app.packageName}")
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupScreenPreview() {
    NoImpulseTheme {
        SetupScreen(
            state = HomeViewModel.UiState(
                setupComplete = false,
                usageAccessGranted = true,
                isDefaultHome = false,
                allowedApps = listOf(AppEntry("Maps", "com.google.android.apps.maps")),
            ),
            installedApps = listOf(
                AppEntry("Calculator", "com.android.calculator2"),
                AppEntry("Clock", "com.android.deskclock"),
                AppEntry("Maps", "com.google.android.apps.maps"),
                AppEntry("Phone", "com.android.dialer"),
                AppEntry("Settings", "com.android.settings"),
            ),
        )
    }
}
