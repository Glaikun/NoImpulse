package com.glaikun.noimpulse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.glaikun.noimpulse.api.AppEntry
import com.glaikun.noimpulse.ui.theme.NoImpulseTheme
import java.util.UUID

@Composable
fun AppDrawerScreen(
    installedApps: List<AppEntry>,
    allowedPackages: Set<String>,
    drawerLaunchesToday: Int,
    onLaunchApp: (String) -> Unit = {},
    onLaunchAfterChallenge: (String) -> Unit = {},
    loadIcon: (String) -> android.graphics.drawable.Drawable? = { null },
) {
    val tokenCount = remember(drawerLaunchesToday) { tokensRequired(drawerLaunchesToday) }
    var filterText by rememberSaveable { mutableStateOf("") }
    val filteredApps = remember(filterText, installedApps) {
        if (filterText.isBlank()) installedApps
        else installedApps.filter { it.label.contains(filterText, ignoreCase = true) }
    }
    // Plain remember (not rememberSaveable) — Saver requires a non-null type. If the
    // user rotates mid-dialog the dialog closes; acceptable for a launcher that rarely
    // rotates, and saves a wrapper class.
    var pendingApp by remember { mutableStateOf<AppEntry?>(null) }

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
                text = "All apps",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 16.dp),
            )

            TextField(
                value = filterText,
                onValueChange = { filterText = it },
                placeholder = { Text("Filter") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    DrawerAppItem(app = app, loadIcon = loadIcon, onClick = {
                        if (app.packageName in allowedPackages) {
                            onLaunchApp(app.packageName)
                        } else {
                            pendingApp = app
                        }
                    })
                }
            }
        }
    }

    pendingApp?.let { app ->
        LaunchChallengeDialog(
            app = app,
            tokenCount = tokenCount,
            drawerLaunchesToday = drawerLaunchesToday,
            onDismiss = { pendingApp = null },
            onConfirm = {
                pendingApp = null
                onLaunchAfterChallenge(app.packageName)
            },
        )
    }
}

@Composable
private fun DrawerAppItem(
    app: AppEntry,
    loadIcon: (String) -> android.graphics.drawable.Drawable?,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .testTag("drawerApp_${app.packageName}")
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

@Composable
private fun LaunchChallengeDialog(
    app: AppEntry,
    tokenCount: Int,
    drawerLaunchesToday: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val tokens = rememberSaveable(
        app.packageName, tokenCount,
        saver = listSaver<List<String>, String>(save = { it.toList() }, restore = { it }),
    ) { generateTokens(tokenCount) }
    val expected = remember(tokens) { tokens.joinToString(" ") }
    var typed by rememberSaveable(app.packageName) { mutableStateOf("") }
    val matches = typed.trim().equals(expected, ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column {
                Text(
                    text = "Drawer launches today: $drawerLaunchesToday",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("drawerLaunchCount"),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Type the tokens below to launch — each separated by a single space.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                tokens.forEach { token ->
                    Text(
                        text = token,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("challengeInput"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = matches,
                modifier = Modifier.testTag("challengeLaunch"),
            ) { Text("Launch") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Generates [count] short hex tokens for the launch challenge. Not cryptographic — this
 * is typing friction. 6 chars is short enough to type without frustration but long
 * enough to feel deliberate.
 */
internal fun generateTokens(count: Int): List<String> = List(count) {
    UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
}

@Preview(showBackground = true)
@Composable
private fun AppDrawerScreenPreview() {
    NoImpulseTheme {
        AppDrawerScreen(
            installedApps = listOf(
                AppEntry("Calculator", "com.android.calculator2"),
                AppEntry("Clock", "com.android.deskclock"),
                AppEntry("Maps", "com.google.android.apps.maps"),
                AppEntry("Phone", "com.android.dialer"),
                AppEntry("Twitter", "com.twitter.android"),
            ),
            allowedPackages = setOf("com.android.dialer", "com.android.deskclock"),
            drawerLaunchesToday = 3,
        )
    }
}
