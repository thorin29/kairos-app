package com.kairos.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kairos.app.data.session.SessionState
import com.kairos.app.di.AppContainer
import com.kairos.app.ui.auth.AuthFlow
import com.kairos.app.ui.common.LoadingScreen
import com.kairos.app.ui.common.PlaceholderScreen
import com.kairos.app.ui.common.rememberContainer
import androidx.compose.ui.Alignment
import com.kairos.app.ui.common.OfflineBanner
import com.kairos.app.ui.devices.DevicesScreen
import com.kairos.app.ui.home.HomeScreen
import com.kairos.app.ui.nav.KairosRail
import com.kairos.app.ui.nav.Route
import com.kairos.app.ui.nav.sectionFor
import com.kairos.app.ui.bible.BibleScreen
import com.kairos.app.ui.calendar.CalendarScreen
import com.kairos.app.ui.chores.ChoresScreen
import com.kairos.app.ui.money.MoneyScreen
import com.kairos.app.ui.groceries.GroceriesScreen
import com.kairos.app.ui.character.CharacterScreen
import com.kairos.app.ui.character.GalleryScreen
import com.kairos.app.ui.character.CoopScreen
import com.kairos.app.ui.school.SchoolScreen
import com.kairos.app.ui.school.AddSchoolWorkScreen
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.tasks.TasksScreen
import com.kairos.app.ui.tasks.AssignTaskScreen
import com.kairos.app.ui.groceries.AddGroceryScreen
import com.kairos.app.ui.reading.ReadingScreen
import com.kairos.app.ui.reauth.ReauthScreen
import com.kairos.app.ui.setup.SetupScreen
import com.kairos.app.ui.workout.WorkoutLogScreen
import com.kairos.app.ui.workout.BrowseWorkoutsScreen
import com.kairos.app.ui.workout.CreatePersonalWorkoutScreen
import com.kairos.app.ui.workout.EditPlanScreen
import com.kairos.app.ui.workout.RotationScreen
import com.kairos.app.ui.workout.RecentWorkoutsScreen
import com.kairos.app.ui.workout.WeightCalculatorScreen
import com.kairos.app.ui.workout.WorkoutsScreen
import kotlinx.coroutines.launch

/**
 * The single auth gate. It watches [SessionState] and shows exactly one of:
 * setup (no server), enroll (no token), reauth (password changed), or the
 * authenticated app. Losing the token flips state and swaps back automatically.
 */
