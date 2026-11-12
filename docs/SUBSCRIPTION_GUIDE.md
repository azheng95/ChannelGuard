# 订阅规则配置教程

本文说明 ChannelGuard 当前支持的订阅 JSON 结构和匹配行为。订阅规则只会在订阅、分组、规则均启用，并且“设置”中的自动匹配已开启时参与自动拦截。

## 1. 导入订阅

订阅可以通过两种方式添加：

- 在应用底部进入“订阅”，输入订阅 JSON 的 URL。
- 在“添加订阅”对话框中选择“从本地文件导入 JSON”。

URL 订阅可以在订阅列表中手动更新，也可以使用页面顶部的更新按钮批量更新。若更新文件中的订阅 `id` 与现有订阅相同，应用会更新规则内容并保留现有订阅的启用状态。

## 2. 完整结构

建议使用“订阅 → 分组 → 规则”的结构。以下示例可以保存为 JSON 文件后导入：

```json
{
  "id": "channelguard-marketing",
  "name": "营销推广频道",
  "author": "Example Author",
  "version": 1,
  "updateUrl": "",
  "updatedAt": "2026-11-12",
  "enabled": true,
  "groups": [
    {
      "key": "marketing",
      "name": "营销与促销",
      "description": "匹配通知频道中的营销推广内容",
      "enabled": true,
      "rules": [
        {
          "key": "all-app-marketing",
          "name": "所有应用的营销频道",
          "packages": ["*"],
          "channelNames": ["营销", "推广", "促销", "广告"],
          "channelIds": [],
          "excludeKeywords": ["订单状态", "支付成功"],
          "matchAll": false,
          "enabled": true
        }
      ]
    }
  ]
}
```

顶层必需有 `groups` 或兼容格式 `rules`，并且至少包含一个可解析的分组。每条规则必须有 `key` 或兼容字段 `id`。其他字段可按需省略。

## 3. 字段说明

### 订阅字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | 字符串 | 订阅唯一标识。更新订阅时用于识别已有订阅。若省略，应用会根据订阅名称或 URL 生成标识；建议显式填写并保持稳定。 |
| `name` | 字符串 | 订阅显示名称。 |
| `author` | 字符串 | 作者或来源说明，可省略。 |
| `version` | 整数 | 订阅版本号，默认 `1`。 |
| `updateUrl` | 字符串 | 更新地址。通过 URL 添加时会使用实际订阅地址。 |
| `updatedAt` | 字符串 | 更新时间或版本备注，仅用于显示。 |
| `enabled` | 布尔值 | 是否启用整个订阅，默认 `true`。 |
| `groups` | 数组 | 分组列表，推荐使用。 |

### 分组字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `key` | 字符串 | 分组标识；兼容 `id`。省略时使用 `"group"`。 |
| `name` | 字符串 | 分组显示名称；省略时使用 `key`。 |
| `description` | 字符串 | 分组说明；兼容 `desc`。 |
| `enabled` | 布尔值 | 是否参与匹配，默认 `true`。 |
| `rules` | 数组 | 该分组下的规则列表。 |

### 规则字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `key` | 字符串 | 规则标识；兼容 `id`。每条规则都必须有其中一个。 |
| `name` | 字符串 | 规则显示名称，省略时使用 `key`。 |
| `packages` | 字符串数组 | 应用包名范围。支持精确包名、`*` 和包名前缀通配符；省略或空数组表示不限制应用范围。兼容别名 `apps`、`appPackages`。 |
| `channelNames` | 字符串数组 | 频道名称关键词。兼容别名 `nameKeywords`。 |
| `channelIds` | 字符串数组 | 频道 ID 关键词。兼容别名 `idKeywords`。 |
| `excludeKeywords` | 字符串数组 | 排除关键词；只要命中频道名称或 ID，当前规则就不会命中。兼容别名 `exclude`。 |
| `matchAll` | 布尔值 | 当名称和 ID 关键词均为空时，是否匹配应用范围内的全部频道。默认 `false`。 |
| `enabled` | 布尔值 | 是否启用规则，默认 `true`。 |

关键词按**不区分大小写的子串**进行比较。例如 `promo` 可以匹配频道名称 `Promo notifications`。一个规则中的多个频道名称和 ID 条件是“或”关系，任意一项命中即可。

## 4. 应用范围写法

### 只匹配一个应用

```json
"packages": ["com.example.shop"]
```

包名必须完全一致。

### 匹配多个指定应用

```json
"packages": ["com.example.shop", "com.example.store"]
```

数组中的包名是“或”关系，命中任一个应用即可。

### 匹配某个包名前缀

```json
"packages": ["com.example.*"]
```

这会匹配 `com.example.shop`、`com.example.reader` 等以 `com.example.` 开头的包名，不会匹配 `com.examples.app`。

### 匹配所有应用

```json
"packages": ["*"]
```

`*` 会匹配所有应用包名。建议在全局规则中显式写出 `["*"]`，让覆盖范围一目了然。省略 `packages` 或填写空数组也表示不限制应用范围。

## 5. 频道匹配方式

### 只按频道名称匹配

```json
"channelNames": ["营销", "推广", "促销", "广告"]
```

`channelIds` 可以省略或设为空数组。只要频道名称包含任意一个关键词，规则就命中。

