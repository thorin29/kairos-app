package com.kairos.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairos.app.data.session.SessionState
import com.kairos.app.di.AppContainer
import com.kairos.app.ui.common.LoadingScreen
import com.kairos.app.ui.enroll.EnrollScreen
import com.kairos.app.ui.home.HomeScreen
import com.kairos.app.ui.nav.Route
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
        is SessionState.NeedsEnroll -> EnrollScreen()
        is SessionState.Ready -> AuthenticatedApp(person = s.person)
    }
}

/** The authenticated area. A NavHost so it can grow bottom-nav destinations as
 *  read screens land; for now it holds only Home. */
@Composable
private fun AuthenticatedApp(person: com.kairos.app.data.remote.dto.PersonDto) {
    val navController = rememberNavController()
    val startRoute = remember { Route.Home }
    NavHost(navController = navController, startDestination = startRoute) {
        composable<Route.Home> {
            HomeScreen(person = person)
        }
    }
}
