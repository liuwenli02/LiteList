package com.liuwenli.litelist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import com.liuwenli.litelist.data.RepeatTemplateEntity
import com.liuwenli.litelist.data.TaskEntity
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun MyScreen(
    modifier: Modifier = Modifier,
    tasks: List<TaskEntity>,
    templates: List<RepeatTemplateEntity>,
    today: LocalDate,
    themeMode: String,
    onThemeChange: suspend (String) -> Unit,
    onEditTemplate: (RepeatTemplateEntity) -> Unit,
    onChangeTemplateStatus: suspend (RepeatTemplateEntity) -> Unit,
    onTerminalTemplate: suspend (RepeatTemplateEntity, Boolean) -> Unit,
) {
    val stats = TaskStatistics.from(tasks, today)
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var pendingTerminal by remember { mutableStateOf<Pair<RepeatTemplateEntity, Boolean>?>(null) }

    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        Text("每日重复计划", style = MaterialTheme.typography.titleLarge)
        if (templates.isEmpty()) Text("暂无重复计划")
        templates.forEach { template ->
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(template.title)
                        Text("${template.startDate ?: "不限"} ～ ${template.endDate ?: "不限"}", style = MaterialTheme.typography.bodySmall)
                        Text(when (template.status) {
                            "PAUSED" -> "已暂停"
                            "ENDED" -> "已停止"
                            else -> "进行中"
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                    if (template.status != "ENDED") TextButton(onClick = { onEditTemplate(template) }) { Text("编辑") }
                }
                if (template.status != "ENDED") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { onChangeTemplateStatus(template) }
                                    .onSuccess { error = null }
                                    .onFailure { error = it.message ?: "操作失败" }
                            }
                        }) { Text(if (template.status == "PAUSED") "恢复" else "暂停") }
                        TextButton(onClick = { pendingTerminal = template to false }) { Text("停止") }
                        TextButton(onClick = { pendingTerminal = template to true }) { Text("删除计划") }
                    }
                }
                HorizontalDivider()
            }
        }
    }
    pendingTerminal?.let { (template, deletePlan) ->
        AlertDialog(
            onDismissRequest = { pendingTerminal = null },
            title = { Text(if (deletePlan) "删除重复计划？" else "停止重复计划？") },
            text = { Text("${template.title}。此操作不可撤销，已有符合保留条件的任务仍会保留。") },
            confirmButton = {
                Button(onClick = {
                    pendingTerminal = null
                    scope.launch {
                        runCatching { onTerminalTemplate(template, deletePlan) }
                            .onSuccess { error = null }
                            .onFailure { error = it.message ?: "操作失败" }
                    }
                }) { Text(if (deletePlan) "删除计划" else "停止") }
            },
            dismissButton = { TextButton(onClick = { pendingTerminal = null }) { Text("取消") } },
        )
    }
}

