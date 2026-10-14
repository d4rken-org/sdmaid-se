---
title: "错误"
lang: zh-Hans
translation_key: bugs
permalink: /zh-hans/bugs
source_hash: a218be31a6c4ae63
---
# 错误
{: #bugs }

🐌 🦋 🐛 🐜 🐝 🐞

错误（bug）是应用生命中的一部分。它们并无恶意，但也不会一直留在这里 😠。

什么是错误？

* 如果应用崩溃，那显然是一个错误
* 如果某些功能的表现出乎意料，那可能是一个错误。

无论哪种情况，请先确认您使用的是最新版本，该错误可能已经被修复。

如果错误尚未修复，请报告它 :grin:。

## 报告错误
{: #reporting-a-bug }

在 GitHub 上创建一个 issue：
<https://github.com/d4rken-org/sdmaid-se/issues>

* 描述您预期的结果以及实际发生的情况
* 列出其他人重现该问题所需执行的步骤
* 描述您的设备环境
    * 您的设备型号（例如 Pixel 5）
    * Android 版本（例如 Android 13.1）
    * 如果您的设备已 root，请注明 Magisk 版本

如果您能够重现该问题，请附上[调试日志](#debug-log)。 

提示：您可以在 SD Maid 的设置中[长按“更新日志”条目](https://github.com/d4rken-org/sdmaid-se/pull/940)，将版本号复制到剪贴板。

## 调试日志
{: #debug-log }

调试日志是对 SD Maid 操作的记录。您开始记录，重现问题，然后停止记录，即可分享一个 zip 文件，其中按时间顺序记录了 SD Maid 所做的一切。

要开始记录调试日志：
* 点按控制面板右下角的设置图标，进入 SD Maid 的设置
* 在设置列表中找到`支持`条目
* 在支持页面上点按`记录 Debug 日志`，然后在对话框中再次点按`记录 Debug 日志`
* 记录期间，控制面板会显示`正在记录 Debug 日志`卡片
* **现在重现您想要记录的问题**
* 重现问题后，在该控制面板卡片或支持页面上点按`停止记录`。如果记录时间很短，SD Maid 会先询问您；点按`仍然停止`即可停止记录。
* `调试日志`页面随即打开。日志文件压缩完成后，点按`分享`即可发送。

调试日志会一直记录，直到您停止它；即使您强行停止并重新启动 SD Maid，记录也会继续。

如果您正在排查与核心功能相关的问题，例如 root 检测或检测到的存储区域，那么在记录期间强行停止并重新启动 SD Maid 会很有帮助，因为某些数据在每个应用会话中只会初始化一次。

调试日志文件存储在 `/storage/emulated/0/Android/data/eu.darken.sdmse/cache/debug/logs`。

图示：

<a href="/assets/images/bugs/debug-log-1.webp"><img src="/assets/images/bugs/debug-log-1.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-2.webp"><img src="/assets/images/bugs/debug-log-2.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-3.webp"><img src="/assets/images/bugs/debug-log-3.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-4.webp"><img src="/assets/images/bugs/debug-log-4.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-5.webp"><img src="/assets/images/bugs/debug-log-5.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-6.webp"><img src="/assets/images/bugs/debug-log-6.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-7.webp"><img src="/assets/images/bugs/debug-log-7.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-8.webp"><img src="/assets/images/bugs/debug-log-8.webp" width="100" alt=""></a>
