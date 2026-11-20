package com.liuwenli.litelist

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.liuwenli.litelist.data.LiteListDatabase
import com.liuwenli.litelist.data.TaskEntity
import com.liuwenli.litelist.data.RepeatTemplateEntity
import com.liuwenli.litelist.data.TaskRepository
import com.liuwenli.litelist.data.ReminderRepository
import com.liuwenli.litelist.reminder.ReminderCoordinator
import com.liuwenli.litelist.ui.TaskEditorDialog
import com.liuwenli.litelist.ui.CalendarScreen
import com.liuwenli.litelist.ui.MyScreen
import com.liuwenli.litelist.ui.TodayTaskSections
import com.liuwenli.litelist.ui.RepeatPlanDialog
import com.liuwenli.litelist.ui.TemplateEditDialog
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class MainActivity : ComponentActivity() {
    private val database by lazy { LiteListDatabase.open(applicationContext) }
    private val repository by lazy {
        TaskRepository(database, Clock.systemDefaultZone())
    }
    private val reminderRepository by lazy { ReminderRepository(database, Clock.systemDefaultZone()) }
    private val reminderCoordinator by lazy { ReminderCoordinator(applicationContext, reminderRepository, repository) }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        lifecycleScope.launch { runCatching { reminderCoordinator.reconcile() } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by remember(repository) { repository.observeTheme() }.collectAsState(initial = "SYSTEM")
            val dark = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                LiteListApp(repository, themeMode)
            }
        }
        lifecycleScope.launch {
            reminderRepository.observeRevision().collect {
                val hasFutureReminders = repository.futureReminderCandidates(ZoneId.systemDefault()).isNotEmpty()
                if (hasFutureReminders && shouldAskNotificationPermission()) {
                    getSharedPreferences("reminder_permissions", MODE_PRIVATE).edit().putBoolean("notification_asked", true).apply()
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    runCatching { reminderCoordinator.reconcile() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { runCatching { reminderCoordinator.reconcile() } }
    }

    override fun onDestroy() {
        super.onDestroy()
        database.close()
    }

    private fun shouldAskNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getSharedPreferences("reminder_permissions", MODE_PRIVATE).getBoolean("notification_asked", false)
}

@Composable
private fun LiteListApp(repository: TaskRepository, themeMode: String) {
    val tabs = listOf("今日", "日历", "我的")
    var selected by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<TaskEntity?>(null) }
    var pendingEdit by remember { mutableStateOf<TaskEntity?>(null) }
    var pendingTemplateEdit by remember { mutableStateOf<RepeatTemplateEntity?>(null) }
    var detailedCreateDate by remember { mutableStateOf<LocalDate?>(null) }
    var showRepeatCreate by remember { mutableStateOf(false) }
    var completedExpanded by remember { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf(true) }
    val tasks by remember(repository) { repository.observeTasks() }.collectAsState(initial = emptyList())
    val templates by remember(repository) { repository.observeTemplates() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val today by produceState(initialValue = LocalDate.now()) {
        while (true) {
            value = LocalDate.now()
            val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
            delay(Duration.between(ZonedDateTime.now(), nextMidnight).toMillis().coerceAtLeast(1_000L))
        }
    }
    val sections = TodayTaskSections.from(tasks, today, ZoneId.systemDefault())

    LaunchedEffect(repository, today) {
        runCatching { repository.maintainDailyWindow() }
            .onFailure { error = it.message ?: "重复任务生成失败" }
    }

    fun toggle(task: TaskEntity) {
        scope.launch {
            runCatching {
                if (task.status == "PENDING") repository.complete(task.generationId, task.taskId, task.dataVersion)
                else repository.reopen(task.generationId, task.taskId, task.dataVersion)
            }.onFailure { error = it.message ?: "操作失败" }
        }
    }

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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { detailedCreateDate = today }) { Text("详细创建") }
                        TextButton(onClick = { showRepeatCreate = true }) { Text("每日重复计划") }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text("今日 ${sections.progressDone}/${sections.progressTotal}")
                    TodayTaskList(
                        modifier = Modifier.weight(1f),
                        sections = sections,
                        completedExpanded = completedExpanded,
                        historyExpanded = historyExpanded,
                        onCompletedExpandedChange = { completedExpanded = it },
                        onHistoryExpandedChange = { historyExpanded = it },
                        onToggle = ::toggle,
                        onDelete = { pendingDelete = it },
                        onEdit = { pendingEdit = it },
                    )
                }
                1 -> CalendarScreen(
                    modifier = Modifier.weight(1f),
                    tasks = tasks,
                    today = today,
                    onCreate = { detailedCreateDate = it },
                    onToggle = ::toggle,
                    onDelete = { pendingDelete = it },
                    onEdit = { pendingEdit = it },
                )
                else -> MyScreen(
                    modifier = Modifier.weight(1f),
                    tasks = tasks,
                    templates = templates,
                    today = today,
                    themeMode = themeMode,
                    onThemeChange = { repository.setTheme(it) },
                    onEditTemplate = { pendingTemplateEdit = it },
                    onChangeTemplateStatus = { template ->
                        if (template.status == "PAUSED") {
                            repository.resumeDailyTemplate(template.generationId, template.templateId, template.dataVersion)
                        } else {
                            repository.pauseDailyTemplate(template.generationId, template.templateId, template.dataVersion)
                        }
                    },
                    onTerminalTemplate = { template, deletePlan ->
                        repository.endDailyTemplate(template.generationId, template.templateId, template.dataVersion, deletePlan)
                    },
                )
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
    detailedCreateDate?.let { createDate ->
        TaskEditorDialog(
            initialDate = createDate,
            onDismiss = { detailedCreateDate = null },
            onSave = { taskTitle, note, plannedDate, deadline, normalReminders, deadlineReminderEnabled ->
                repository.create(taskTitle, note, plannedDate, deadline, normalReminders, deadlineReminderEnabled)
            },
        )
    }
    if (showRepeatCreate) {
        RepeatPlanDialog(
            today = today,
            onDismiss = { showRepeatCreate = false },
            onSave = { planTitle, note, startDate, endDate ->
                repository.createDailyTemplate(planTitle, note, startDate, endDate)
            },
        )
    }
    pendingEdit?.let { task ->
        TaskEditorDialog(
            initialDate = today,
            existingTask = task,
            onDismiss = { pendingEdit = null },
            loadReminders = { repository.normalReminders(task.generationId, task.taskId) },
            onReenable = { repository.reenableReminders(task.generationId, task.taskId, task.dataVersion) },
            onSave = { taskTitle, note, plannedDate, deadline, normalReminders, deadlineReminderEnabled ->
                repository.edit(task.generationId, task.taskId, task.dataVersion, taskTitle, note, plannedDate, deadline, normalReminders, deadlineReminderEnabled)
            },
        )
    }
    pendingTemplateEdit?.let { template ->
        TemplateEditDialog(
            template = template,
            onDismiss = { pendingTemplateEdit = null },
            onSave = { title, note ->
                repository.editDailyTemplate(template.generationId, template.templateId, template.dataVersion, title, note)
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
internal fun TaskRow(task: TaskEntity, onToggle: (TaskEntity) -> Unit, onDelete: (TaskEntity) -> Unit, onEdit: (TaskEntity) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = task.status == "COMPLETED", onCheckedChange = { onToggle(task) })
        Text(task.title, modifier = Modifier.weight(1f).padding(top = 12.dp))
        TextButton(onClick = { onEdit(task) }) { Text("编辑") }
        TextButton(onClick = { onDelete(task) }) { Text("删除") }
    }
}

