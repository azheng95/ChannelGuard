package com.xfxo.channelguard.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.xfxo.channelguard.data.RuleApplier
import com.xfxo.channelguard.data.RuleRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { RuleRepository() }
    val scope = rememberCoroutineScope()
    var automaticEnabled by remember { mutableStateOf(repository.isAutomaticEnabled()) }
    var applying by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("设置", fontWeight = FontWeight.SemiBold) }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
                    Text("自动匹配", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 16.dp)) {
                            Text("应用启用的订阅规则", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "手动频道设置始终优先；关闭后只保留手动控制。",
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = automaticEnabled,
                            onCheckedChange = {
                                automaticEnabled = it
                                repository.setAutomaticEnabled(it)
                            }
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
                    Text("应用现有频道", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "扫描系统已记录的通知频道，并应用当前手动设置和订阅匹配结果。",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    progress?.let { (current, total) ->
                        LinearProgressIndicator(
                            progress = { if (total == 0) 0f else current.toFloat() / total },
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                        )
                        Text(
                            "$current / $total",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Button(
                        onClick = {
                            applying = true
                            result = null
                            scope.launch {
                                val applied = RuleApplier.applyRulesToAllApps(context, repository) { current, total, _ ->
                                    progress = current to total
                                }
                                progress = null
                                applying = false
                                result = "扫描 ${applied.totalApps} 个应用、${applied.totalChannels} 个频道；尝试处理 ${applied.attemptedChannels} 个频道，失败 ${applied.errorCount} 项"
                            }
                        },
                        enabled = !applying,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) { Text(if (applying) "正在扫描" else "立即扫描并应用") }
                    result?.let {
                        Text(it, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
                    Text("匹配优先级", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "单频道手动设置优先于订阅自动匹配。订阅命中按列表顺序取第一个匹配项；每个结果都可在应用详情中查看命中来源。",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
