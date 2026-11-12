package com.xfxo.channelguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import com.xfxo.channelguard.R
import com.xfxo.channelguard.data.ChannelStore
import com.xfxo.channelguard.data.RuleRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelDetailScreen(pkg: String, navController: NavController) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val channelStore = remember { ChannelStore() }
    val ruleRepository = remember { RuleRepository() }
    val channels by channelStore.observe(pkg).collectAsState()
    val appInfo = remember(pkg) {
        runCatching { packageManager.getApplicationInfo(pkg, 0) }.getOrNull()
    }
    val appName = remember(pkg, appInfo) {
        appInfo?.let { packageManager.getApplicationLabel(it).toString() } ?: pkg
    }
    val appIcon = remember(pkg, appInfo) {
        appInfo?.let { runCatching { packageManager.getApplicationIcon(it) }.getOrNull() }
    }
    val blockedCount = channels.count {
        ruleRepository.isBlocked(pkg, it.channelId) ||
            (!ruleRepository.hasManualRule(pkg, it.channelId) &&
                ruleRepository.isMatchedBySubscription(pkg, it.name, it.channelId))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("通知频道", fontWeight = FontWeight.SemiBold)
                        Text(
                            appName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (channels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有发现通知频道", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "打开 $appName 并触发一次通知后，这里会显示频道。",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (appIcon != null) {
                            androidx.compose.foundation.Image(
                                bitmap = remember(appIcon) { appIcon.toBitmap().asImageBitmap() },
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 14.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .size(48.dp)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                appName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                pkg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChannelStat("全部频道", channels.size.toString(), Modifier.weight(1f))
                        ChannelStat("已拦截", blockedCount.toString(), Modifier.weight(1f))
                        ChannelStat("正常送达", (channels.size - blockedCount).toString(), Modifier.weight(1f))
                    }
                    Text(
                        "频道列表",
                        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                items(channels, key = { it.channelId }) { channel ->
                    var blocked by remember(pkg, channel.channelId) {
                        mutableStateOf(
                            ruleRepository.isBlocked(pkg, channel.channelId) ||
                                (!ruleRepository.hasManualRule(pkg, channel.channelId) &&
                                    ruleRepository.isMatchedBySubscription(pkg, channel.name, channel.channelId))
                        )
                    }
                    val manuallyOverridden = ruleRepository.hasManualRule(pkg, channel.channelId)
                    val match = if (manuallyOverridden) null
                    else ruleRepository.matchChannel(pkg, channel.name, channel.channelId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                channel.name.ifBlank { channel.channelId },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                channel.channelId,
                                modifier = Modifier.padding(top = 3.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                modifier = Modifier.padding(top = 8.dp),
                                color = if (blocked) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    when {
                                        manuallyOverridden && blocked -> "手动拦截"
                                        manuallyOverridden -> "手动放行"
                                        match != null && blocked -> "订阅命中 · ${match.subscriptionName}"
                                        else -> importanceLabel(channel.importance)
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (blocked) MaterialTheme.colorScheme.onErrorContainer
                                    else MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                        Switch(
                            checked = !blocked,
                            onCheckedChange = { enabled ->
                                blocked = !enabled
                                ruleRepository.setBlocked(pkg, channel.channelId, blocked)
                            }
                        )
                    }
                    Divider(
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, modifier = Modifier.padding(top = 3.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun importanceLabel(importance: Int): String {
    val resource = when (importance) {
        0 -> R.string.channel_importance_none
        1 -> R.string.channel_importance_min
        2 -> R.string.channel_importance_low
        3 -> R.string.channel_importance_default
        4, 5 -> R.string.channel_importance_high
        else -> R.string.channel_importance_default
    }
    return stringResource(resource)
}
