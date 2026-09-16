package io.github.zmdld11.shuschedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.zmdld11.shuschedule.ui.MainViewModel
import io.github.zmdld11.shuschedule.ui.importer.ImportScreen
import io.github.zmdld11.shuschedule.ui.schedule.ScheduleScreen
import io.github.zmdld11.shuschedule.ui.semester.SemestersScreen
import io.github.zmdld11.shuschedule.ui.settings.SettingsScreen
import io.github.zmdld11.shuschedule.ui.theme.ShuScheduleTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val dynamicColor by mainViewModel.dynamicColor.collectAsStateWithLifecycle()
            ShuScheduleTheme(dynamicColor = dynamicColor) {
                AppNavHost(mainViewModel = mainViewModel)
            }
        }
    }
}

@Composable
private fun AppNavHost(mainViewModel: MainViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            ScheduleScreen(
                onImport = { nav.navigate("import") },
                onSettings = { nav.navigate("settings") },
                onSemesters = { nav.navigate("semesters") },
            )
        }
        composable("import") {
            ImportScreen(onFinished = { nav.popBackStack() })
        }
        composable("semesters") {
            SemestersScreen(onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onImport = { nav.navigate("import") },
                mainViewModel = mainViewModel,
            )
        }
    }
}
