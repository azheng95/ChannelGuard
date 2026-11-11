package com.xfxo.channelguard.ui

import android.content.pm.ApplicationInfo
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import com.xfxo.channelguard.R
import com.xfxo.channelguard.data.RuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledApp(
    val packageName: String,
    val label: String,
    val appInfo: ApplicationInfo
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(navController: NavController) {
    val context = LocalContext.current
    val iconSizePx = with(LocalDensity.current) { 48.dp.roundToPx() }
    val iconCache = remember { LruCache<String, ImageBitmap>(64) }
    val packageManager = context.packageManager
    var query by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }
    val applicationsState = produceState<List<InstalledApp>?>(initialValue = null, refreshKey) {
        value = withContext(Dispatchers.IO) {
            packageManager.getInstalledApplications(0).map { app ->
                InstalledApp(
                    packageName = app.packageName,
                    label = packageManager.getApplicationLabel(app).toString(),
                    appInfo = app
                )
            }.sortedBy { it.label.lowercase() }
        }
    }
    val applications = applicationsState.value
    val filtered = applications.orEmpty().filter {
        it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
    }
    val blockedCount = remember(refreshKey) {
        RuleRepository().getAllRules().count { it.value }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("通知管理", fontWeight = FontWeight.SemiBold)
                        Text(
                            "按应用管理通知频道",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryTile(
                    label = "已安装应用",
                    value = applications?.size?.toString() ?: "—",
                    modifier = Modifier.weight(1f)
                )
                SummaryTile(
                    label = "手动拦截",
                    value = blockedCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("搜索应用名称或包名") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Text("清除", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (query.isBlank()) "所有应用" else "搜索结果",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${filtered.size} 个",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (applications == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在读取应用列表", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (query.isBlank()) "未找到应用" else "没有匹配的应用",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (query.isBlank()) "刷新应用列表后再试" else "检查应用名称或包名",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        val cacheKey = "$refreshKey:${app.packageName}"
                        val iconBitmap by produceState<ImageBitmap?>(
                            initialValue = iconCache[cacheKey],
                            cacheKey,
                            iconSizePx
                        ) {
                            if (value == null) {
                                value = withContext(Dispatchers.IO) {
                                    runCatching {
                                        packageManager.getApplicationIcon(app.appInfo)
                                            .toBitmap(iconSizePx, iconSizePx)
                                            .asImageBitmap()
                                    }.getOrNull()?.also { iconCache.put(cacheKey, it) }
                                }
                            }
                        }
                        val loadedIcon = iconBitmap
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate("channelDetail/${app.packageName}") }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (loadedIcon != null) {
                                Image(
                                    bitmap = loadedIcon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            app.label.take(1).uppercase(),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 14.dp, end = 8.dp)
                            ) {
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    app.packageName,
                                    modifier = Modifier.padding(top = 3.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                "›",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Divider(
                            modifier = Modifier.padding(start = 82.dp, end = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                value,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
