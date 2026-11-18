package com.liuwenli.litelist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.liuwenli.litelist.data.LiteListDatabase
import com.liuwenli.litelist.data.TaskEntity
import com.liuwenli.litelist.data.TaskRepository
import com.liuwenli.litelist.ui.TaskEditorDialog
import com.liuwenli.litelist.ui.TodayTaskSections
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repository by lazy {
        TaskRepository(Room.databaseBuilder(applicationContext, LiteListDatabase::class.java, "litelist.db").build(), Clock.systemDefaultZone())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { LiteListApp(repository) } }
    }
}

@Composable
private fun LiteListApp(repository: TaskRepository) {
    val tabs = listOf("今日", "日历", "我的")
    var selected by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<TaskEntity?>(null) }
    var pendingEdit by remember { mutableStateOf<TaskEntity?>(null) }
    var showDetailedCreate by remember { mutableStateOf(false) }
    var completedExpanded by remember { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf(true) }
    val tasks by remember(repository) { repository.observeTasks() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val today by produceState(initialValue = LocalDate.now()) {
        while (true) {
            value = LocalDate.now()
            val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
            delay(Duration.between(ZonedDateTime.now(), nextMidnight).toMillis().coerceAtLeast(1_000L))
        }
    }
    val sections = TodayTaskSections.from(tasks, today, ZoneId.systemDefault())

    Scaffold(bottomBar = {
        NavigationBar {
            tabs.forEachIndexed { index, name ->
                NavigationBarItem(
                    selected = selected == index,
                    onClick = { selected = index },
                    icon = { Text(name.take(1)) },
                    label = { Text(name) },
                )
            }
        }
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tabs[selected], style = MaterialTheme.typography.headlineMedium)
            when (selected) {
                0 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(title, { title = it }, label = { Text("任务标题") }, modifier = Modifier.weight(1f), singleLine = true)
                        Button(onClick = {
                            scope.launch {
                                runCatching { repository.create(title, null, LocalDate.now(), null) }
                                    .onSuccess { title = ""; error = null }
                                    .onFailure { error = it.message ?: "创建失败" }
                            }
                        }) { Text("添加") }
                    }
                    TextButton(onClick = { showDetailedCreate = true }) { Text("详细创建") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text("今日 ${sections.progressDone}/${sections.progressTotal}")
                    TodayTaskList(
                        modifier = Modifier.weight(1f),
                        sections = sections,
                        completedExpanded = completedExpanded,
                        historyExpanded = historyExpanded,
                        onCompletedExpandedChange = { completedExpanded = it },
                        onHistoryExpandedChange = { historyExpanded = it },
                        onToggle = { task ->
                        scope.launch {
                            runCatching {
                                if (task.status == "PENDING") repository.complete(task.generationId, task.taskId, task.dataVersion)
                                else repository.reopen(task.generationId, task.taskId, task.dataVersion)
                            }.onFailure { error = it.message ?: "操作失败" }
                        }
                        },
                        onDelete = { pendingDelete = it },
                        onEdit = { pendingEdit = it },
                    )
                }
                else -> Text("功能开发中")
            }
        }
    }
    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除任务？") },
            text = { Text(task.title) },
            confirmButton = {
                Button(onClick = {
                    pendingDelete = null
                    scope.launch {
                        runCatching { repository.delete(task.generationId, task.taskId, task.dataVersion) }
                            .onFailure { error = it.message ?: "删除失败" }
                    }
                }) { Text("删除") }
            },
            dismissButton = { Button(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
    if (showDetailedCreate) {
        TaskEditorDialog(
            initialDate = today,
            onDismiss = { showDetailedCreate = false },
            onSave = { taskTitle, note, plannedDate, deadline ->
                repository.create(taskTitle, note, plannedDate, deadline)
            },
        )
    }
    pendingEdit?.let { task ->
        TaskEditorDialog(
            initialDate = today,
            existingTask = task,
            onDismiss = { pendingEdit = null },
            onSave = { taskTitle, note, plannedDate, deadline ->
                repository.edit(task.generationId, task.taskId, task.dataVersion, taskTitle, note, plannedDate, deadline)
            },
        )
    }
}

@Composable
private fun TodayTaskList(
    modifier: Modifier,
    sections: com.liuwenli.litelist.ui.TodaySections,
    completedExpanded: Boolean,
    historyExpanded: Boolean,
    onCompletedExpandedChange: (Boolean) -> Unit,
    onHistoryExpandedChange: (Boolean) -> Unit,
    onToggle: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onEdit: (TaskEntity) -> Unit,
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { Text("今日待完成 (${sections.pending.size})", style = MaterialTheme.typography.titleMedium) }
        if (sections.pending.isEmpty()) item { Text("今天暂无待完成任务") }
        items(sections.pending, key = { "pending:${it.taskId}" }) { task -> TaskRow(task, onToggle, onDelete, onEdit) }

        item {
            TextButton(onClick = { onCompletedExpandedChange(!completedExpanded) }) {
                Text("今日已完成 (${sections.completed.size}) ${if (completedExpanded) "收起" else "展开"}")
            }
        }
        if (completedExpanded) {
            if (sections.completed.isEmpty()) item { Text("今天还没有完成任务") }
            items(sections.completed, key = { "completed:${it.taskId}" }) { task -> TaskRow(task, onToggle, onDelete, onEdit) }
        }

        item {
            TextButton(onClick = { onHistoryExpandedChange(!historyExpanded) }) {
                Text("历史未完成 (${sections.pastPending.size}) ${if (historyExpanded) "收起" else "展开"}")
            }
        }
        if (historyExpanded) {
            if (sections.pastPending.isEmpty()) item { Text("没有历史未完成任务") }
            items(sections.pastPending, key = { "history:${it.taskId}" }) { task ->
                Column {
                    Text(task.plannedDate, style = MaterialTheme.typography.labelMedium)
                    TaskRow(task, onToggle, onDelete, onEdit)
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntity, onToggle: (TaskEntity) -> Unit, onDelete: (TaskEntity) -> Unit, onEdit: (TaskEntity) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = task.status == "COMPLETED", onCheckedChange = { onToggle(task) })
        Text(task.title, modifier = Modifier.weight(1f).padding(top = 12.dp))
        TextButton(onClick = { onEdit(task) }) { Text("编辑") }
        TextButton(onClick = { onDelete(task) }) { Text("删除") }
    }
}

