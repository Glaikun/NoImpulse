package com.glaikun.noimpulse.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.glaikun.noimpulse.model.AppEntry
import kotlinx.coroutines.delay
import java.util.UUID

// ── Reflection questions ─────────────────────────────────────────────────────

/** A reflection prompt with the [answer] the user must select to proceed. */
internal data class ReflectionQuestion(val text: String, val answer: Boolean?)

/**
 * Starter bank of reflection prompts with designated (mixed) correct answers. This is
 * intentional friction, not a knowledge test — answering "correctly" forces the user to
 * read and consciously affirm intent before making their phone more tempting. Edit freely.
 */
internal val reflectionBank: List<ReflectionQuestion> = listOf(
    ReflectionQuestion("Are you trying to reduce impulsive phone use?", true),
    ReflectionQuestion("Could this app pull you into mindless scrolling?", null),
    ReflectionQuestion("Is now a good time to make your phone more tempting?", null),
    ReflectionQuestion("Are you adding this on impulse right now?", false),
    ReflectionQuestion("Do you want to spend less time on your phone overall?", true),
    ReflectionQuestion("Is this choice aligned with your real intentions?", true),
    ReflectionQuestion("Would a less distracting alternative work instead?", null),
    ReflectionQuestion("Have you paused to think this through?", true),
)

/** Picks [count] random questions from the [reflectionBank]. */
internal fun pickReflectionQuestions(count: Int): List<ReflectionQuestion> =
    reflectionBank.shuffled().take(count)

/** True when every question has been answered with its designated [ReflectionQuestion.answer]. */
internal fun reflectionsAllCorrect(
    questions: List<ReflectionQuestion>,
    answers: Map<Int, Boolean>,
): Boolean = questions.indices.all {
    questions[it].answer == null || answers[it] == questions[it].answer
}

// ── Math problems ────────────────────────────────────────────────────────────

internal data class MathProblem(val a: Int, val b: Int) {
    val answer: Int get() = a + b
    val text: String get() = "$a + $b = ?"
}

internal fun generateMathProblem(): MathProblem = MathProblem((2..19).random(), (2..19).random())

// ── Reusable yes/no challenge ────────────────────────────────────────────────

@Composable
private fun YesNoButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

/**
 * Renders [questions] each with Yes/No buttons and reports via [onAllCorrectChange] whether
 * every question currently has its designated answer selected.
 */
@Composable
internal fun ReflectionChallenge(
    questions: List<ReflectionQuestion>,
    onAllCorrectChange: (Boolean) -> Unit,
) {
    val answers = remember(questions) { mutableStateMapOf<Int, Boolean>() }

    fun update(index: Int, value: Boolean) {
        answers[index] = value
        onAllCorrectChange(reflectionsAllCorrect(questions, answers))
    }

    Column {
        questions.forEachIndexed { i, q ->
            Text(text = q.text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                YesNoButton("Yes", selected = answers[i] == true) { update(i, true) }
                YesNoButton("No", selected = answers[i] == false) { update(i, false) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ── Allowlist-add gate ───────────────────────────────────────────────────────

/**
 * Two-step gate for adding an app to the allowlist: type a full UUID, then answer 3
 * reflection questions correctly. Deliberate friction — allowlisting removes an app's
 * launch friction, so it should require real intent.
 */
@Composable
internal fun AddToAllowlistDialog(
    app: AppEntry,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit,
) {
    var onReflectionStep by remember(app.packageName) { mutableStateOf(false) }
    val uuid = remember(app.packageName) { UUID.randomUUID().toString() }
    var typed by remember(app.packageName) { mutableStateOf("") }
    val uuidMatches = typed.trim().equals(uuid, ignoreCase = true)
    val questions = remember(app.packageName) { pickReflectionQuestions(3) }
    var reflectionsCorrect by remember(app.packageName) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${app.label}?") },
        text = {
            if (!onReflectionStep) {
                Column {
                    Text(
                        text = "Allowlisting removes this app's launch friction. " +
                            "Type the code below exactly to continue.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = uuid,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("allowlistUuidInput"),
                    )
                }
            } else {
                Column {
                    Text(
                        text = "Answer these to confirm your intent.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    ReflectionChallenge(questions) { reflectionsCorrect = it }
                }
            }
        },
        confirmButton = {
            if (!onReflectionStep) {
                TextButton(
                    onClick = { onReflectionStep = true },
                    enabled = uuidMatches,
                    modifier = Modifier.testTag("allowlistNext"),
                ) { Text("Next") }
            } else {
                TextButton(
                    onClick = onConfirmed,
                    enabled = reflectionsCorrect,
                    modifier = Modifier.testTag("allowlistConfirm"),
                ) { Text("Add") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * A bare full-UUID typing gate. Used to *remove* friction — making your phone more
 * permissive should take deliberate effort, same as adding an app to the allowlist.
 */
@Composable
internal fun UuidChallengeDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit,
) {
    val uuid = remember { UUID.randomUUID().toString() }
    var typed by remember { mutableStateOf("") }
    val matches = typed.trim().equals(uuid, ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = uuid,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("uuidInput"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmed,
                enabled = matches,
                modifier = Modifier.testTag("uuidConfirm"),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Opening-friction launch dialogs ──────────────────────────────────────────

@Composable
internal fun TimedWaitDialog(
    app: AppEntry,
    seconds: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var remaining by remember(app.packageName) { mutableStateOf(seconds) }
    LaunchedEffect(app.packageName) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Text(
                text = if (remaining > 0) "Wait ${remaining}s before opening…" else "You can open it now.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = remaining <= 0,
                modifier = Modifier.testTag("challengeLaunch"),
            ) { Text("Launch") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun MathChallengeDialog(
    app: AppEntry,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val problem = remember(app.packageName) { generateMathProblem() }
    var typed by remember(app.packageName) { mutableStateOf("") }
    val correct = typed.trim().toIntOrNull() == problem.answer

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column {
                Text(
                    text = "Solve to open: ${problem.text}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("challengeInput"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = correct,
                modifier = Modifier.testTag("challengeLaunch"),
            ) { Text("Launch") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun ReflectionLaunchDialog(
    app: AppEntry,
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val questions = remember(app.packageName) { pickReflectionQuestions(count.coerceAtLeast(1)) }
    var allCorrect by remember(app.packageName) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column {
                Text(
                    text = "Answer these before opening.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                ReflectionChallenge(questions) { allCorrect = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = allCorrect,
                modifier = Modifier.testTag("challengeLaunch"),
            ) { Text("Launch") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
