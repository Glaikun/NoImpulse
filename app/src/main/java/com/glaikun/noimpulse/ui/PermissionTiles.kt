package com.glaikun.noimpulse.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Permission-request tiles shared by the Setup and Settings screens. A tile shows the
 * permission's title/subtitle and either a "✓ Granted" badge or an action button.
 */
@Composable
internal fun PermissionStep(
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

/**
 * Accessibility tile + inline "Why?" link. Variant of [PermissionStep] because the
 * accessibility consent is sensitive enough that we want the affordance to read
 * the pre-prompt right there, not buried behind a single Grant button.
 */
@Composable
internal fun AccessibilityPermissionStep(
    granted: Boolean,
    onTapGrant: () -> Unit,
    onTapWhy: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PermissionStep(
            title = "App-switch detection",
            subtitle = "Re-trigger friction when a friction-locked app comes back to the foreground. " +
                "Optional — the rest of the launcher works without it.",
            granted = granted,
            actionLabel = "Grant",
            onAction = onTapGrant,
        )
        if (!granted) {
            TextButton(
                onClick = onTapWhy,
                modifier = Modifier.testTag("accessibilityWhy"),
            ) { Text("Why does this need a scary permission?") }
        }
    }
}

@Composable
internal fun AccessibilityPrePromptDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Why does NoImpulse need accessibility access?") },
        text = {
            Column {
                Text(
                    text = "Android only lets an app notice when a different app comes to " +
                        "the foreground if it asks via the Accessibility API. We use it for " +
                        "one thing: re-showing the friction screen when you return to an app " +
                        "you've added friction to.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "What this service can see",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "• The package name of the app currently in the foreground.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "What it cannot do",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "• Read screen contents — messages, passwords, anything you " +
                        "type. The API itself withholds that data " +
                        "(canRetrieveWindowContent is false).\n" +
                        "• Take screenshots or screen recordings.\n" +
                        "• Make any network calls. The app declares no INTERNET permission " +
                        "and is open source so anyone can verify.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "About the next screen",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Android shows a strong warning on the accessibility settings " +
                        "page. That warning is the system's default for any accessibility " +
                        "app; it describes what an accessibility app could do, not what " +
                        "this one actually does.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("accessibilityOpenSettings"),
            ) { Text("Open system settings") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("accessibilityMaybeLater"),
            ) { Text("Maybe later") }
        },
    )
}
