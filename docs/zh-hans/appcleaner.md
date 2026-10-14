---
title: "应用清理（App Cleaner）"
lang: zh-Hans
translation_key: appcleaner
permalink: /zh-hans/appcleaner
source_hash: 505b031f74bf71a2
---
# 应用清理（App Cleaner）
{: #app-cleaner }

应用清理（AppCleaner）工具能够识别各个应用，并查找“可舍弃”的文件。如果一个文件可以删除而不会造成不良影响，并且它要么不是必需的，要么在需要时会被重新创建，那么它就被视为可舍弃的文件。“缓存”中的大多数文件类型都符合这一定义。

## 设置
{: #settings }

### 包含系统应用
{: #include-system-apps }

此设置会将系统应用纳入结果中。默认未启用，因为某些系统应用（取决于 ROM）在缓存被清除后会出现异常。另外，有些系统应用没有“清除缓存”按钮，这可能导致 SD Maid 在通过无障碍服务清除缓存时卡住。

### 包括其他用户
{: #include-other-users }

需要 root。启用后，除了当前运行 SD Maid 的用户之外，SD Maid 还会搜索其他用户的文件夹。

### 包含正在运行的应用
{: #include-running-apps }

是否包含正在运行的应用。

### 最小文件大小
{: #minimum-size }

应用的缓存需要达到多大才会被列出。设置最小大小是在实用性和速度之间的一个良好折中。如果应用的缓存只有几 KB，清除它通常没有什么好处。此设置针对的是某个应用找到的缓存总量（考虑所有已启用的过滤器）。

### 最短时间
{: #minimum-age }

可用于只删除较旧的缓存文件。它会应用于结果中的每个文件或文件夹。

### 包含无法访问的缓存
{: #include-inaccessible-caches }

根据 SD Maid 获得的权限不同，有些文件可能无法直接访问。这通常是指应用默认的私有缓存（例如 `/data/data/eu.darken.sdmse/cache`）。如果启用此设置，SD Maid 会尝试借助无障碍服务打开系统设置，并为目标应用点按`清除缓存`按钮。

### 过滤器
{: #filter }

您可以启用或停用不同的过滤器，以影响应用清理工具找出的结果。

#### 通用过滤器
{: #common-filters }

通用过滤器针对所有应用中的各种文件类型。

#### 特定过滤器
{: #specific-filters }

特定过滤器针对特定应用，例如 WhatsApp 或 Telegram。

## 故障排除
{: #troubleshooting }
### `com.miui.securitycenter`
{: #commiuisecuritycenter }
#### 缺少 `GET_USAGE_STATS` 权限
{: #missing-get_usage_stats-permission }

MIUI 和 HyperOS 使用一个名为“Security”或“Security Center”（手机管家/安全中心，包名：`com.miui.securitycenter`）的额外应用来显示应用的系统详情。清除应用缓存的正常操作流程是：点击`清除数据`，选择`清除缓存`，确认操作。

如果所有应用的“清除数据”按钮都显示为灰色且无法点击，说明“Security Center”应用缺少 `GET_USAGE_STATS` 权限。正常情况下，该权限应默认授予。目前还不清楚这种情况是如何发生的，但解决方法是重新授予该权限。

前往：`System Settings`（系统设置）> `Security and privacy`（安全与隐私）> `More security and privacy`（更多安全与隐私设置）> `Special permissions`（特殊权限）> `Usage access`（使用情况访问权限）> 找到 `Security`（手机管家）应用 > 授予该权限

另外，如果您拥有 ADB 或更高级别的访问权限，也可以运行命令 `pm grant com.miui.securitycenter android.permission.PACKAGE_USAGE_STATS`

<a href="/assets/images/appcleaner/miui-usage-stats-1.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-1.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-2.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-2.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-3.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-3.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-4.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-4.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-5.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-5.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-6.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-6.webp" width="100px">
</a>
