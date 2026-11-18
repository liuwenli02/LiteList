package com.liuwenli.litelist.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch

@Composable
fun RepeatPlanDialog(
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: suspend (String, String?, LocalDate?, LocalDate?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("创建每日重复计划", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    label = { Text("计划标题") },
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
                DateBoundRow("开始日期", startDate, today, onSelect = { startDate = it })
                DateBoundRow("结束日期", endDate, startDate ?: today, onSelect = { endDate = it })
                Text("仅生成从今天起七天内符合日期范围的实例", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
                    Button(onClick = {
                        scope.launch {
                            saving = true
                            runCatching { onSave(title, note, startDate, endDate) }
                                .onSuccess { onDismiss() }
                                .onFailure { error = it.message ?: "保存失败" }
                            saving = false
                        }
                    }, enabled = !saving) { Text(if (saving) "保存中" else "保存") }
                }
            }
        }
    }
}

@Composable
private fun DateBoundRow(label: String, date: LocalDate?, pickerDefault: LocalDate, onSelect: (LocalDate?) -> Unit) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = {
            val initial = date ?: pickerDefault
            DatePickerDialog(context, { _, year, month, day ->
                onSelect(LocalDate.of(year, month + 1, day))
            }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
        }) { Text("$label：${date ?: "不限制"}") }
        if (date != null) TextButton(onClick = { onSelect(null) }) { Text("清除") }
    }
}

