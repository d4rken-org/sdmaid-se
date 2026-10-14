---
title: "计划任务（Scheduler）"
lang: zh-Hans
translation_key: scheduler
permalink: /zh-hans/scheduler
source_hash: 74e1452091eaf142
---
# 计划任务（Scheduler）
{: #scheduler }

SD Maid 2 中的**计划任务**（Scheduler）功能可以让您按特定间隔自动执行维护任务，确保设备无需手动操作即可保持优化状态。它支持**应用清理**（AppCleaner）、**卸载残留**（CorpseFinder）和**系统清理**（SystemCleaner）等多个工具，这些工具可以配置为按固定间隔或在特定时间运行。

<img src="/assets/images/scheduler/overview-1.webp" width="150">
<img src="/assets/images/scheduler/overview-2.webp" width="150">
<img src="/assets/images/scheduler/overview-3.webp" width="150">
<img src="/assets/images/scheduler/overview-4.webp" width="150">
<img src="/assets/images/scheduler/overview-5.webp" width="150">
<img src="/assets/images/scheduler/overview-6.webp" width="150">


## 如何设置计划
{: #how-to-set-up-a-schedule }

1. 前往**设置**，然后选择**计划任务**。
2. 点按 **+** 按钮以创建新计划，或管理现有计划。
3. 选择要按计划运行的工具（例如**应用清理**、**卸载残留**、**系统清理**）。
4. 设置**重复间隔**（例如每 3 天）和执行的**大约时间**。
5. 切换开关以启用该计划。

## 编辑或删除计划
{: #editing-or-deleting-schedules }

1. 前往**计划任务**设置来管理您的计划。
2. 您可以修改现有计划、调整间隔，或在不再需要时删除它们。

计划启用后，SD Maid 会在指定时间运行所选任务。

## 其他选项
{: #additional-options }

通过主设置菜单 `设置 > 计划任务` 可以配置一些额外选项。

- **低电量时跳过**：如果设备处于省电模式，计划任务将被跳过。
- **仅在充电时执行**：计划任务只会在设备充电时运行，以避免耗电。
- **计划任务后命令**：您可以设置在计划任务完成后执行的 shell 命令。
- **无障碍服务**：默认情况下，计划任务会跳过基于无障碍服务的功能，因为这些功能在设备锁定时无法工作。如果您启用此选项，但没有对设备进行任何其他更改，计划任务就会失败。因此，除非您清楚自己在做什么，否则请保持此选项关闭。它只在一些特殊情况下有用，例如始终开启的 Android TV 设备。

## 故障排除
{: #troubleshooting }

### 计划任务未执行 
{: #scheduler-is-not-executing }

请区分计划任务根本没有运行，和已执行的操作没有达到预期效果这两种情况。您可以在 SD Maid 的[历史部分](/zh-hans/stats)中查看执行历史记录。如果计划任务已执行但失败了，那里应该会有一条错误信息。

如果计划任务根本没有运行，可能是系统为了省电而阻止了 SD Maid 启动。启用第一个计划后，您应该已经看到一个标题为“建议”、带有“修复”操作的提示，要求您停用电池优化。如果您不小心关闭了该提示，就需要手动进入系统设置中 SD Maid 的页面，检查 SD Maid 是否正被系统“优化”。

### 无障碍服务
{: #accessibility-service }
基于无障碍服务的删除操作（例如应用清理删除“无法访问的项目”）无法通过计划任务执行。计划任务执行时，设备通常处于锁定状态；就像您无法在关闭或锁定的屏幕上点击任何东西一样，SD Maid 也做不到。

默认情况下，计划任务不会执行基于无障碍服务的操作。不过，如果您清楚自己在做什么，可以在计划任务设置中启用“无障碍服务”选项。

解决方法：root 设备（用户应用和系统应用均适用），或安装 Shizuku（仅适用于用户应用）。这两种方式都能让 SD Maid 在不使用无障碍服务功能的情况下执行类似的操作。