@Composable
fun AppRoot(container: AppContainer) {
    val session = container.sessionRepository
    val state by session.state.collectAsState()

    // Show the branded splash for at least ~2s on launch, even when the session
    // resolves instantly, so the logo + καιρός is actually seen.
    var minSplashDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        minSplashDone = true
    }

    val s = state
    if (!minSplashDone || s is SessionState.Loading) {
        LoadingScreen()
        return
    }

    when (s) {
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
 * The authenticated area. The rail slides straight in from the left (rounded on
 * the top-right, full-height under the status bar); the content behind blurs and
 * dims, and a subtle scrim over the status-bar strip keeps the system icons
 * readable. Opens collapsed; expanding sticks for the session.
 */
@Composable
private fun AuthenticatedApp(person: com.kairos.app.data.remote.dto.PersonDto) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val container = rememberContainer()

    var open by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val expanded by container.navExpanded.collectAsState()
    val online by container.networkMonitor.online.collectAsState()
    var selectedKey by remember { mutableStateOf("home") }

    // Bump each time we come back to Home from another destination, so Home can
    // reload the day. Reliable under our drawer nav, where ON_RESUME doesn't fire.
    val currentEntry by navController.currentBackStackEntryAsState()
    var homeRefresh by remember { mutableStateOf(0) }
    var wasAwayFromHome by remember { mutableStateOf(false) }
    val homeRouteName = remember { Route.Home::class.qualifiedName }
    LaunchedEffect(currentEntry) {
        val isHome = currentEntry?.destination?.route == homeRouteName
        if (isHome) {
            if (wasAwayFromHome) {
                homeRefresh++
                wasAwayFromHome = false
            }
        } else if (currentEntry != null) {
            wasAwayFromHome = true
        }
    }

    val openProgress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
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
        // Content — blurred and dimmed while the menu is open.
        Box(Modifier.fillMaxSize().blur(radius = (openProgress * 6f).dp)) {
            NavHost(navController = navController, startDestination = Route.Home) {
                composable<Route.Home> {
                    HomeScreen(
                        person = person,
                        onOpenDrawer = { open = true },
                        onLogWorkout = { date -> navController.navigate(Route.WorkoutLog(date)) },
                        onOpenMoney = { go(Route.Section("money"), "money") },
                        onAssignTask = { navController.navigate(Route.AssignTask) },
                        refreshKey = homeRefresh,
                    )
                }
                composable<Route.Section> { entry ->
                    val key = entry.toRoute<Route.Section>().key
                    if (key == "workouts") {
                        WorkoutsScreen(
                            onOpenDrawer = { open = true },
                            onLogWorkout = { date -> navController.navigate(Route.WorkoutLog(date)) },
                            onOpenRecent = { navController.navigate(Route.RecentWorkouts) },
                            onOpenCalculator = { navController.navigate(Route.WeightCalculator) },
                            onOpenBrowse = { navController.navigate(Route.BrowseWorkouts) },
                            onOpenCreatePersonal = { navController.navigate(Route.CreatePersonalWorkout) },
                            onOpenEditPlan = { navController.navigate(Route.EditPlan) },
                        )
                    } else if (key == "bible") {
                        BibleScreen(onOpenDrawer = { open = true })
                    } else if (key == "chores") {
                        ChoresScreen(onOpenDrawer = { open = true })
                    } else if (key == "calendar") {
                        CalendarScreen(onOpenDrawer = { open = true })
                    } else if (key == "money") {
                        MoneyScreen(onOpenDrawer = { open = true })
                    } else if (key == "reading") {
                        ReadingScreen(onOpenDrawer = { open = true })
                    } else if (key == "tasks") {
                        TasksScreen(
                            onOpenDrawer = { open = true },
                            onOpenAssign = { navController.navigate(Route.AssignTask) },
                        )
                    } else if (key == "school") {
                        SchoolScreen(
                            onOpenDrawer = { open = true },
                            onOpenAdd = { navController.navigate(Route.AddSchool) },
                        )
                    } else if (key == "characters") {
                        CharacterScreen(
                            person = person,
                            onOpenDrawer = { open = true },
                            onOpenGallery = { navController.navigate(Route.Gallery) },
                            onOpenCoop = { navController.navigate(Route.Coop) },
                        )
                    } else if (key == "groceries") {
                        GroceriesScreen(
                            onOpenDrawer = { open = true },
                            onAddItem = { navController.navigate(Route.AddGrocery) },
                            meId = person.id,
                            canDeleteAny = person.kind == "PARENT" || person.role == "ADMIN",
                        )
                    } else {
                        PlaceholderScreen(title = sectionFor(key).label, onOpenDrawer = { open = true })
                    }
                }
                composable<Route.RecentWorkouts> {
                    RecentWorkoutsScreen(onBack = { navController.popBackStack() })
                }
                composable<Route.WeightCalculator> {
                    WeightCalculatorScreen(onBack = { navController.popBackStack() })
                }
                composable<Route.BrowseWorkouts> {
                    BrowseWorkoutsScreen(onBack = { navController.popBackStack() })
                }
                composable<Route.CreatePersonalWorkout> {
                    CreatePersonalWorkoutScreen(onBack = { navController.popBackStack() })
                }
                composable<Route.EditPlan> {
                    EditPlanScreen(
                        onBack = { navController.popBackStack() },
                        onOpenRotation = { navController.navigate(Route.Rotation) },
                    )
                }
                composable<Route.AddGrocery> {
                    AddGroceryScreen(
                        parentEntry = navController.previousBackStackEntry,
                        onClose = { navController.popBackStack() },
                    )
                }
                composable<Route.Gallery> {
                    GalleryScreen(onBack = { navController.popBackStack() })
                }
                composable<Route.Coop> {
                    CoopScreen(onBack = { navController.popBackStack() })
                }
                composable<Route.AddSchool> {
                    AddSchoolWorkScreen(
                        parentEntry = navController.previousBackStackEntry,
                        onClose = { navController.popBackStack() },
                    )
                }
                composable<Route.AssignTask> {
                    AssignTaskScreen(
                        parentEntry = navController.previousBackStackEntry,
                        onClose = { homeRefresh++; navController.popBackStack() },
                    )
                }
                composable<Route.Rotation> {
                    RotationScreen(onBack = { navController.popBackStack() })
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

        if (openProgress > 0.001f) {
            // Dim scrim over the (blurred) content — tap to close.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = openProgress }
                    .background(Color.Black.copy(alpha = 0.28f))
                    .pointerInput(Unit) { detectTapGestures { open = false } },
            )
            // The rail: slides in from the left, rounded on the top-right.
            KairosRail(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(railWidth)
                    .graphicsLayer { translationX = (openProgress - 1f) * size.width },
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
                onSignOut = { confirmSignOut = true },
            )
            // Subtle darker shade over the status-bar strip for icon readability.
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(Color.Black.copy(alpha = 0.18f * openProgress)),
            )
        }

        if (confirmSignOut) {
            AnimatedDialog(
                onDismissRequest = { confirmSignOut = false },
                title = "Sign out?",
                confirmButton = {
                    TextButton(onClick = {
                        confirmSignOut = false
                        open = false
                        scope.launch { container.sessionRepository.signOut() }
                    }) { Text("Sign out") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
                },
            ) {
                Text("You'll need your password and a device code to sign in again on this phone.")
            }
        }

        OfflineBanner(online = online, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
