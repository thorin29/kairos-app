package com.kairos.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kairos.app.data.session.SessionState
import com.kairos.app.di.AppContainer
import com.kairos.app.ui.auth.AuthFlow
import com.kairos.app.ui.common.LoadingScreen
import com.kairos.app.ui.common.PlaceholderScreen
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.devices.DevicesScreen
import com.kairos.app.ui.home.HomeScreen
import com.kairos.app.ui.nav.KairosRail
import com.kairos.app.ui.nav.Route
import com.kairos.app.ui.nav.sectionFor
import com.kairos.app.ui.reauth.ReauthScreen
import com.kairos.app.ui.setup.SetupScreen
import com.kairos.app.ui.workout.WorkoutLogScreen
import kotlinx.coroutines.launch

/**
 * The single auth gate. It watches [SessionState] and shows exactly one of:
 * setup (no server), enroll (no token), reauth (password changed), or the
 * authenticated app. Losing the token flips state and swaps back automatically.
 */
@Composable
fun AppRoot(container: AppContainer) {
    val session = container.sessionRepository
    val state by session.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is SessionState.Loading -> LoadingScreen()
        is SessionState.NeedsSetup -> SetupScreen()
        is SessionState.NeedsEnroll -> AuthFlow()
        is SessionState.NeedsReauth -> ReauthScreen(person = s.person)
        is SessionState.Ready -> AuthenticatedApp(person = s.person)
    }
}

private val COLLAPSED_WIDTH = 76.dp
private val EXPANDED_WIDTH = 224.dp

/**
 * The authenticated area: the NavHost content with the Kairos rail as an overlay
 * that rolls out from the top-left (matching the web) when the logo is tapped.
 * Opens collapsed; expanding sticks for the session until collapsed or relaunch.
 */
@Composable
private fun AuthenticatedApp(person: com.kairos.app.data.remote.dto.PersonDto) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val container = rememberContainer()

    var open by remember { mutableStateOf(false) }
    val expanded by container.navExpanded.collectAsStateWithLifecycle()
    var selectedKey by remember { mutableStateOf("home") }

    val openProgress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "navOpen",
    )
    val railWidth by animateDpAsState(
        targetValue = if (expanded) EXPANDED_WIDTH else COLLAPSED_WIDTH,
        animationSpec = tween(durationMillis = 200),
        label = "railWidth",
    )

    fun go(route: Route, key: String) {
        selectedKey = key
        open = false
        navController.navigate(route) {
            popUpTo(Route.Home)
            launchSingleTop = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Route.Home) {
            composable<Route.Home> {
                HomeScreen(
                    person = person,
                    onOpenDrawer = { open = true },
                    onLogWorkout = { date -> navController.navigate(Route.WorkoutLog(date)) },
                )
            }
            composable<Route.Section> { entry ->
                val key = entry.toRoute<Route.Section>().key
                PlaceholderScreen(title = sectionFor(key).label, onOpenDrawer = { open = true })
            }
            composable<Route.Devices> {
                DevicesScreen(onBack = { navController.popBackStack() })
            }
            composable<Route.WorkoutLog> { entry ->
                WorkoutLogScreen(
                    date = entry.toRoute<Route.WorkoutLog>().date,
                    onDone = { navController.popBackStack() },
                )
            }
        }

        if (openProgress > 0.001f) {
            // Scrim — tap to close.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = openProgress }
                    .background(Color.Black.copy(alpha = 0.32f))
                    .pointerInput(Unit) {
                        detectTapGestures { open = false }
                    },
            )
            // The rail, scaling out from the top-left corner.
            KairosRail(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(railWidth)
                    .graphicsLayer {
                        scaleX = openProgress
                        scaleY = openProgress
                        alpha = openProgress
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
                expanded = expanded,
                person = person,
                selectedKey = selectedKey,
                activeLabel = sectionFor(selectedKey).label,
                onSection = { section ->
                    if (section.key == "home") go(Route.Home, "home")
                    else go(Route.Section(section.key), section.key)
                },
                onToggleExpanded = { container.navExpanded.value = !container.navExpanded.value },
                onLogoClick = { open = false },
                onDevices = {
                    open = false
                    navController.navigate(Route.Devices) { launchSingleTop = true }
                },
                onSignOut = {
                    open = false
                    scope.launch { container.sessionRepository.signOut() }
                },
            )
        }
    }
}
