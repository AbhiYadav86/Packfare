package com.packfare.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.packfare.app.PackfareApp
import com.packfare.app.ui.expense.AddExpenseScreen
import com.packfare.app.ui.expense.AddExpenseViewModel
import com.packfare.app.ui.explore.ExploreScreen
import com.packfare.app.ui.fares.FareSearchScreen
import com.packfare.app.ui.fares.FareSearchViewModel
import com.packfare.app.ui.explore.ExploreViewModel
import com.packfare.app.ui.explore.PlaceScreen
import com.packfare.app.ui.explore.PlaceViewModel
import com.packfare.app.ui.home.HomeScreen
import com.packfare.app.ui.home.HomeTab
import com.packfare.app.ui.plan.HowLongScreen
import com.packfare.app.ui.plan.PlanTripScreen
import com.packfare.app.ui.plan.PlanTripViewModel
import com.packfare.app.ui.trip.TripDetailScreen
import com.packfare.app.ui.trip.TripDetailViewModel
import com.packfare.app.ui.trips.TripListScreen
import com.packfare.app.ui.trips.TripListViewModel
import com.packfare.app.ui.vault.DocViewerScreen
import com.packfare.app.ui.vault.DocViewerViewModel
import com.packfare.app.ui.vault.VaultScreen
import com.packfare.app.ui.vault.VaultViewModel

private object Routes {
    const val HOME = "home"
    const val TRIP = "trip/{tripId}"
    const val EXPENSE = "trip/{tripId}/expense?expenseId={expenseId}&scan={scan}"
    const val PLACE = "place/{title}"
    const val PLAN = "plan?stops={stops}&days={days}&budget={budget}&from={from}&resume={resume}&style={style}"
    const val DOC = "doc/{docId}"
    const val FARES = "fares?from={from}&to={to}&date={date}"

    fun trip(id: Long) = "trip/$id"
    fun expense(tripId: Long, expenseId: Long? = null, scan: Boolean = false) =
        "trip/$tripId/expense?expenseId=${expenseId ?: -1L}&scan=$scan"
    fun place(title: String) = "place/${Uri.encode(title)}"
    /** [stops] may carry nights as "Manali~3". [resume]: -2 draft, 0.. recent search, -1 none. */
    fun plan(
        stops: List<String> = emptyList(),
        days: Int = -1,
        budgetPaise: Long = -1,
        from: String? = null,
        resume: Int = -1,
        style: String? = null,
    ) = "plan?stops=${Uri.encode(stops.joinToString("|"))}&days=$days&budget=$budgetPaise&from=${Uri.encode(from.orEmpty())}" +
        "&resume=$resume&style=${Uri.encode(style.orEmpty())}"
    fun doc(id: Long) = "doc/$id"
    fun fares(from: String = "", to: String = "", epochDay: Long? = null) =
        "fares?from=${Uri.encode(from)}&to=${Uri.encode(to)}&date=${epochDay ?: -1L}"
}

