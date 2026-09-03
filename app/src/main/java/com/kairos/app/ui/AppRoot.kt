package com.kairos.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairos.app.data.session.SessionState
import com.kairos.app.di.AppContainer
import com.kairos.app.ui.common.LoadingScreen
import com.kairos.app.ui.auth.AuthFlow
import com.kairos.app.ui.home.HomeScreen
import com.kairos.app.ui.devices.DevicesScreen
import com.kairos.app.ui.workout.WorkoutLogScreen
import com.kairos.app.ui.reauth.ReauthScreen
import com.kairos.app.ui.nav.Route
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.common.PlaceholderScreen
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.APP_SECTIONS
import com.kairos.app.ui.nav.sectionFor
import kotlinx.coroutines.launch
import com.kairos.app.ui.setup.SetupScreen

/**
 * The single auth gate. It watches [SessionState] and shows exactly one of:
 * setup (no server), enroll (no token), or the authenticated app (Ready). There
 * is no "navigate to login" — losing the token flips the state and this swaps
 * back automatically, which closes the soft-navigation bypass class by design.
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

/** The authenticated area: a navigation drawer (opened by the top-left menu)
 *  over a NavHost. Home is real; other sections are placeholders until built. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedApp(person: com.kairos.app.data.remote.dto.PersonDto) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val container = rememberContainer()
    var selectedKey by remember { mutableStateOf("home") }

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    fun go(route: Route, key: String) {
        selectedKey = key
        scope.launch { drawerState.close() }
        navController.navigate(route) {
            popUpTo(Route.Home)
            launchSingleTop = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Kairos",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(24.dp),
                )
                APP_SECTIONS.forEach { section ->
                    NavigationDrawerItem(
                        label = { Text(section.label) },
                        selected = section.key == selectedKey,
                        icon = { ColorDot(section.color) },
                        onClick = {
                            if (section.key == "home") go(Route.Home, "home")
                            else go(Route.Section(section.key), section.key)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("Devices") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Route.Devices) { launchSingleTop = true }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Sign out") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        scope.launch { container.sessionRepository.signOut() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
        NavHost(navController = navController, startDestination = Route.Home) {
            composable<Route.Home> {
                HomeScreen(
                    person = person,
                    onOpenDrawer = openDrawer,
                    onLogWorkout = { date -> navController.navigate(Route.WorkoutLog(date)) },
                )
            }
            composable<Route.Section> { entry ->
                val key = entry.toRoute<Route.Section>().key
                PlaceholderScreen(title = sectionFor(key).label, onOpenDrawer = openDrawer)
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
    }
}

@Composable
private fun ColorDot(color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color),
    )
}
