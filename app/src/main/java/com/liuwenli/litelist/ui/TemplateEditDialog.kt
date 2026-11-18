package com.liuwenli.litelist.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.liuwenli.litelist.data.RepeatTemplateEntity
import com.liuwenli.litelist.data.StaleTemplateException
import kotlinx.coroutines.launch

@Composable
fun TemplateEditDialog(
    template: RepeatTemplateEntity,
    onDismiss: () -> Unit,
    onSave: suspend (String, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var title by remember(template.templateId) { mutableStateOf(template.title) }
    var note by remember(template.templateId) { mutableStateOf(template.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var stale by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("编辑每日计划", style = MaterialTheme.typography.headlineSmall)
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
                    label = { Text("备注") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("更新将应用到未来未单独编辑的实例", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (stale) TextButton(onClick = onDismiss) { Text("关闭并重新加载") }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
                    Button(onClick = {
                        scope.launch {
                            saving = true
                            runCatching { onSave(title, if (note.isEmpty() && template.note == null) null else note) }
                                .onSuccess { onDismiss() }
                                .onFailure { error = it.message ?: "保存失败"; stale = it is StaleTemplateException }
                            saving = false
                        }
                    }, enabled = !saving) { Text(if (saving) "保存中" else "保存") }
                }
            }
        }
    }
}

