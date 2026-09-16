package io.github.zmdld11.shuschedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.zmdld11.shuschedule.ui.hub.HubViewModel
import io.github.zmdld11.shuschedule.ui.importer.ImportScreen
import io.github.zmdld11.shuschedule.ui.theme.ShuScheduleTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShuScheduleTheme {
                AppNavHost()
            }
        }
    }
}

@Composable
private fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "hub") {
        composable("hub") { HubScreen(onImport = { nav.navigate("import") }) }
        composable("import") { ImportScreen(onFinished = { nav.popBackStack() }) }
    }
}

/** 临时枢纽页：M3 会被周视图主页取代，导入/备份入口随后迁入正式界面 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HubScreen(
    onImport: () -> Unit,
    viewModel: HubViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.exportTo(context, uri) { ok ->
            scope.launch { snackbar.showSnackbar(if (ok) "备份已导出" else "导出失败") }
        }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importFrom(context, uri) { ok, msg ->
            scope.launch { snackbar.showSnackbar(if (ok) "备份已恢复" else "恢复失败：$msg") }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("上大课表") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("v0.1.0-dev", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("从教务导入课表") }
            OutlinedButton(
                onClick = { exportLauncher.launch("shu-schedule-backup.json") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("导出备份 (JSON)") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("从备份恢复") }
            TextButton(onClick = onImport) { Text("M3 期间临时入口，正式主页见 #4") }
        }
    }
}
