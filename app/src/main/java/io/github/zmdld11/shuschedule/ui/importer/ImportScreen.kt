package io.github.zmdld11.shuschedule.ui.importer

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.zmdld11.shuschedule.data.db.TermType
import io.github.zmdld11.shuschedule.data.jwxk.JwxkSpec
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** WebView 与原生侧的 JS 桥（仅上报数据，不提供任何可执行入口） */
private class ImportWebBridge(
    private val onProbe: (Boolean) -> Unit,
    private val onSchedule: (String) -> Unit,
) {
    @JavascriptInterface
    fun onLoginProbe(ok: Boolean) = onProbe(ok)

    @JavascriptInterface
    fun onScheduleJson(json: String) = onSchedule(json)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onFinished: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val probeScript by viewModel.loginProbeScript.collectAsStateWithLifecycle()
    val fetchScript by viewModel.fetchScript.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val selectedTerm by viewModel.selectedTerm.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var webView by remember { mutableStateOf<WebView?>(null) }
    val bridge = remember {
        ImportWebBridge(
            onProbe = viewModel::onLoginProbe,
            onSchedule = viewModel::onScheduleJson,
        )
    }

    // 抓取指令 → 注入 WebView
    LaunchedEffect(fetchScript) {
        fetchScript?.let { js ->
            webView?.evaluateJavascript(js, null)
            viewModel.consumeFetchScript()
        }
    }

    // 登录态轮询：每 3 秒注入一次探测脚本
    LaunchedEffect(probeScript, webView) {
        val js = probeScript ?: return@LaunchedEffect
        while (true) {
            webView?.evaluateJavascript(js, null)
            delay(3000)
        }
    }

    LaunchedEffect(state) {
        val s = state
        if (s is ImportState.Error) snackbar.showSnackbar(s.message)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("导入课表") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val s = state) {
            is ImportState.Preview -> PreviewContent(
                preview = s,
                onConfirm = viewModel::confirmImport,
                onCancel = viewModel::backToReady,
                modifier = Modifier.padding(padding),
            )

            is ImportState.Done -> DoneContent(
                done = s,
                onFinish = onFinished,
                modifier = Modifier.padding(padding),
            )

            else -> Column(Modifier.padding(padding).fillMaxSize()) {
                if (state is ImportState.Fetching) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("学年 ${selectedYear}-${selectedYear + 1}", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(-1, 1).forEach { delta ->
                            OutlinedButton(onClick = { viewModel.selectedYear.value += delta }) {
                                Text(if (delta < 0) "上一年" else "下一年")
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TermType.entries.forEach { term ->
                            FilterChip(
                                selected = selectedTerm == term,
                                onClick = { viewModel.selectedTerm.value = term },
                                label = { Text(term.label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when (state) {
                            is ImportState.WaitingLogin -> "① 在下方「上海大学统一身份认证」页登录（账号即一卡通）；② 登录成功回到教务页面后，点「开始抓取」。密码只进学校官方页面，本应用不保存。"
                            is ImportState.Ready -> "已检测到登录态 ✓ 选择学年学期后点「开始抓取」"
                            else -> "正在抓取课表…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = viewModel::startFetch,
                        enabled = state !is ImportState.Fetching,
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) { Text("开始抓取") }
                }

                @SuppressLint("SetJavaScriptEnabled")
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            addJavascriptInterface(bridge, ImportViewModel.BRIDGE_NAME)
                            loadUrl(JwxkSpec.SSO_LOGIN_URL)
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewContent(
    preview: ImportState.Preview,
    onConfirm: (LocalDate) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(preview.suggestedStart) }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("抓取成功，请核对", style = MaterialTheme.typography.titleLarge)
        Text(
            "${preview.year}-${preview.year + 1}学年${preview.term.label}（xqm=${preview.xqmUsed}）· 共 ${preview.courses.size} 门课",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("开学第一周周一：", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { showDatePicker = true }) { Text(startDate.toString()) }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(preview.courses) { course ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(
                        "${course.name}（${course.credit}学分）",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        course.sessions.joinToString("；") { s ->
                            "周${"一二三四五六日"[s.weekday - 1]} 第${s.startNode}-${s.endNode}节 ${s.weekText.ifBlank { "全学期" }} ${s.room} ${s.teacher}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onConfirm(startDate) }) { Text("确认导入") }
            OutlinedButton(onClick = onCancel) { Text("返回") }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        startDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DoneContent(
    done: ImportState.Done,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("导入完成 🎉", style = MaterialTheme.typography.titleLarge)
        Text("${done.semesterName} · ${done.importedCourses} 门课已保存")
        Button(onClick = onFinish) { Text("完成") }
    }
}
