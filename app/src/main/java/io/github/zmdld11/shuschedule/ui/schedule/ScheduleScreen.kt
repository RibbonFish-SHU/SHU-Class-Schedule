package io.github.zmdld11.shuschedule.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.zmdld11.shuschedule.data.db.CourseSession
import io.github.zmdld11.shuschedule.data.db.CourseWithSessions
import java.time.LocalDate

private val CELL_HEIGHT = 52.dp
private val DAY_CHARS = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onSemesters: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedWeek by viewModel.selectedWeek.collectAsStateWithLifecycle()
    val detail by viewModel.detailCourse.collectAsStateWithLifecycle()

    val currentWeek = state.currentWeek
    // 纯 Compose 派生：selectedWeek 只经追踪的 State 读，避免原始 Flow.value 读取与重组时序分歧
    val week = selectedWeek ?: currentWeek
    var showJumpDialog by remember { mutableStateOf(false) }

    val semester = state.semester

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            semester?.displayName ?: "上大课表",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        Text(
                            if (semester == null) "去导入第一张课表吧" else "第 $week / ${semester.totalWeeks} 周${if (week == currentWeek) " · 本周" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSemesters) {
                        Icon(Icons.Filled.EventNote, contentDescription = "学期管理")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { padding ->
        if (semester == null) {
            Column(
                Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("还没有课表", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "从上海大学教务系统一键导入",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onImport) { Text("从教务导入课表") }
            }
            return@Scaffold
        }

        Column(Modifier.padding(padding).fillMaxSize()) {
            // 周切换条
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { viewModel.selectWeek((week - 1).coerceAtLeast(1)) },
                    enabled = week > 1,
                ) { Text("‹", style = MaterialTheme.typography.titleLarge) }
                Text(
                    "第 $week 周",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showJumpDialog = true },
                    textAlign = TextAlign.Center,
                )
                if (selectedWeek != null) {
                    TextButton(onClick = { viewModel.selectWeek(null) }) { Text("回本周") }
                }
                TextButton(
                    onClick = { viewModel.selectWeek((week + 1).coerceAtMost(semester.totalWeeks)) },
                    enabled = week < semester.totalWeeks,
                ) { Text("›", style = MaterialTheme.typography.titleLarge) }
            }

            // 表头：星期 + 日期
            val today = LocalDate.now()
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(40.dp))
                repeat(7) { i ->
                    val date = state.dateOf(week, i + 1)
                    val isToday = date == today
                    Column(
                        Modifier.weight(1f).padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            DAY_CHARS[i],
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isToday) FontWeight.Bold else null,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            date?.let { "${it.monthValue}/${it.dayOfMonth}" } ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 网格主体
            val nodeCount = maxOf(state.timeSlots.size, state.courses.maxOfOrNull { c -> c.sessions.maxOfOrNull { it.endNode } ?: 0 } ?: 0, 10)
            Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // 左侧节次时间列
                Column(Modifier.width(40.dp)) {
                    repeat(nodeCount) { i ->
                        val slot = state.timeSlots.getOrNull(i)
                        Column(
                            Modifier.height(CELL_HEIGHT).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelSmall)
                            Text(
                                slot?.startTime ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // 7 天课程列
                repeat(7) { dayIdx ->
                    val weekday = dayIdx + 1
                    val blocks = viewModel.blocksFor(week, weekday)
                    Box(Modifier.weight(1f).height(CELL_HEIGHT * nodeCount)) {
                        blocks.forEach { block ->
                            val span = block.session.endNode - block.session.startNode + 1
                            Box(
                                Modifier
                                    .offset(y = CELL_HEIGHT * (block.session.startNode - 1))
                                    .fillMaxWidth()
                                    .height(CELL_HEIGHT * span - 2.dp)
                                    .padding(horizontal = 1.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CoursePalette.container(block.course.course.colorIndex))
                                    .clickable { viewModel.showDetail(block.course) }
                                    .padding(horizontal = 3.dp, vertical = 2.dp),
                            ) {
                                Column {
                                    Text(
                                        block.course.course.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = CoursePalette.onContainer(block.course.course.colorIndex),
                                        maxLines = if (span >= 2) 3 else 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (span >= 2) {
                                        val place = listOf(
                                            block.session.campus.takeIf { it.isNotBlank() },
                                            block.session.room.takeIf { it.isNotBlank() }?.let { "@$it" },
                                        ).filterNotNull().joinToString("·")
                                        if (place.isNotBlank()) {
                                            Text(
                                                place,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                lineHeight = 11.sp,
                                                color = CoursePalette.onContainer(block.course.course.colorIndex),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (block.session.teacher.isNotBlank()) {
                                            Text(
                                                block.session.teacher,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                lineHeight = 11.sp,
                                                color = CoursePalette.onContainer(block.course.course.colorIndex),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 课程详情
    detail?.let { course ->
        ModalBottomSheet(onDismissRequest = { viewModel.showDetail(null) }) {
            CourseDetailContent(
                course = course,
                currentWeek = week,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            )
        }
    }

    // 跳周对话框
    if (showJumpDialog && semester != null) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) { Text("取消") }
            },
            title = { Text("跳转到周次") },
            text = {
                Column {
                    repeat(semester.totalWeeks) { i ->
                        val w = i + 1
                        TextButton(
                            onClick = {
                                viewModel.selectWeek(w)
                                showJumpDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "第 $w 周${if (w == currentWeek) "（本周）" else ""}",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun CourseDetailContent(
    course: CourseWithSessions,
    currentWeek: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(course.course.name, style = MaterialTheme.typography.titleLarge)
        Text(
            listOfNotNull(
                course.course.className.takeIf { it.isNotBlank() },
                "课程号 ${course.course.courseCode}".takeIf { course.course.courseCode.isNotBlank() },
                course.course.credit.takeIf { it.isNotBlank() }?.let { "${it} 学分" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        course.sessions.sortedWith(compareBy({ it.weekday }, { it.startNode })).forEach { s ->
            SessionRow(session = s, currentWeek = currentWeek)
        }
    }
}

@Composable
private fun SessionRow(session: CourseSession, currentWeek: Int) {
    val hasThisWeek = session.hasWeek(currentWeek)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "周${DAY_CHARS[session.weekday - 1]} 第${session.startNode}-${session.endNode}节",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                listOf(
                    formatWeeks(CourseSession.weeksOf(session.weeksMask)),
                    session.campus.takeIf { it.isNotBlank() },
                    session.room,
                    session.teacher,
                ).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (hasThisWeek) "本周有课" else "本周无课",
            style = MaterialTheme.typography.labelMedium,
            color = if (hasThisWeek) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}

/** 周次集合 → 压缩文本：{1..8}→"1-8周"；全奇/全偶→"2-16周(双)"；杂散→"1,5,9周" */
private fun formatWeeks(weeks: Set<Int>): String {
    if (weeks.isEmpty()) return "全学期"
    val sorted = weeks.sorted()
    if (sorted.size >= 3) {
        val allOdd = sorted.all { it % 2 == 1 }
        val allEven = sorted.all { it % 2 == 0 }
        if ((allOdd || allEven) && (sorted.last() - sorted.first()) / 2 + 1 == sorted.size) {
            val tag = if (allOdd) "单" else "双"
            return "${sorted.first()}-${sorted.last()}周($tag)"
        }
    }
    val parts = mutableListOf<String>()
    var start = sorted.first()
    var prev = start
    for (w in sorted.drop(1)) {
        if (w == prev + 1) {
            prev = w
        } else {
            parts += if (start == prev) "${start}周" else "${start}-${prev}周"
            start = w
            prev = w
        }
    }
    parts += if (start == prev) "${start}周" else "${start}-${prev}周"
    return parts.joinToString(",")
}
