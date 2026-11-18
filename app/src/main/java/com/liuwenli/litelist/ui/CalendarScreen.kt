package com.liuwenli.litelist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.liuwenli.litelist.TaskRow
import com.liuwenli.litelist.data.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.delay

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    tasks: List<TaskEntity>,
    today: LocalDate,
    onCreate: (LocalDate) -> Unit,
    onToggle: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onEdit: (TaskEntity) -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDate by remember { mutableStateOf(today) }
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            delay(60_000)
            value = Instant.now()
        }
    }
    val zone = ZoneId.systemDefault()
    val selectedTasks = CalendarTasks.forDate(tasks, selectedDate, zone)
    val firstOffset = month.atDay(1).dayOfWeek.value - 1

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
            Text("${month.year} 年 ${month.monthValue} 月", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(day) }
            }
        }
        repeat(6) { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { weekday ->
                    val dayNumber = week * 7 + weekday - firstOffset + 1
                    val date = if (dayNumber in 1..month.lengthOfMonth()) month.atDay(dayNumber) else null
                    Box(modifier = Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                        date?.let {
                            val marker = CalendarTasks.marker(tasks, it, zone, now)
                            Column(
                                modifier = Modifier.fillMaxSize().clickable { selectedDate = it }.padding(2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                val selected = it == selectedDate
                                Box(
                                    modifier = Modifier.size(30.dp).background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        CircleShape,
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) { Text(dayNumber.toString()) }
                                Box(modifier = Modifier.size(6.dp).background(
                                    when (marker) {
                                        DateMarker.OVERDUE -> MaterialTheme.colorScheme.error
                                        DateMarker.PENDING -> MaterialTheme.colorScheme.primary
                                        DateMarker.COMPLETED -> MaterialTheme.colorScheme.tertiary
                                        null -> Color.Transparent
                                    }, CircleShape,
                                ))
                            }
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(selectedDate.toString(), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onCreate(selectedDate) }) { Text("在此日创建") }
        }
        if (selectedTasks.isEmpty()) Text("该日期暂无任务")
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(selectedTasks, key = { it.taskId }) { task -> TaskRow(task, onToggle, onDelete, onEdit) }
        }
    }
}

