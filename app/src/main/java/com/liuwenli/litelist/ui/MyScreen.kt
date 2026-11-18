package com.liuwenli.litelist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.liuwenli.litelist.data.TaskEntity
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun MyScreen(
    tasks: List<TaskEntity>,
    today: LocalDate,
    themeMode: String,
    onThemeChange: suspend (String) -> Unit,
) {
    val stats = TaskStatistics.from(tasks, today)
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("统计", style = MaterialTheme.typography.titleLarge)
        Text(stats.completionPercent?.let { "总体完成率 $it%（${stats.eligibleCompleted}/${stats.eligibleTotal}）" } ?: "总体完成率：暂无统计数据")
        Text("累计完成数 ${stats.totalCompleted}")
        Text("主题", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("SYSTEM" to "跟随系统", "LIGHT" to "浅色", "DARK" to "深色").forEach { (mode, label) ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = {
                        scope.launch {
                            runCatching { onThemeChange(mode) }
                                .onSuccess { error = null }
                                .onFailure { error = it.message ?: "切换失败" }
                        }
                    },
                    label = { Text(label) },
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

