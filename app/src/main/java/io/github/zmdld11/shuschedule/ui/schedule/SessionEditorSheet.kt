package io.github.zmdld11.shuschedule.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zmdld11.shuschedule.data.db.CourseSession
import io.github.zmdld11.shuschedule.data.parser.WeekTextParser

/**
 * 课程/时段编辑表单：新建自定义课程、给已有课程加时段、改任意字段。
 * 周次文本走 WeekTextParser 语法（"1-16周"、"2-16周(双)"…），实时预览解析结果。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEditorSheet(
    courseName: String,
    initialSession: CourseSession?,
    slotCount: Int,
    isNewCourse: Boolean,
    onSave: (
        name: String,
        weekday: Int,
        startNode: Int,
        endNode: Int,
        weeksText: String,
        room: String,
        teacher: String,
        campus: String,
    ) -> Unit,
    onDeleteSession: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(courseName) }
    var weekday by remember { mutableStateOf(initialSession?.weekday ?: 1) }
    var startNode by remember { mutableStateOf(initialSession?.startNode ?: 1) }
    var endNode by remember { mutableStateOf(initialSession?.endNode ?: 2) }
    var weeksText by remember {
        mutableStateOf(
            initialSession?.let { formatWeeks(CourseSession.weeksOf(it.weeksMask)) } ?: "1-16周"
        )
    }
    var room by remember { mutableStateOf(initialSession?.room.orEmpty()) }
    var teacher by remember { mutableStateOf(initialSession?.teacher.orEmpty()) }
    var campus by remember { mutableStateOf(initialSession?.campus.orEmpty()) }

    val maxNode = maxOf(slotCount, endNode)
    val weeksPreview = formatWeeks(WeekTextParser.parseWeeks(weeksText))
    val valid = name.isNotBlank() && startNode <= endNode

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            when {
                isNewCourse -> "添加课程"
                initialSession == null -> "添加时段"
                else -> "编辑时段"
            },
            style = MaterialTheme.typography.titleLarge,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("课程名") },
            singleLine = true,
            isError = name.isBlank(),
            supportingText = if (name.isBlank()) { { Text("课程名不能为空") } } else null,
        )

        Text("星期", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..7).forEach { d ->
                FilterChip(
                    selected = weekday == d,
                    onClick = { weekday = d },
                    label = { Text(DAY_CHARS[d - 1]) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NodeDropdown(
                label = "开始",
                value = startNode,
                range = 1..maxNode,
                onChange = { startNode = it },
            )
            NodeDropdown(
                label = "结束",
                value = endNode,
                range = 1..maxNode,
                onChange = { endNode = it },
            )
            if (startNode > endNode) {
                Text(
                    "开始节需 ≤ 结束节",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        OutlinedTextField(
            value = weeksText,
            onValueChange = { weeksText = it },
            label = { Text("周次") },
            singleLine = true,
            supportingText = { Text("将保存为：$weeksPreview") },
        )

        OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("教室") }, singleLine = true)
        OutlinedTextField(value = teacher, onValueChange = { teacher = it }, label = { Text("教师") }, singleLine = true)
        OutlinedTextField(value = campus, onValueChange = { campus = it }, label = { Text("校区（可空）") }, singleLine = true)

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    onSave(name.trim(), weekday, startNode, endNode, weeksText, room.trim(), teacher.trim(), campus.trim())
                },
                enabled = valid,
            ) { Text("保存") }
            OutlinedButton(onClick = onDismiss) { Text("取消") }
            if (onDeleteSession != null) {
                TextButton(onClick = onDeleteSession) { Text("删除该时段") }
            }
        }
        if (onDeleteSession != null) {
            Text(
                "删除该学期的最后一个时段会连课程一起删除",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NodeDropdown(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) {
            Text("$label 第 $value 节")
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            range.forEach { n ->
                DropdownMenuItem(
                    text = { Text("第 $n 节") },
                    onClick = {
                        onChange(n)
                        expanded = false
                    },
                )
            }
        }
    }
}
