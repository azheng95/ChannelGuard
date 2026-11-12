package com.xfxo.channelguard.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xfxo.channelguard.data.RuleRepository
import com.xfxo.channelguard.data.Subscription
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { RuleRepository() }
    val scope = rememberCoroutineScope()
    var subscriptions by remember { mutableStateOf(repository.getSubscriptions()) }
    var url by remember { mutableStateOf("") }
    var busyId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Subscription?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var enabledOnly by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val result = runCatching {
                val text = context.contentResolver
                    .openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("无法读取文件")
                repository.addSubscriptionFromText(text)
            }.getOrElse { RuleRepository.ImportResult.Failure(it.message ?: "读取失败") }
            when (result) {
                is RuleRepository.ImportResult.Success -> {
                    subscriptions = repository.getSubscriptions()
                    message = "已导入 ${result.subscription.name}"
                }
                is RuleRepository.ImportResult.Failure -> message = "导入失败：${result.message}"
            }
        }
    }

    val visibleSubscriptions = subscriptions.filter { !enabledOnly || it.enabled }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("订阅") },
                actions = {
                    IconButton(
                        enabled = busyId == null && subscriptions.any { it.updateUrl.isNotBlank() },
                        onClick = {
                            scope.launch {
                                busyId = "all"
                                val updatable = subscriptions.filter { it.updateUrl.isNotBlank() }
                                var failures = 0
                                updatable.forEach { subscription ->
                                    if (repository.refreshSubscription(subscription) is RuleRepository.ImportResult.Failure) failures++
                                }
                                subscriptions = repository.getSubscriptions()
                                busyId = null
                                message = if (failures == 0) "订阅已更新" else "$failures 个订阅更新失败"
                            }
                        }
                    ) { Icon(Icons.Default.Refresh, contentDescription = "更新所有订阅") }
                    IconButton(onClick = { enabledOnly = !enabledOnly }) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = if (enabledOnly) "显示全部订阅" else "仅显示已启用订阅",
                            tint = if (enabledOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加订阅")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "${subscriptions.size} 个订阅 · ${subscriptions.filter { it.enabled }.sumOf { it.ruleCount }} 条启用规则",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            message?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("添加失败") || it.startsWith("更新失败")) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            if (visibleSubscriptions.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (subscriptions.isEmpty()) "还没有订阅" else "没有启用的订阅", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "添加订阅地址后，规则会按应用和通知频道自动匹配。",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)
                ) {
                    items(visibleSubscriptions, key = { it.id }) { subscription ->
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(subscription.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (subscription.ruleCount == 0) "暂无规则" else "${subscription.ruleCount} 条规则 · v${subscription.version}",
                                        modifier = Modifier.padding(top = 4.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        (subscription.author.ifBlank { "ChannelGuard" }) +
                                            if (subscription.updatedAt.isBlank()) "" else " · ${subscription.updatedAt}",
                                        modifier = Modifier.padding(top = 5.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = subscription.enabled,
                                    onCheckedChange = { enabled ->
                                        repository.setSubscriptionEnabled(subscription.id, enabled)
                                        subscriptions = repository.getSubscriptions()
                                    }
                                )
                            }
                            if (subscription.updateUrl.isNotBlank()) {
                                Text(
                                    subscription.updateUrl,
                                    modifier = Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (subscription.updateUrl.isNotBlank()) {
                                    IconButton(
                                        enabled = busyId == null,
                                        onClick = {
                                            busyId = subscription.id
                                            scope.launch {
                                                val result = repository.refreshSubscription(subscription)
                                                busyId = null
                                                subscriptions = repository.getSubscriptions()
                                                message = when (result) {
                                                    is RuleRepository.ImportResult.Success -> "已更新 ${result.subscription.name}"
                                                    is RuleRepository.ImportResult.Failure -> "更新失败：${result.message}"
                                                }
                                            }
                                        }
                                    ) { Icon(Icons.Default.Refresh, contentDescription = "更新 ${subscription.name}") }
                                }
                                IconButton(onClick = { pendingDelete = subscription }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除 ${subscription.name}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { subscription ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除订阅？") },
            text = { Text("${subscription.name} 的规则将不再参与匹配。") },
            confirmButton = {
                TextButton(onClick = {
                    repository.removeSubscription(subscription.id)
                    subscriptions = repository.getSubscriptions()
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加订阅") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("订阅地址") },
                        placeholder = { Text("https://example.com/subscription.json") }
                    )
                    TextButton(
                        onClick = {
                            showAddDialog = false
                            filePicker.launch(arrayOf("application/json", "text/*"))
                        }
                    ) { Text("从本地文件导入 JSON") }
                    if (message?.startsWith("添加失败") == true) {
                        Text(
                            message.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = url.isNotBlank() && busyId == null,
                    onClick = {
                        busyId = "add"
                        message = null
                        scope.launch {
                            val result = repository.addSubscriptionFromUrl(url.trim())
                            busyId = null
                            when (result) {
                                is RuleRepository.ImportResult.Success -> {
                                    subscriptions = repository.getSubscriptions()
                                    url = ""
                                    message = "已添加 ${result.subscription.name}"
                                    showAddDialog = false
                                }
                                is RuleRepository.ImportResult.Failure -> message = "添加失败：${result.message}"
                            }
                        }
                    }
                ) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
        )
    }
}
