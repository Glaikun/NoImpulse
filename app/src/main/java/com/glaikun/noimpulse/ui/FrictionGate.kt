package com.glaikun.noimpulse.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.glaikun.noimpulse.model.AppEntry
import com.glaikun.noimpulse.model.FrictionRule
import com.glaikun.noimpulse.model.FrictionType
import java.util.UUID

/**
 * Renders the configured friction dialog(s) for [app] and reports the result.
 *
 * The default daily-scaling token challenge always runs first as an unconditional baseline
 * ([tokenCount] decides how many tokens must be typed; [drawerLaunchesToday] is shown as
 * context). Any assigned [rules] then stack on top of it and run in sequence — each step
 * gets fresh state via [key] so duplicate rule types (e.g. two MATH steps) don't share
 * inputs. With no rules assigned, only the baseline token challenge runs.
 *
 * The same Composable is reused by [AppDrawerScreen] (first launch from the drawer)
 * and by `MainActivity` (re-friction triggered by the accessibility service).
 */
@Composable
fun FrictionGate(
    app: AppEntry,
    rules: List<FrictionRule>,
    drawerLaunchesToday: Int,
    tokenCount: Int,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
) {
    // The default daily-scaling token challenge always runs first as a baseline; any
    // assigned rules simply stack after it. Building one sequence lets us run every step
    // through the same loop instead of special-casing the baseline.
    val sequence = remember(rules, tokenCount) { frictionSequence(rules, tokenCount) }
    var step by remember(app.packageName, sequence) { mutableStateOf(0) }
    val current = step.coerceIn(sequence.indices)
    val advance: () -> Unit = {
        if (current >= sequence.lastIndex) onComplete() else step = current + 1
    }
    val rule = sequence[current]
    key(current) {
        when (rule.type) {
            FrictionType.TOKENS ->
                LaunchChallengeDialog(app, rule.param.coerceAtLeast(1), drawerLaunchesToday, onCancel, advance)
            FrictionType.TIMED_WAIT -> TimedWaitDialog(app, rule.param, onCancel, advance)
            FrictionType.MATH -> MathChallengeDialog(app, onCancel, advance)
            FrictionType.REFLECTION -> ReflectionLaunchDialog(app, rule.param, onCancel, advance)
        }
    }
}

/**
 * The full ordered list of frictions to run: the baseline token challenge (sized by
 * [tokenCount]) first, then the app's assigned [rules] stacked on top.
 */
internal fun frictionSequence(rules: List<FrictionRule>, tokenCount: Int): List<FrictionRule> =
    listOf(FrictionRule(FrictionType.TOKENS, tokenCount)) + rules

// ── Type-N-tokens dialog ────────────────────────────────────────────────────

@Composable
internal fun LaunchChallengeDialog(
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
