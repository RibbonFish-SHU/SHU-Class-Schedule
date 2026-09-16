package io.github.zmdld11.shuschedule.ui.semester

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zmdld11.shuschedule.data.db.Semester
import io.github.zmdld11.shuschedule.data.repo.ScheduleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class SemestersViewModel @Inject constructor(
    private val repository: ScheduleRepository,
) : ViewModel() {

    val semesters: StateFlow<List<Semester>> =
        repository.observeSemesters().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun activate(id: Long) = viewModelScope.launch { repository.activateSemester(id) }

    fun update(id: Long, startDateEpochDay: Long, weeks: Int) =
        viewModelScope.launch { repository.updateSemesterRange(id, startDateEpochDay, weeks) }

    fun delete(id: Long) = viewModelScope.launch { repository.deleteSemester(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemestersScreen(
    onBack: () -> Unit,
    viewModel: SemestersViewModel = hiltViewModel(),
) {
    val semesters by viewModel.semesters.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Semester?>(null) }
    var deleting by remember { mutableStateOf<Semester?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("学期管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(semesters, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(s.displayName, style = MaterialTheme.typography.titleMedium)
                            if (s.isActive) {
                                Text(
                                    "使用中",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            "开学 ${LocalDate.ofEpochDay(s.startDateEpochDay)} · ${s.totalWeeks} 周",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!s.isActive) {
                                TextButton(onClick = { viewModel.activate(s.id) }) { Text("设为使用中") }
                            }
                            TextButton(onClick = { editing = s }) { Text("编辑") }
                            TextButton(onClick = { deleting = s }) { Text("删除") }
                        }
                    }
                }
            }
        }
    }

    editing?.let { s ->
        var weeks by remember(s.id) { mutableStateOf(s.totalWeeks.toString()) }
        var showPicker by remember(s.id) { mutableStateOf(false) }
        var startDate by remember(s.id) { mutableStateOf(LocalDate.ofEpochDay(s.startDateEpochDay)) }

        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("编辑 ${s.displayName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("第一周周一：", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { showPicker = true }) { Text(startDate.toString()) }
                    }
                    OutlinedTextField(
                        value = weeks,
                        onValueChange = { weeks = it.filter(Char::isDigit).take(2) },
                        label = { Text("总周数") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    weeks.toIntOrNull()?.let { w ->
                        if (w in 1..25) {
                            viewModel.update(s.id, startDate.toEpochDay(), w)
                            editing = null
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } },
        )

        if (showPicker) {
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = startDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { showPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let {
                            startDate = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                            // 归一到周一
                            val dow = startDate.dayOfWeek.value
                            if (dow != 1) startDate = startDate.minusDays((dow - 1).toLong())
                        }
                        showPicker = false
                    }) { Text("确定") }
                },
                dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } },
            ) {
                DatePicker(state = pickerState)
            }
        }
    }

    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除 ${s.displayName}？") },
            text = { Text("该学期全部课程将一并删除，且不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(s.id)
                    deleting = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}