### 只按频道 ID 匹配

```json
"channelIds": ["marketing", "promotion"]
```

`channelNames` 可以省略或设为空数组。只要频道 ID 包含任意一个关键词，规则就命中。

### 名称或 ID 任一匹配

```json
"channelNames": ["活动"],
"channelIds": ["campaign"]
```

名称或 ID 任一匹配即可，不要求两者同时命中。

### 排除特定频道

```json
"channelNames": ["订单", "促销"],
"excludeKeywords": ["订单状态", "支付成功"]
```

排除词会同时检查频道名称和频道 ID，并优先于普通匹配条件。例如频道名包含“订单状态”时，即使命中“订单”关键词，这条规则仍然不会命中。

### 匹配应用范围内全部频道

```json
"packages": ["com.example.shop"],
"channelNames": [],
"channelIds": [],
"matchAll": true
```

此配置会匹配该应用的所有频道。将 `packages` 改为 `["*"]` 则会匹配所有应用的所有频道，范围非常宽。只有在确实需要全频道匹配时才设置 `matchAll: true`。

若 `channelNames` 和 `channelIds` 都为空且 `matchAll` 为 `false` 或省略，该规则不会命中任何频道。这是默认行为。

## 6. 规则组合与优先级

- 订阅、分组和规则都必须启用，规则才参与匹配。
- 规则按订阅列表、分组、规则的现有顺序查找；返回第一个命中项作为命中来源。
- 同一规则内，多个应用包名、频道名称关键词、频道 ID 关键词分别是“任意一项匹配”；频道名称与频道 ID 两组之间也是“任一命中”。
- 排除关键词优先于该规则的频道条件。
- 单个频道的手动设置优先于订阅结果。手动拦截会拦截该频道；手动放行会阻止 ChannelGuard 的订阅规则再次拦截该频道。
- 手动放行只控制 ChannelGuard 的规则，不保证恢复已被 Android 或用户关闭的系统频道。
- “设置”中的自动匹配关闭后，所有订阅规则暂不参与自动拦截；手动频道设置仍然有效。
- 新建通知频道时会自动判断。已经存在的频道可在“设置”中点“立即扫描并应用”。

## 7. 多组规则示例

同一订阅可以将不同应用或频道类型拆分为多条规则，方便维护命中来源：

```json
{
  "id": "example-notification-rules",
  "name": "示例通知规则",
  "groups": [
    {
      "key": "shopping",
      "name": "购物应用",
      "enabled": true,
      "rules": [
        {
          "key": "shop-marketing",
          "name": "购物促销",
          "packages": ["com.example.shop", "com.example.store"],
          "channelNames": ["促销", "优惠"],
          "excludeKeywords": ["退款进度"],
          "enabled": true
        },
        {
          "key": "shop-marketing-global",
          "name": "其他应用推广",
          "packages": ["*"],
          "channelNames": ["营销", "推广", "广告"],
          "excludeKeywords": [],
          "enabled": true
        }
      ]
    },
    {
      "key": "social",
      "name": "社交应用",
      "enabled": false,
      "rules": [
        {
          "key": "social-live",
          "name": "直播提醒",
          "packages": ["com.example.social.*"],
          "channelIds": ["live_notice"],
          "enabled": true
        }
      ]
    }
  ]
}
```

这个例子中，“社交应用”分组关闭，因此其中的规则不会参与匹配；其他规则仍按顺序生效。

## 8. 顶层简写格式

为兼容简单订阅，解析器也接受把规则数组直接放在顶层的 `rules` 字段中：

```json
{
  "id": "simple-marketing",
  "name": "简易营销规则",
  "rules": [
    {
      "key": "global-promo",
      "name": "全局推广",
      "packages": ["*"],
      "channelNames": ["推广", "促销"],
      "enabled": true
    }
  ]
}
```

应用会把这种写法转换为一个默认分组。新订阅仍建议显式使用 `groups`，以便后续按类型组织和管理规则。

## 9. 常见问题

### 只填写 `channelNames`，不填 `channelIds`，可以吗？

可以。缺失的 `channelIds` 会视为空数组；频道名称命中任一关键词即可。反过来，只使用 `channelIds` 也可以。

### 规则为什么没有匹配？

按以下顺序检查：

1. 订阅、分组、规则是否都启用。
2. “设置”中的自动匹配是否开启。
3. `packages` 是否包含目标应用的真实包名；全局规则可用 `["*"]`。
4. 频道名称或 ID 是否包含关键词。关键词是子串匹配，不是正则表达式。
5. 频道名称和 ID 均为空时，是否忘记设置 `matchAll: true`。
6. 频道名称或 ID 是否命中 `excludeKeywords`。
7. 若是已有频道，是否执行了“立即扫描并应用”。

### 更新订阅后为什么启用状态没变化？

更新时会保留该订阅原有的启用状态。规则内容来自更新后的 JSON，但是否启用仍由应用内订阅列表的开关控制。

### 如何避免全局规则误拦截？

优先限定 `packages`，使用足够具体的频道关键词，并为容易误命中的重要频道添加 `excludeKeywords`。不要在没有充分确认时同时使用 `packages: ["*"]` 和 `matchAll: true`。
