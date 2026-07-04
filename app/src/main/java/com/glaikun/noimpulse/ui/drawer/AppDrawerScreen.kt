package com.glaikun.noimpulse.ui.drawer

import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.glaikun.noimpulse.model.AppEntry
import com.glaikun.noimpulse.model.FrictionRule
import com.glaikun.noimpulse.model.FrictionType
import com.glaikun.noimpulse.ui.AddToAllowlistDialog
import com.glaikun.noimpulse.ui.AppIcon
import com.glaikun.noimpulse.ui.FrictionGate
import com.glaikun.noimpulse.ui.UuidChallengeDialog
import com.glaikun.noimpulse.ui.tokensRequired
import com.glaikun.noimpulse.ui.theme.NoImpulseTheme

@Composable
fun AppDrawerScreen(
    installedApps: List<AppEntry>,
    allowedPackages: Set<String>,
    lockedPackages: Set<String> = emptySet(),
    drawerLaunchesToday: Int,
    isRestrictedNow: Boolean = false,
    onLaunchApp: (String) -> Unit = {},
    onLaunchAfterChallenge: (String) -> Unit = {},
    onRestrictedTap: () -> Unit = {},
    loadIcon: (String) -> Drawable? = { null },
    appFriction: Map<String, List<FrictionRule>> = emptyMap(),
    onSetAppAllowed: (String, Boolean) -> Unit = { _, _ -> },
    onAddAppFriction: (String, FrictionRule) -> Unit = { _, _ -> },
    onRemoveAppFriction: (String, FrictionRule) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
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
    var sheetApp by remember { mutableStateOf<AppEntry?>(null) }
    var addGateApp by remember { mutableStateOf<AppEntry?>(null) }
    // The friction change awaiting confirmation — see PendingFriction for the two kinds.
    var pendingFriction by remember { mutableStateOf<PendingFriction?>(null) }

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
            Row (
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
            ) {
                Text(
                    text = "All Apps",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("openSettings"),
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "settings")
                }
            }


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
                    DrawerAppItem(
                        app = app,
                        loadIcon = loadIcon,
                        onClick = {
                            when {
                                // Allowlisted apps launch directly — even during restricted time.
                                app.packageName in allowedPackages -> onLaunchApp(app.packageName)
                                // Restricted time blocks the rest — show the notice instead of
                                // opening the friction gate.
                                isRestrictedNow -> onRestrictedTap()
                                else -> pendingApp = app
                            }
                        },
                        onLongClick = { sheetApp = app },
                    )
                }
            }
        }
    }

    pendingApp?.let { app ->
        // Friction applies only to non-allowlisted apps (allowlisted ones launch directly,
        // above). FrictionGate iterates assigned rules in sequence, or falls back to the
        // default daily-scaling token challenge when no rules are assigned.
        FrictionGate(
            app = app,
            rules = appFriction[app.packageName].orEmpty(),
            drawerLaunchesToday = drawerLaunchesToday,
            tokenCount = tokenCount,
            onCancel = { pendingApp = null },
            onComplete = {
                pendingApp = null
                onLaunchAfterChallenge(app.packageName)
            },
        )
    }

    sheetApp?.let { app ->
        AppOptionsSheet(
            app = app,
            isAllowed = app.packageName in allowedPackages,
            isLocked = app.packageName in lockedPackages,
            currentFrictions = appFriction[app.packageName].orEmpty(),
            onDismiss = { sheetApp = null },
            onAddToAllowlist = {
                sheetApp = null
                addGateApp = app
            },
            onRemoveFromAllowlist = {
                onSetAppAllowed(app.packageName, false)
                sheetApp = null
            },
            onRequestChange = { pendingFriction = it },
        )
    }

    addGateApp?.let { app ->
        AddToAllowlistDialog(
            app = app,
            onDismiss = { addGateApp = null },
            onConfirmed = {
                onSetAppAllowed(app.packageName, true)
                addGateApp = null
            },
        )
    }

    pendingFriction?.let { pending ->
        val confirm = {
            pending.remove?.let { onRemoveAppFriction(pending.app.packageName, it) }
            pending.add?.let { onAddAppFriction(pending.app.packageName, it) }
            pendingFriction = null
        }
        when (pending) {
            is PendingFriction.Strengthen -> AlertDialog(
                onDismissRequest = { pendingFriction = null },
                title = { Text("Apply friction?") },
                text = { Text("Make ${pending.app.label} harder to open with this friction?") },
                confirmButton = {
                    TextButton(
                        onClick = confirm,
                        modifier = Modifier.testTag("confirmAddFriction"),
                    ) { Text("Apply") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingFriction = null }) { Text("Cancel") }
                },
            )
            is PendingFriction.Weaken -> key(pending) {
                UuidChallengeDialog(
                    title = if (pending.add != null) "Shorten timed wait?" else "Remove friction?",
                    message = "This makes ${pending.app.label} easier to open. " +
                        "Type the code below exactly to confirm.",
                    confirmLabel = if (pending.add != null) "Shorten" else "Remove",
                    onDismiss = { pendingFriction = null },
                    onConfirmed = confirm,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerAppItem(
    app: AppEntry,
    loadIcon: (String) -> Drawable?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .testTag("drawerApp_${app.packageName}")
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppOptionsSheet(
    app: AppEntry,
    isAllowed: Boolean,
    isLocked: Boolean,
    currentFrictions: List<FrictionRule>,
    onDismiss: () -> Unit,
    onAddToAllowlist: () -> Unit,
    onRemoveFromAllowlist: () -> Unit,
    onRequestChange: (PendingFriction) -> Unit,
) {
    // Timed wait is a single grouped choice (at most one timer per app); the rest stack freely.
    val currentTimer = currentFrictions.firstOrNull { it.type == FrictionType.TIMED_WAIT }
    val stackableOptions = listOf(
        "Math problem" to FrictionRule(FrictionType.MATH, 1),
        "Reflection questions" to FrictionRule(FrictionType.REFLECTION, 3),
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(text = app.label, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))

            // The always-allowed core (phone/settings/messages/camera/maps) has no controls —
            // it can't be removed from the allowlist or have friction added.
            if (isLocked) {
                Text(
                    text = "Always available. This core app can't be removed from the " +
                        "allowlist or have friction added.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("lockedAppNote"),
                )
                return@Column
            }

            if (isAllowed) {
                TextButton(
                    onClick = onRemoveFromAllowlist,
                    modifier = Modifier.testTag("removeFromAllowlist"),
                ) { Text("Remove from allowlist") }
            } else {
                TextButton(
                    onClick = onAddToAllowlist,
                    modifier = Modifier.testTag("addToAllowlist"),
                ) { Text("Add to allowlist") }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(text = "Add extra friction", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "A token challenge already applies whenever this app isn't " +
                    "allowlisted. Add more below to stack on top. Removing one — or " +
                    "shortening the timed wait — requires a code.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            TimedWaitSection(
                app = app,
                currentTimer = currentTimer,
                onRequestChange = onRequestChange,
            )

            Spacer(Modifier.height(8.dp))

            // ── Stackable frictions ──
            stackableOptions.forEach { (label, rule) ->
                val assigned = rule in currentFrictions
                FrictionToggle(
                    label = label,
                    assigned = assigned,
                    onToggle = {
                        onRequestChange(
                            if (assigned) PendingFriction.Weaken(app, remove = rule)
                            else PendingFriction.Strengthen(app, remove = null, add = rule),
                        )
                    },
                )
            }
        }
    }
}

/**
 * The "Timed wait" radio group. At most one timer per app, and the section decides
 * what each pick means: turning it Off or picking a shorter duration weakens
 * friction (UUID gate); the first timer or a longer one strengthens it.
 */
@Composable
private fun TimedWaitSection(
    app: AppEntry,
    currentTimer: FrictionRule?,
    onRequestChange: (PendingFriction) -> Unit,
) {
    val timerDurations = listOf(10, 30, 60)
    Text(text = "Timed wait", style = MaterialTheme.typography.titleSmall)
    FrictionRadioRow(label = "Off", selected = currentTimer == null, testTag = "timedWaitOff") {
        currentTimer?.let { onRequestChange(PendingFriction.Weaken(app, remove = it)) }
    }
    timerDurations.forEach { seconds ->
        val rule = FrictionRule(FrictionType.TIMED_WAIT, seconds)
        FrictionRadioRow(label = "${seconds}s", selected = currentTimer == rule, testTag = "timedWait_$seconds") {
            when {
                currentTimer == rule -> {} // already selected — nothing to change
                currentTimer != null && seconds < currentTimer.param ->
                    onRequestChange(PendingFriction.Weaken(app, remove = currentTimer, add = rule))
                else -> onRequestChange(PendingFriction.Strengthen(app, remove = currentTimer, add = rule))
            }
        }
    }
}

@Composable
private fun FrictionRadioRow(label: String, selected: Boolean, testTag: String, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FrictionToggle(label: String, assigned: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
    ) {
        Checkbox(checked = assigned, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
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
