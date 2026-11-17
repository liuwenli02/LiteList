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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.liuwenli.litelist.data.LiteListDatabase
import com.liuwenli.litelist.data.TaskEntity
import com.liuwenli.litelist.data.TaskRepository
import java.time.Clock
import java.time.LocalDate
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
    val tasks by remember(repository) { repository.observeTasks() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val today = LocalDate.now().toString()

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
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    val current = tasks.filter { it.plannedDate == today }
                    Text("今日 ${current.count { it.status == "COMPLETED" }}/${current.size}")
                    if (current.isEmpty()) Text("今天暂无任务")
                    TaskList(current, onToggle = { task ->
                        scope.launch {
                            runCatching {
                                if (task.status == "PENDING") repository.complete(task.generationId, task.taskId, task.dataVersion)
                                else repository.reopen(task.generationId, task.taskId, task.dataVersion)
                            }.onFailure { error = it.message ?: "操作失败" }
                        }
                    }, onDelete = { pendingDelete = it })
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
}

@Composable
private fun TaskList(tasks: List<TaskEntity>, onToggle: (TaskEntity) -> Unit, onDelete: (TaskEntity) -> Unit) {
    LazyColumn {
        items(tasks, key = { it.taskId }) { task ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = task.status == "COMPLETED", onCheckedChange = { onToggle(task) })
                Text(task.title, modifier = Modifier.weight(1f).padding(top = 12.dp))
                Button(onClick = { onDelete(task) }) { Text("删除") }
            }
        }
    }
}