@Composable
fun PackfareNavHost(app: PackfareApp) {
    val nav = rememberNavController()
    val repository = app.repository

    NavHost(navController = nav, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            var tab by rememberSaveable { mutableStateOf(HomeTab.TRIPS) }
            HomeScreen(tab = tab, onTabChange = { tab = it }) { current ->
                when (current) {
                    HomeTab.TRIPS -> {
                        val vm: TripListViewModel = viewModel(
                            factory = viewModelFactory { initializer { TripListViewModel(repository) } },
                        )
                        TripListScreen(
                            vm,
                            onOpenTrip = { nav.navigate(Routes.trip(it)) },
                            // Trip planning starts in Explore: themes, suggested trips, places.
                            onPlanTrip = { tab = HomeTab.EXPLORE },
                        )
                    }
                    HomeTab.EXPLORE -> {
                        val vm: ExploreViewModel = viewModel(
                            factory = viewModelFactory { initializer { ExploreViewModel(app.explore, app.prefs) } },
                        )
                        ExploreScreen(
                            vm,
                            onOpenPlace = { nav.navigate(Routes.place(it)) },
                            onPlanTrip = { stops, from, style -> nav.navigate(Routes.plan(stops.map { (n, k) -> "$n~$k" }, from = from, style = style)) },
                            onBuildOwn = { nav.navigate(Routes.plan()) },
                            onResume = { index -> nav.navigate(Routes.plan(resume = index)) },
                        )
                    }
                    HomeTab.FARES -> {
                        val vm: FareSearchViewModel = viewModel(
                            factory = viewModelFactory { initializer { FareSearchViewModel(app.fares, null, null, null, app.prefs) } },
                        )
                        FareSearchScreen(vm, onBack = null)
                    }
                    HomeTab.HOW_LONG -> HowLongScreen(
                        onPlan = { stops, days, budget -> nav.navigate(Routes.plan(stops, days, budget)) },
                    )
                    HomeTab.VAULT -> {
                        val vm: VaultViewModel = viewModel(
                            factory = viewModelFactory { initializer { VaultViewModel(app.vault) } },
                        )
                        VaultScreen(vm, onOpen = { nav.navigate(Routes.doc(it)) })
                    }
                }
            }
        }

        composable(Routes.TRIP, arguments = listOf(navArgument("tripId") { type = NavType.LongType })) { entry ->
            val tripId = entry.arguments!!.getLong("tripId")
            val vm: TripDetailViewModel = viewModel(
                factory = viewModelFactory { initializer { TripDetailViewModel(repository, tripId) } },
            )
            TripDetailScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onAddExpense = { scan -> nav.navigate(Routes.expense(tripId, scan = scan)) },
                onEditExpense = { expenseId -> nav.navigate(Routes.expense(tripId, expenseId)) },
                onTripDeleted = { nav.popBackStack(Routes.HOME, inclusive = false) },
                onFindFares = { from, to, day -> nav.navigate(Routes.fares(from, to, day)) },
            )
        }

        composable(
            Routes.EXPENSE,
            arguments = listOf(
                navArgument("tripId") { type = NavType.LongType },
                navArgument("expenseId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("scan") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val args = entry.arguments!!
            val tripId = args.getLong("tripId")
            val expenseId = args.getLong("expenseId").takeIf { it > 0 }
            val vm: AddExpenseViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        AddExpenseViewModel(repository, app.receiptScanner, app.receiptStorage, tripId, expenseId)
                    }
                },
            )
            AddExpenseScreen(vm, startWithScan = args.getBoolean("scan"), onDone = { nav.popBackStack() })
        }

        composable(Routes.PLACE, arguments = listOf(navArgument("title") { type = NavType.StringType })) { entry ->
            val title = entry.arguments?.getString("title").orEmpty()
            val vm: PlaceViewModel = viewModel(
                factory = viewModelFactory { initializer { PlaceViewModel(app.explore, title, app.fares, app.prefs) } },
            )
            PlaceScreen(
                vm = vm,
                title = title,
                onBack = { nav.popBackStack() },
                onPlan = { place, from, nights -> nav.navigate(Routes.plan(listOf(place), days = nights, from = from)) },
                onOpenPlace = { other -> nav.navigate(Routes.place(other)) },
                onFindFares = { dest -> nav.navigate(Routes.fares(to = dest)) },
            )
        }

        composable(
            Routes.PLAN,
            arguments = listOf(
                navArgument("stops") { type = NavType.StringType; defaultValue = "" },
                navArgument("days") { type = NavType.IntType; defaultValue = -1 },
                navArgument("budget") { type = NavType.LongType; defaultValue = -1L },
                navArgument("from") { type = NavType.StringType; defaultValue = "" },
                navArgument("resume") { type = NavType.IntType; defaultValue = -1 },
                navArgument("style") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val args = entry.arguments!!
            val resume = args.getInt("resume")
            val style = args.getString("style").orEmpty().ifBlank { null }?.let { com.packfare.app.domain.TravelStyle.fromName(it) }
            val stops = args.getString("stops").orEmpty().split("|").filter { it.isNotBlank() }
            val days = args.getInt("days").takeIf { it > 0 }
            val budget = args.getLong("budget").takeIf { it > 0 }
            val from = args.getString("from").orEmpty().ifBlank { null }
            val vm: PlanTripViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { PlanTripViewModel(repository, app.explore, app.fares, app.prefs, stops, days, budget, from, resume, style) }
                },
            )
            PlanTripScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onCreated = { tripId -> openTripReplacingPlanner(nav, tripId) },
            )
        }

        composable(
            Routes.FARES,
            arguments = listOf(
                navArgument("from") { type = NavType.StringType; defaultValue = "" },
                navArgument("to") { type = NavType.StringType; defaultValue = "" },
                navArgument("date") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            val args = entry.arguments!!
            val from = args.getString("from").orEmpty().ifBlank { null }
            val to = args.getString("to").orEmpty().ifBlank { null }
            val date = args.getLong("date").takeIf { it >= 0 }?.let { java.time.LocalDate.ofEpochDay(it) }
                ?.takeIf { !it.isBefore(java.time.LocalDate.now()) }
            val vm: FareSearchViewModel = viewModel(
                factory = viewModelFactory { initializer { FareSearchViewModel(app.fares, from, to, date, app.prefs) } },
            )
            FareSearchScreen(vm, onBack = { nav.popBackStack() })
        }

        composable(Routes.DOC, arguments = listOf(navArgument("docId") { type = NavType.LongType })) { entry ->
            val docId = entry.arguments!!.getLong("docId")
            val cacheDir = LocalContext.current.cacheDir
            val vm: DocViewerViewModel = viewModel(
                factory = viewModelFactory { initializer { DocViewerViewModel(app.vault, docId, cacheDir) } },
            )
            DocViewerScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}

/** After creating a trip, show it, and make Back go home rather than to the planner. */
private fun openTripReplacingPlanner(nav: NavHostController, tripId: Long) {
    nav.navigate(Routes.trip(tripId)) {
        popUpTo(Routes.HOME) { inclusive = false }
    }
}
