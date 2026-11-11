package com.xfxo.channelguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { RuleRepository() }
    val scope = rememberCoroutineScope()

    var smartModeEnabled by remember { mutableStateOf(repository.isSmartModeEnabled()) }
    var syncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var applying by remember { mutableStateOf(false) }
    var applyMessage by remember { mutableStateOf<String?>(null) }
    var applyProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var cloudMeta by remember { mutableStateOf(repository.getCloudMeta()) }

    LaunchedEffect(Unit) {
        repository.initSmartRules(context)
        smartModeEnabled = repository.isSmartModeEnabled()
        cloudMeta = repository.getCloudMeta()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("设置", fontWeight = FontWeight.SemiBold)
                        Text(
                            "规则与自动处理",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            item {
                SettingHeading("智能模式", "自动识别并拦截匹配营销关键词的通知频道")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("自动拦截匹配频道", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            if (smartModeEnabled) "新发现的匹配频道将自动关闭" else "仅使用你手动设置的频道规则",
                            modifier = Modifier.padding(top = 3.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = smartModeEnabled,
                        onCheckedChange = { enabled ->
                            smartModeEnabled = enabled
                            repository.setSmartModeEnabled(context, enabled)
                        }
                    )
                }
                Divider(Modifier.padding(horizontal = 20.dp))
            }

            item {
                SettingHeading("规则库", "关键词规则用于识别运营、推广和营销频道")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("当前规则", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${cloudMeta.ruleCount} 条 · ${if (cloudMeta.fromBuiltin) "内置" else "云端"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!cloudMeta.fromBuiltin) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("最近同步", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            formatTime(cloudMeta.lastSyncTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = {
                        syncing = true
                        syncMessage = null
                        scope.launch {
                            val result = repository.syncFromCloud(context)
                            syncing = false
                            cloudMeta = repository.getCloudMeta()
                            syncMessage = when (result) {
                                is RuleRepository.SyncResult.Success ->
                                    "同步完成 · ${result.count} 条规则 · v${result.version}"
                                is RuleRepository.SyncResult.Failure -> "同步失败 · ${result.message}"
                            }
                        }
                    },
                    enabled = !syncing,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    if (syncing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (syncing) "正在同步" else "同步规则库")
                }
                syncMessage?.let { MessageLine(it, isError = it.startsWith("同步失败")) }
                Divider(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp))
            }

            item {
                SettingHeading("立即应用", "将当前规则重新应用到设备上已存在的通知频道")
                applyProgress?.let { (current, total) ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        LinearProgressIndicator(
                            progress = { if (total == 0) 0f else current.toFloat() / total.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "正在扫描应用 · $current / $total",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = {
                        applying = true
                        applyMessage = null
                        applyProgress = null
                        scope.launch {
                            val result = RuleApplier.applyRulesToAllApps(
                                context,
                                repository,
                                onProgress = { current, total, _ -> applyProgress = current to total }
                            )
                            applying = false
                            applyProgress = null
                            applyMessage = "已扫描 ${result.totalApps} 个应用、${result.totalChannels} 个频道，拦截 ${result.appliedChannels} 个"
                        }
                    },
                    enabled = !applying && smartModeEnabled,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(if (applying) "正在应用规则" else "立即扫描并应用")
                }
                if (!smartModeEnabled) {
                    Text(
                        "请先开启智能模式",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                applyMessage?.let { MessageLine(it) }
                Divider(Modifier.padding(horizontal = 20.dp).padding(top = 18.dp))
            }

            item {
                SettingHeading("优先级", "手动设置始终优先于智能匹配")
                Text(
                    "手动模式下可在频道详情中逐条控制。智能模式根据关键词处理频道；关闭规则匹配不会覆盖已有的手动设置。",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingHeading(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            subtitle,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MessageLine(message: String, isError: Boolean = false) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    )
}

private fun formatTime(millis: Long): String {
    if (millis == 0L) return "从未同步"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
