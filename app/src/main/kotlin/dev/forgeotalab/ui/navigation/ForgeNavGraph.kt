package dev.forgeotalab.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.forgeotalab.ui.screens.analysis.AnalysisScreen
import dev.forgeotalab.ui.screens.extraction.ExtractionScreen
import dev.forgeotalab.ui.screens.home.HomeScreen
import dev.forgeotalab.ui.screens.incremental.IncrementalWizardScreen
import dev.forgeotalab.ui.screens.browser.FilesystemBrowserScreen
import dev.forgeotalab.ui.screens.onboarding.OnboardingScreen
import dev.forgeotalab.ui.screens.result.ResultScreen
import dev.forgeotalab.ui.screens.settings.SettingsScreen

/**
 * Forge OTA Lab navigation graph — single NavHost for the entire app.
 *
 * Accessibility: Each destination announces its screen title via TalkBack
 * on navigation transitions. Focus order is reset on destination change.
 *
 * WHY all destinations declared now: The PRD specifies 9 major surfaces.
 * Downstream features (Extraction, IncrementalWizard, FilesystemBrowser,
 * Settings) register as branded placeholder screens until their slices land.
 *
 * @param pendingImportUri URI from share intent / open-with handler. When
 *   non-null, triggers auto-navigation to analysis after import completes.
 * @param onPendingImportConsumed Callback to clear the pending URI after handling.
 */
@Composable
fun ForgeNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: Any = HomeRoute,
    pendingImportUri: Uri? = null,
    onPendingImportConsumed: () -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        // =================================================================
        // Onboarding — first launch only
        // =================================================================
        composable<OnboardingRoute> {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(HomeRoute) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<HomeRoute> {
            HomeScreen(
                pendingImportUri = pendingImportUri,
                onPendingImportConsumed = onPendingImportConsumed,
                onNavigateToAnalysis = { packageId ->
                    navController.navigate(AnalysisRoute(packageId = packageId))
                },
                onNavigateToResult = { jobId ->
                    navController.navigate(ResultRoute(jobId = jobId))
                },
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute)
                },
            )
        }

        composable<AnalysisRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AnalysisRoute>()
            AnalysisScreen(
                packageId = route.packageId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToExtraction = { pkgId ->
                    navController.navigate(ExtractionRoute(packageId = pkgId))
                },
                onNavigateToIncrementalWizard = { pkgId ->
                    navController.navigate(IncrementalWizardRoute(packageId = pkgId))
                },
            )
        }

        // =====================================================================
        // Stub destinations for downstream features — branded placeholders
        // =====================================================================

        composable<ExtractionRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ExtractionRoute>()
            ExtractionScreen(
                packageId = route.packageId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToResult = { jobId ->
                    navController.navigate(ResultRoute(jobId = jobId))
                },
            )
        }

        composable<ResultRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ResultRoute>()
            ResultScreen(
                jobId = route.jobId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToFilesystemBrowser = { artifactId ->
                    navController.navigate(FilesystemBrowserRoute(artifactId = artifactId))
                },
            )
        }

        composable<IncrementalWizardRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<IncrementalWizardRoute>()
            IncrementalWizardScreen(
                packageId = route.packageId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToExtraction = { pkgId ->
                    navController.navigate(ExtractionRoute(packageId = pkgId))
                },
            )
        }

        composable<FilesystemBrowserRoute> {
            FilesystemBrowserScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
