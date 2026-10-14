---
title: "卸载残留（Corpse Finder）"
lang: zh-Hans
translation_key: corpsefinder
permalink: /zh-hans/corpsefinder
source_hash: 9e16286f0fda1dc8
---
# 卸载残留（Corpse Finder）
{: #corpse-finder }

`卸载残留`（CorpseFinder）工具会查找属于已不再安装的应用的文件。


## 风险等级
{: #risklevel }

默认情况下，所有风险等级选项都处于关闭状态。只有在您清楚自己在做什么时才启用它们，并且在删除任何内容之前，请先检查结果。
启用风险等级选项会增加误删您并不想删除的文件的可能性。

风险等级主要适用于白名单位置，例如公共存储的根目录（`/storage/emulated/0`）。这类位置没有通用的命名规律，SD Maid 只能通过一个人工维护的数据库来判断其归属。


### 显示有用的残留
{: #include-desirable-remnants }

有用的残留是指归属已知、所属应用未安装，但在 SD Maid 内部被标记为 `KEEPER` 的文件。这些通常是用户创建的文件，例如您拍摄的照片。

### 包含常见名称
{: #include-common-names }

有些应用使用的名称非常常见。可能有成百上千款笔记应用会创建 `/sdcard/Notes` 文件夹来保存数据。SD Maid 可能认识其中一款应用，而它并未安装在您的手机上；但 SD Maid 无法确定您的手机上没有另一款应用仍在使用该文件夹，而 SD Maid 并不知道这款应用也在使用该文件夹。 

## 过滤器
{: #filter }

每个过滤器负责一个特定的数据区域。启用某个过滤器后，卸载残留工具会额外搜索该区域。

### SD 卡
{: #sdcard }

* 通常是用户存放文件的地方
* 公共存储（包括内置存储和可移动存储），但不包括 `Android/data` 中的特定子目录。
* 这里的文件没有命名规律。

示例：

```bash
/storage/emulated/0/SDMaid
/storage/DEAD-BEEF/SDMaid
/sdcard/SDMaid
```

### 公共媒体
{: #public-media }

* 存储应用的公共媒体，例如通过 WhatsApp 接收的媒体。文件夹以应用的包名命名。
* 公共存储中对操作系统有特殊含义的子目录。 

示例：

```bash
/storage/emulated/0/Android/media/eu.darken.sdmse
/storage/DEAD-BEEF/Android/media/eu.darken.sdmse
/sdcard/Android/media/eu.darken.sdmse
```

### 公共应用数据
{: #public-app-data }

* 应用的额外文件。
* 文件夹以应用的包名命名。
* 公共存储中对操作系统有特殊含义的子目录。

示例：

```bash
/storage/emulated/0/Android/data/eu.darken.sdmse
/storage/DEAD-BEEF/Android/data/eu.darken.sdmse
/sdcard/Android/data/eu.darken.sdmse
```

### OBB 资源
{: #obb-resources }

* 通常是游戏的额外资源文件。
* 文件夹和文件以应用的包名命名。
* 公共存储中对操作系统有特殊含义的子目录。

示例：

```bash
/storage/emulated/0/Android/obb/eu.darken.sdmse
/storage/DEAD-BEEF/Android/obb/eu.darken.sdmse
/sdcard/Android/obb/eu.darken.sdmse
```

### 私有程序数据
{: #private-app-data }

* 文件夹和文件以应用的包名命名。
* 公共存储中对操作系统有特殊含义的子目录。

示例：

```bash
/data/data/eu.darken.sdmse
```

### Dalvik 虚拟机缓存
{: #dalvik-cache }

* 存储经过优化的应用代码版本，以便更快地执行。
* 位于系统分区中，通常在特定架构的目录下。
* 文件使用应用的包名命名。

示例：

```bash
/data/dalvik-cache/arm64/data@app@eu.darken.sdmse@base.apk@classes.dex
/data/dalvik-cache/arm/data@app@eu.darken.sdmse@base.apk@classes.dex
```

### 程序库
{: #app-libraries }

* 包含应用使用的原生库，例如 `.so` 文件。
* 通常位于应用数据目录下的 `lib` 文件夹中。
* 文件根据架构和库的要求命名。

示例：

```bash
/data/app/eu.darken.sdmse-1/lib/arm64/libexample.so
/data/app/eu.darken.sdmse-2/lib/arm/libexample.so
```

### 程序源
{: #app-sources }

* 指作为应用源代码的 APK 文件。
* 存储在系统应用、用户应用或厂商应用的特定目录中。
* 文件以应用的包名和版本命名。

示例：

```bash
/data/app/eu.darken.sdmse-1/base.apk
/system/app/ExampleApp/ExampleApp.apk
```

### 私有程序源
{: #private-app-sources }

* 与程序源类似，但存储在受保护的目录中，以增强安全性。
* 只有系统或相应的应用才能访问。
* 文件以应用的包名命名。

示例：

```bash
/data/app-private/eu.darken.sdmse/base.apk
```
