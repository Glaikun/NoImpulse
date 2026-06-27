package com.glaikun.noimpulse.ui.screens

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.glaikun.noimpulse.ui.theme.NoImpulseTheme

/**
 * Pre-setup explainer. Shown once on a fresh install (gated by `introSeen` in
 * [com.glaikun.noimpulse.data.SettingsRepository]); never again unless the
 * user wipes data.
 *
 * The trust statement near the bottom (open source, no network) lands here on
 * purpose — by the time the user reaches the accessibility-consent step in setup,
 * they should already know NoImpulse can't and won't transmit anything.
 */
@Composable
fun IntroScreen(onContinue: () -> Unit = {}) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(32.dp))
            Text(
                text = "Before you start",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(24.dp))

            Section(
                title = "NoImpulse is a launcher.",
                body = "Once set, it replaces your normal home screen. Apps you didn't " +
                    "choose are still installed — they're just not on your home screen.",
            )
            Section(
                title = "Friction is the point.",
                body = "Opening apps you haven't allowlisted will be slow on purpose — " +
                    "typing, waiting, a quick math problem. Slow taps stop impulsive ones.",
            )
            Section(
                title = "You choose what's easy.",
                body = "Add apps to the allowlist to keep them one tap away. Everything " +
                    "else takes effort. No blocklists — only opt-ins.",
            )
            Section(
                title = "Open source, on-device only.",
                body = "Nothing about how you use your phone is uploaded anywhere — the " +
                    "app makes no network calls about you. The whole project is open " +
                    "source so anyone can verify that.",
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("introContinue"),
            ) { Text("I understand — set me up") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Text(text = title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
}

@Preview(showBackground = true)
@Composable
private fun IntroScreenPreview() {
    NoImpulseTheme { IntroScreen() }
}
