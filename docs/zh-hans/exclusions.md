---
title: "排除项"
lang: zh-Hans
translation_key: exclusions
permalink: /zh-hans/exclusions
source_hash: d5c3aea2efa43796
---
# 排除项
{: #exclusions }

SD Maid 2/SE 的排除功能允许用户指定在系统扫描和维护任务中应用应忽略的特定文件夹或文件。如果有些数据您希望不受应用操作的影响，这个功能就很有用。设置排除项后，SD Maid 2/SE 在执行卸载残留、应用清理以及其他系统管理功能等任务时，会跳过这些目录或文件。

添加排除项最简单的方法是直接从结果中添加。您可以对单个项目使用“排除”按钮，也可以长按或多选条目，然后选择“排除”选项。此外，还可以通过应用设置手动添加排除项，通过指定路径或通配符模式实现更精细的控制。

排除项可以通过菜单导出和导入。只有在 SD Maid 版本相近时才支持导入排除项。如果导入和导出所用的版本之间相差太多次更新，可能会出错。这种情况下，您可以尝试在较早的版本上导入，然后重新导出排除项。

<img src="/assets/images/exclusions/overview-1.webp" width="150">
<img src="/assets/images/exclusions/overview-2.webp" width="150">
<img src="/assets/images/exclusions/overview-3.webp" width="150">
<img src="/assets/images/exclusions/overview-4.webp" width="150">

## 类型
{: #types }

SD Maid 支持 3 种不同类型的排除项：

### 排除应用
{: #app-exclusions }

应用排除项基于每个应用的包名。可以通过应用控制（AppControl）工具创建。只需从列表中选择一个应用，然后使用相应的`排除`操作即可。

### 排除路径
{: #path-exclusions }

在存储分析器（StorageAnalyzer）工具中选择（长按）路径，即可创建路径排除项。

### 分段排除项 
{: #segment-exclusions }
分段排除项是一种不要求完全匹配的路径排除项。一个分段是路径的一部分，例如一个文件夹。路径 `/sdcard/DCIM/Camera` 由 3 个分段组成。通过为 `DCIM/Camera` 创建一个分段排除项，您可以同时排除内部存储（`/storage/emulated/0/DCIM/Camera`）和外部存储（`/sdcard/DCIM/Camera`）中的所有相机照片。

如果勾选`允许部分匹配`选项，您可以为 `DCIM/Cam` 创建一个排除项，同时匹配 `Cam` 文件夹和 `Camera` 文件夹。

## 工具
{: #tools }

SD Maid 的每个工具看待数据的方式都不同。并非所有排除项对所有工具的影响都相同。

### 卸载残留（CorpseFinder）
{: #corpsefinder }

卸载残留（CorpseFinder）工具知道什么是“应用”，以及哪些文件属于某个应用。它遵循所有类型的排除项。创建应用排除项意味着，卸载残留工具找到的、它认为属于被排除应用的所有文件都会被排除。

支持的排除类型：`APP`、`PATH`、`SEGMENTS`

### 系统清理（SystemCleaner）
{: #systemcleaner }

系统清理（SystemCleaner）工具只处理路径数据。它不知道什么是“应用”，也不知道哪些文件属于某个应用。

支持的排除类型：`PATH`、`SEGMENTS`

### 应用清理（AppCleaner）
{: #appcleaner }

应用清理（AppCleaner）工具遵循所有类型的排除项。如果有些应用包含大量需要扫描的文件，您可以使用排除项来加快扫描速度。

支持的排除类型：`APP`、`PATH`、`SEGMENTS`

### 去重工具（Deduplicator）
{: #deduplicator }

去重工具（Deduplicator）不知道什么是“应用”，只处理文件数据。

支持的排除类型：`PATH`、`SEGMENTS`

### 媒体压缩（Media Squeeze）
{: #media-squeeze }

媒体压缩（Media Squeeze）只处理文件数据。被排除的文件不会被压缩。

支持的排除类型：`PATH`、`SEGMENTS`

### Swiper
{: #swiper }

Swiper 只处理文件数据。被排除的文件不会包含在新的会话中。滑动时长按`跳过`会排除当前文件。

支持的排除类型：`PATH`、`SEGMENTS`

### 存储分析器（StorageAnalyzer）
{: #storageanalyzer }

存储分析器（StorageAnalyzer）不支持排除项，因为必须扫描所有内容才能计算出正确的总大小。
