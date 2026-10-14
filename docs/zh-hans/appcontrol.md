---
title: "应用控制（AppControl）"
lang: zh-Hans
translation_key: appcontrol
permalink: /zh-hans/appcontrol
source_hash: 1a4a7450fc5b86ca
---
# 应用控制（AppControl）
{: #appcontrol }
应用控制（AppControl）是管理已安装应用的一站式工具，涵盖所有相关操作。扫描完成后，您会得到一个可搜索的已安装应用列表。系统应用默认不显示，需要在设置中启用。
某些选项可能只有在 SD Maid 获得 root 权限后才可用（才能执行）。

有些操作可以对多个应用执行，有些只能对单个项目执行。要对多个应用执行操作，请长按一个应用，然后点按以选择更多应用。

## 操作
{: #actions }
其中一些操作只能在应用详情中使用，点按列表中的条目即可进入应用详情。

### 启动应用
{: #run-app }
启动该应用的主要活动，通常与启动器执行的操作相同。

### 强行停止
{: #force-stop }
尝试强行停止该应用。使用 Root 或 ADB/Shizuku 时效果最好，否则会使用无障碍服务。

### 导出 APK
{: #export-apk }
尝试将应用的 `.apk` 文件从内部私有存储复制到公共存储的下载目录。如果应用经过加密，此操作需要 root；未加密的应用无需 root 也可以导出。

### 冻结应用
{: #freeze-app }
需要 root 或 ADB/Shizuku。停用该应用。该应用将不可见，也无法自行启动。必须重新启用后才能正常工作。不会丢失任何数据，即使卸载 SD Maid，此状态也会保持。任何合格的应用启用/停用工具都可以启用被 SD Maid 停用的应用，反之亦然。

### 删除应用
{: #delete-app }
删除该应用及其数据。


## 设置
{: #settings }
### 测定大小
{: #determine-size }
可以停用此项以加快应用列表的加载速度。测定应用的大小需要花费大量时间。

### 活动识别
{: #determine-activity }
可以停用此项以加快应用列表的加载速度。判断应用最近是否活跃需要花费大量时间。
