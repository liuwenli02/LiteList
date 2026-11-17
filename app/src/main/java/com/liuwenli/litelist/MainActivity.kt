package com.liuwenli.litelist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { LiteListApp() } }
    }
}

@Composable
private fun LiteListApp() {
    val tabs = listOf("今日", "日历", "我的")
    var selected by remember { mutableIntStateOf(0) }
    Scaffold(bottomBar = {
        NavigationBar {
            tabs.forEachIndexed { index, title ->
                NavigationBarItem(
                    selected = selected == index,
                    onClick = { selected = index },
                    icon = { Text(title.take(1)) },
                    label = { Text(title) },
                )
            }
        }
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(tabs[selected], style = MaterialTheme.typography.headlineMedium)
            Text("功能开发中")
        }
    }
}
