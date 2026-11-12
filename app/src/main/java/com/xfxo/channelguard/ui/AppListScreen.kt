package com.xfxo.channelguard.ui

import android.content.pm.ApplicationInfo
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import com.xfxo.channelguard.R
import com.xfxo.channelguard.data.AppFilterPreferences
import com.xfxo.channelguard.data.AppGroupFilter
import com.xfxo.channelguard.data.ChannelMatcher
import com.xfxo.channelguard.data.ChannelStore
import com.xfxo.channelguard.data.RuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledApp(
    val packageName: String,
    val label: String,
    val appInfo: ApplicationInfo,
    val channelCount: Int = 0,
    val blockedChannelCount: Int = 0
)

private enum class AppSort(val title: String) {
    NAME("按应用名称"),
    PACKAGE("按包名"),
    CHANNEL_COUNT("按频道数量"),
    BLOCKED_COUNT("按拦截频道数")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(navController: NavController) {
    val context = LocalContext.current
    val iconSizePx = with(LocalDensity.current) { 48.dp.roundToPx() }
    val iconCache = remember { LruCache<String, ImageBitmap>(64) }
    val packageManager = context.packageManager
    AppFilterPreferences.initialize(context)
    var query by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var appGroupFilter by remember { mutableStateOf(AppFilterPreferences.readAppGroup()) }
    var onlyBlockedApps by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(AppSort.NAME) }
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
    val appRowsState = produceState<List<InstalledApp>?>(null, applications, refreshKey) {
        value = applications?.let { installed ->
            withContext(Dispatchers.IO) {
                val store = ChannelStore()
                val repository = RuleRepository()
                val manualRules = repository.getAllRules()
                val subscriptions = repository.getSubscriptions()
                val automaticEnabled = repository.isAutomaticEnabled()
                installed.map { app ->
                    val channels = store.get(app.packageName)
                    val blocked = channels.count { channel ->
                        val key = "${app.packageName}/${channel.channelId}"
                        when {
                            manualRules.containsKey(key) -> manualRules[key] == true
                            automaticEnabled -> ChannelMatcher.match(
                                app.packageName, channel.name, channel.channelId, subscriptions
                            ) != null
                            else -> false
                        }
                    }
                    app.copy(channelCount = channels.size, blockedChannelCount = blocked)
                }
            }
        }
    }
    val appRows = appRowsState.value
    val filtered = appRows.orEmpty().filter {
        val isSystem = it.appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        (if (isSystem) appGroupFilter == AppGroupFilter.SYSTEM else appGroupFilter == AppGroupFilter.USER) &&
            (!onlyBlockedApps || it.blockedChannelCount > 0) &&
            (it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true))
    }.let { rows ->
        when (sort) {
            AppSort.NAME -> rows.sortedBy { it.label.lowercase() }
            AppSort.PACKAGE -> rows.sortedBy { it.packageName.lowercase() }
            AppSort.CHANNEL_COUNT -> rows.sortedByDescending { it.channelCount }
            AppSort.BLOCKED_COUNT -> rows.sortedByDescending { it.blockedChannelCount }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("应用")
                    }
                },
                actions = {
                    IconButton(onClick = { searchVisible = !searchVisible; if (!searchVisible) query = "" }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索应用")
                    }
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "筛选和排序",
                                tint = if (showFilterMenu) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            MenuSectionTitle("排序")
                            AppSort.entries.forEach { option ->
                                MenuRadioRow(option.title, sort == option) { sort = option }
                            }
                            MenuSectionTitle("分组")
                            MenuCheckboxRow(
                                "系统应用",
                                appGroupFilter == AppGroupFilter.SYSTEM,
                                enabled = appGroupFilter != AppGroupFilter.SYSTEM
                            ) {
                                appGroupFilter = appGroupFilter.select(AppGroupFilter.SYSTEM)
                                AppFilterPreferences.writeAppGroup(appGroupFilter)
                            }
                            MenuCheckboxRow(
                                "用户应用",
                                appGroupFilter == AppGroupFilter.USER,
                                enabled = appGroupFilter != AppGroupFilter.USER
                            ) {
                                appGroupFilter = appGroupFilter.select(AppGroupFilter.USER)
                                AppFilterPreferences.writeAppGroup(appGroupFilter)
                            }
                            MenuSectionTitle("显示")
                            MenuCheckboxRow("仅显示有拦截频道的应用", onlyBlockedApps) {
                                onlyBlockedApps = it
                            }
                        }
                    }
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
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
            if (searchVisible) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
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
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        query.isNotBlank() -> "搜索结果"
                        onlyBlockedApps -> "已拦截应用"
                        appGroupFilter == AppGroupFilter.SYSTEM -> "系统应用"
                        else -> "用户应用"
                    },
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

            if (applications == null || appRows == null) {
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
                                .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                    buildString {
                                        append(app.packageName)
                                        append(" · ${app.channelCount} 个频道")
                                        if (app.blockedChannelCount > 0) append(" · 拦截 ${app.blockedChannelCount}")
                                    },
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
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 78.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, end = 24.dp, top = 14.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun MenuRadioRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun MenuCheckboxRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
