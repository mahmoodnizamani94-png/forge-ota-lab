package dev.forgeotalab

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import dev.forgeotalab.contracts.model.ThemeMode
import dev.forgeotalab.data.ForgePreferences
import dev.forgeotalab.ui.navigation.ForgeNavGraph
import dev.forgeotalab.ui.navigation.HomeRoute
import dev.forgeotalab.ui.navigation.OnboardingRoute
import dev.forgeotalab.ui.theme.ForgeTheme
import javax.inject.Inject

/**
 * Main activity — single-activity architecture with Compose Navigation.
 *
 * Handles three import entry points per PRD:
 * 1. SAF file picker (initiated from HomeScreen FAB)
 * 2. Share intent (ACTION_SEND from file managers)
 * 3. Open-with handler (ACTION_VIEW from file managers/browsers)
 *
 * Theme switching: Reads ThemeMode from DataStore via ForgePreferences.
 * The theme updates instantly via Compose recomposition — no activity
 * recreation needed.
 *
 * Onboarding gate: Reads onboardingCompleted from DataStore. If false,
 * the nav graph starts at OnboardingRoute. After completion, navigates
 * to HomeRoute and onboarding is never re-shown.
 *
 * Accessibility: Screen title announced as "Forge OTA Lab" on launch.
 * Focus order: top bar → content area → floating action button.
 * Heading semantics: screen title uses Role.Heading.
 *
 * @AndroidEntryPoint enables Hilt injection into this activity
 * and any Composables that use hiltViewModel().
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var forgePreferences: ForgePreferences

    /** URI pending analysis from share intent or open-with handler. */
    private var pendingImportUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle incoming intent (share or open-with)
        handleIncomingIntent(intent)

        setContent {
            // Observe theme mode reactively — instant switching, no restart
            val themeMode by forgePreferences.themeMode
                .collectAsState(initial = ThemeMode.DARK)
            val isSystemDark = isSystemInDarkTheme()
            val useDarkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemDark
            }

            // Observe onboarding completion — determines start destination
            val onboardingCompleted by forgePreferences.onboardingCompleted
                .collectAsState(initial = true) // Default true to avoid flash

            val startDestination: Any = if (onboardingCompleted) HomeRoute else OnboardingRoute

            ForgeTheme(useDarkTheme = useDarkTheme) {
                ForgeNavGraph(
                    startDestination = startDestination,
                    pendingImportUri = pendingImportUri,
                    onPendingImportConsumed = { pendingImportUri = null },
                )
            }
        }
    }

    /**
     * Handle new intents when the activity is already running.
     *
     * WHY override: When the app is already open and receives a new
     * share/open-with intent, onNewIntent is called instead of onCreate.
     * Without handling this, the second file share would be lost.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Extract a content URI from ACTION_SEND or ACTION_VIEW intents.
     *
     * WHY takePersistableUriPermission here: The activity is the earliest
     * point where we have an intent with URI permissions. Taking persistable
     * permission here ensures the URI remains accessible after the activity
     * is recreated (process death, config change).
     */
    private fun handleIncomingIntent(intent: Intent?) {
        intent ?: return

        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> {
                // Share intent — URI in EXTRA_STREAM
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            Intent.ACTION_VIEW -> {
                // Open-with — URI is the intent data
                intent.data
            }
            else -> null
        }

        uri?.let { incomingUri ->
            // WHY try/catch: Not all content providers support persistable permissions.
            // Share intents from some apps only grant one-time access.
            try {
                contentResolver.takePersistableUriPermission(
                    incomingUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // One-time access — analysis can still proceed
            }

            pendingImportUri = incomingUri
        }
    }
}

