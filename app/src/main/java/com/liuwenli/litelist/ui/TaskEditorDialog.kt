package com.liuwenli.litelist.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import com.liuwenli.litelist.data.StaleTaskException
import com.liuwenli.litelist.data.TaskEntity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.launch

@Composable
fun TaskEditorDialog(
    initialDate: LocalDate,
    existingTask: TaskEntity? = null,
    onDismiss: () -> Unit,
    loadReminders: suspend () -> List<LocalDateTime> = { emptyList() },
    onReenable: suspend () -> Unit = {},
    onSave: suspend (String, String?, LocalDate, LocalDateTime?, List<LocalDateTime>, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember(existingTask?.taskId) { mutableStateOf(existingTask?.title.orEmpty()) }
    var note by remember(existingTask?.taskId) { mutableStateOf(existingTask?.note.orEmpty()) }
    var plannedDate by remember(existingTask?.taskId) { mutableStateOf(existingTask?.plannedDate?.let(LocalDate::parse) ?: initialDate) }
    var deadline by remember(existingTask?.taskId) { mutableStateOf(existingTask?.deadlineLocalDateTime?.let(LocalDateTime::parse)) }
    var deadlineReminderEnabled by remember(existingTask?.taskId) { mutableStateOf(existingTask?.deadlineReminderDisabled != true) }
    var normalReminders by remember(existingTask?.taskId) { mutableStateOf(emptyList<LocalDateTime>()) }
    var remindersLoaded by remember(existingTask?.taskId) { mutableStateOf(existingTask == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var stale by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(existingTask?.taskId) {
        if (existingTask != null) {
            runCatching { loadReminders() }
                .onSuccess { normalReminders = it; remindersLoaded = true }
                .onFailure { error = it.message ?: "提醒读取失败" }
        }
    }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (existingTask == null) "创建任务" else "编辑任务", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    label = { Text("任务标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it; error = null },
                    label = { Text("备注（可选）") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    DatePickerDialog(context, { _, year, month, day ->
                        plannedDate = LocalDate.of(year, month + 1, day)
                    }, plannedDate.year, plannedDate.monthValue - 1, plannedDate.dayOfMonth).show()
                }) { Text("计划日期：$plannedDate") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val start = deadline ?: plannedDate.atTime(9, 0)
                        DatePickerDialog(context, { _, year, month, day ->
                            val chosenDate = LocalDate.of(year, month + 1, day)
                            TimePickerDialog(context, { _, hour, minute ->
                                deadline = chosenDate.atTime(hour, minute)
                            }, start.hour, start.minute, true).show()
                        }, start.year, start.monthValue - 1, start.dayOfMonth).show()
                    }) { Text(deadline?.let { "DDL：$it" } ?: "设置 DDL（可选）") }
                    if (deadline != null) TextButton(onClick = { deadline = null }) { Text("清除") }
                }
                if (deadline != null) {
                    Row {
                        Checkbox(checked = deadlineReminderEnabled, onCheckedChange = { deadlineReminderEnabled = it })
                        Text("到 DDL 时提醒", modifier = Modifier.padding(top = 12.dp))
                    }
                }
                Text("普通提醒（可多个）", style = MaterialTheme.typography.titleMedium)
                if (!remindersLoaded) Text("正在读取提醒…")
                normalReminders.forEach { time ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(time.toString(), modifier = Modifier.padding(top = 12.dp))
                        TextButton(onClick = { normalReminders = normalReminders - time }) { Text("移除") }
                    }
                }
                TextButton(onClick = {
                    val start = deadline ?: plannedDate.atTime(9, 0)
                    DatePickerDialog(context, { _, year, month, day ->
                        val date = LocalDate.of(year, month + 1, day)
                        TimePickerDialog(context, { _, hour, minute ->
                            val selected = date.atTime(hour, minute)
                            if (selected in normalReminders) error = "同一时刻不能重复提醒"
                            else { normalReminders = (normalReminders + selected).sorted(); error = null }
                        }, start.hour, start.minute, true).show()
                    }, start.year, start.monthValue - 1, start.dayOfMonth).show()
                }, enabled = remindersLoaded) { Text("添加普通提醒") }
                if (existingTask?.reminderMode == "MUTED") {
                    Text("此任务的提醒已静音")
                    TextButton(onClick = {
                        scope.launch {
                            saving = true
                            runCatching { onReenable() }
                                .onSuccess { onDismiss() }
                                .onFailure { error = it.message ?: "开启失败" }
                            saving = false
                        }
                    }, enabled = !saving) { Text("重新开启提醒") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (stale) TextButton(onClick = onDismiss) { Text("关闭并重新加载") }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
                    Button(onClick = {
                        scope.launch {
                            saving = true
                            runCatching { onSave(title, note, plannedDate, deadline, normalReminders, deadlineReminderEnabled) }
                                .onSuccess { onDismiss() }
                                .onFailure { error = it.message ?: "保存失败"; stale = it is StaleTaskException }
                            saving = false
                        }
                    }, enabled = !saving && remindersLoaded) { Text(if (saving) "保存中" else "保存") }
                }
            }
        }
    }
}

