package dev.forgeotalab.ui.screens.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.forgeotalab.ui.theme.ForgeTheme

/**
 * Warning dialog for enabling privileged mode (Shizuku/root).
 *
 * PRD: "Privileged mode toggle: default off, behind explicit warning dialog
 * ('Enabling this requires Shizuku/root. Use only if you understand the risks.')
 * — requires dialog confirmation, not dismissible by outside tap."
 *
 * Accessibility:
 *   - Dialog title announced as heading
 *   - Confirm button has descriptive label
 *   - Not dismissible by outside tap or back press — explicit user action required
 */
@Composable
fun PrivilegedModeDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ForgeTheme.colors

    AlertDialog(
        onDismissRequest = {
            // WHY empty: PRD requires "not dismissible by outside tap."
            // The user must explicitly tap Cancel or Confirm.
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colors.feedbackWarningText,
                ),
            ) {
                Text("I understand, enable")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colors.textSecondary,
                ),
            ) {
                Text("Cancel")
            }
        },
        title = {
            Text(
                text = "Enable Privileged Mode?",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.feedbackWarningText,
            )
        },
        text = {
            Text(
                text = "Enabling this requires Shizuku or root access. " +
                    "Use only if you understand the risks.\n\n" +
                    "Privileged mode allows direct local partition capture " +
                    "for incremental OTA base image acquisition. " +
                    "Core extraction features work without this.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
        },
        containerColor = colors.surfaceOverlay,
        titleContentColor = colors.feedbackWarningText,
        textContentColor = colors.textPrimary,
    )
}
