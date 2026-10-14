---
title: "设置 SD Maid SE"
lang: zh-Hans
translation_key: setup
permalink: /zh-hans/setup
source_hash: a14003a95dd2336d
---
# 设置 SD Maid SE
{: #setting-up-sd-maid-se }
关于 SD Maid SE 各个设置步骤的说明。

SD Maid 会根据可用的访问权限进行调整。虽然所有权限都是可选的，但如果您不授予任何权限，大多数功能都将无法工作。完成所有设置项后，SD Maid 的表现才会最好。

如果有 [root](#root-access) 或 [ADB 访问权限](#shizuku)，SD Maid 会自动尝试完成部分设置步骤。

有些权限相互重叠，或者一个是另一个的超集，例如您可以同时授予 SD Maid root 权限和 ADB 访问权限。对于任何操作，SD Maid 总是会尝试使用最佳的访问方式；如果失败，则改用次佳的访问方式重试，然后是第三佳的，依此类推。授予 SD Maid 相互重叠的权限可以提高可靠性。

> **提示：**下面的一些设置步骤需要运行 ADB 命令。如果您的电脑上没有配置 ADB，可以使用 [Web ADB Helper](https://d4rken.github.io/web-adb/) 直接在浏览器中运行这些命令。

## 管理存储
{: #manage-storage }

“管理存储”权限是一项范围很广的存储访问权限。

* 在 Android 12 及更早版本上，它会授予对所有公共存储的访问权限。
* 在 Android 13+ 上，它仍然会授予对所有公共存储的访问权限，但不包括 `Android/data` 下的子目录。

## 使用情况
{: #usage-statistics }

使用情况（Usage statistics）权限可让 SD Maid 获取应用的数据和缓存大小，以及应用是否正在运行或上次使用的时间。

## 存储访问框架
{: #storage-access-framework }
SD Maid 使用 [Android 存储访问框架（SAF）](http://developer.android.com/guide/topics/providers/document-provider.html)来获取对更多存储区域的访问权限。对于仅凭“管理存储”权限无法访问的特定路径，SD Maid 会请您授予访问权限（Android 就是这样™️）。

* 点按`授予权限`按钮 
* 会打开一个新窗口
* 如果已经选中了正确的路径就无需操作，否则您需要导航到 SD Maid 需要的路径。
* 然后点按`使用此文件夹`按钮，该路径对应的设置条目就会解锁并变为绿色。

重复此过程，直到所有路径都变为绿色。

<a href="/assets/images/setup/saf-1.webp"><img src="/assets/images/setup/saf-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-2.webp"><img src="/assets/images/setup/saf-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-3.webp"><img src="/assets/images/setup/saf-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-4.webp"><img src="/assets/images/setup/saf-4.webp" width="100" alt=""></a>

### SAF：故障排除
{: #saf-troubleshooting }
适用于事情没有按计划进行的情况。

#### SAF：`Android/data|obb` 访问权限
{: #saf-androiddataobb-access }

在 Android 11 和 Android 12 上，使用较新版本的系统`文件`应用（`com.google.android.documentsui`）无法授予对 `Android/data|obb` 的访问权限。如果已安装的`文件`应用的目标版本为 Android 14（API 级别 34）或更高，SD Maid 就会隐藏针对这些路径的 SAF 访问请求（与 Android 13+ 上一样）。

Google Play 通过 `Google Play 系统更新`服务来更新`文件`应用。Android 12 系统自带的原始“文件”应用（针对 API 级别 31 构建，例如 `aml_doc_310851020 (310851020)`）允许 SD Maid 在 `Android/Data` 上打开 SAF 路径选择窗口，而最新的“文件”应用（针对 API 级别 34 或更高版本构建，例如 `14-10492947 (340916000)`）会阻止这样做。

**解决方法：**

您可以卸载`文件`应用的更新，然后再设置 SD Maid。之后再次更新`文件`应用，不会破坏已经授予的权限。

* 打开设备设置
* 前往“Apps & notifications”（应用和通知）或“Application Manager”（应用管理器）
* 找到`文件`系统应用
* 点按三点菜单，通常位于右上角
* 选择“卸载更新”

或者

_您也可以使用 [Web ADB Helper](https://d4rken.github.io/web-adb/) 运行此命令。_

`adb uninstall com.google.android.documentsui`

#### SAF：OPEN_DOCUMENT_TREE ActivityNotFoundException
{: #saf-open_document_tree-activitynotfoundexception }

通过存储访问框架授予路径访问权限，需要一个能让用户确认要授予访问权限的路径的应用。这个应用就是“Documents”（文档）应用。它是自 Android 5.0 起内置于每台 Android 设备中的系统应用（`com.android.documentsui` 或 `com.google.android.documentsui`）。如果您遇到此错误，说明有人删除或停用了这个应用。大多数情况下，是有人运行了一个过于激进的“精简”（de-bloating）工具，把它停用了。您可以通过 ADB 重新启用它：

_您也可以使用 [Web ADB Helper](https://d4rken.github.io/web-adb/) 运行这些命令。_

```
adb shell pm enable com.android.documentsui
or
adb shell pm enable com.google.android.documentsui
```

#### SAF：窗口为空
{: #saf-empty-window }
如果用于授予路径访问权限的窗口是空的，那么可能是您的外部存储提供程序软件包（`com.android.externalstorage`）被停用了（很可能是被过于激进的“精简”工具停用的）。您可以通过 ADB 重新启用它：

_您也可以使用 [Web ADB Helper](https://d4rken.github.io/web-adb/) 运行此命令。_

```
adb shell pm enable com.android.externalstorage
```

再次运行 `Google Play 系统更新`即可恢复该更新。您也可以从 [APKMirror.com](https://www.apkmirror.com/apk/google-inc/files-6/) 手动下载更新后的版本。

## 通知
{: #notifications }

如果没有此权限，SD Maid 将无法发布通知。在某些 ROM 上，这可能会降低后台执行时的可靠性。

## 应用列表
{: #app-inventory }

SD Maid 需要 `QUERY_ALL_PACKAGES` 权限来检查您的设备上安装了哪些应用（即[“查看已安装应用的清单”](https://support.google.com/googleplay/android-developer/answer/10158779?hl=en)）。另请参阅[“使用情况权限”](/zh-hans/setup#usage-statistics)

以下功能必须具备 `QUERY_ALL_PACKAGES` 权限：

* [卸载残留（CorpseFinder）](/zh-hans/corpsefinder)：为了判断哪些文件可以删除，SD Maid 需要知道您是否安装了可能需要这些文件的应用。
* [应用清理（AppCleaner）](/zh-hans/appcleaner)：确定每个应用的缓存
* [应用控制（AppControl）](/zh-hans/appcontrol)：能够列出所有已安装的应用
* [存储分析器（StorageAnalyzer）](/zh-hans/storageanalyzer)：将已用存储归属到已安装的应用

此权限自 [Android 11](https://developer.android.com/training/package-visibility) 起就已存在，通常默认授予。自 Android 14 起，有些设备（例如 [#1015](https://github.com/d4rken-org/sdmaid-se/issues/1015)、[#1354](https://github.com/d4rken-org/sdmaid-se/issues/1354)）不会默认授予此权限，或者该权限可能被撤销。

[<img src="/assets/images/setup/app-inventory.webp" width="100">](/assets/images/setup/app-inventory.webp)

### 无效的应用列表
{: #invalid-app-list }

在某些设备上，“应用列表”权限（`QUERY_ALL_PACKAGES` / `GET_INSTALLED_APPS`）已经授予，但系统仍然返回**不完整的已安装应用列表**，例如缺少 `android` 等核心系统软件包的列表。SD Maid 检测到这种情况时，会在设置卡片中显示**“无效的应用列表”**警告和**“可能的设备限制”**提示，而不是继续进行。

SD Maid 是有意在这里停下的：如果它看不到完整的已安装应用列表，就无法可靠地判断哪些文件属于仍然安装着的应用。如果强行继续，可能会删除属于某个 SD Maid 只是没能看到的应用的数据，因此在获得完整列表之前，与应用相关的功能会一直保持停用。

**您可以尝试：**

* 打开系统设置，确保 SD Maid 已获得“应用列表”权限。在某些厂商的设备上，这个开关的名称不同（例如“查询所有软件包”），或者位于单独的“隐私”或“权限管理器”界面中。
* 检查是否有内置的省电功能、隐私保护或“隐藏应用”功能正在过滤 SD Maid 能看到的应用列表，并将 SD Maid 从中排除。
* 重新启动设备，然后重新打开 SD Maid，以便重新评估权限状态。

如果“应用列表”权限已经允许，但列表仍被报告为无效，这通常是**设备限制**，而不是您能修复的问题：有些系统（例如 HarmonyOS，或经过大量修改的 Android 版本）从不向第三方应用提供完整的应用列表。示例请参阅 [#2441](https://github.com/d4rken-org/sdmaid-se/issues/2441)。

在这种情况下，与应用相关的工具（[卸载残留](/zh-hans/corpsefinder)、[应用清理](/zh-hans/appcleaner)、[应用控制](/zh-hans/appcontrol)）会保持停用以保护您的数据，但[存储分析器](/zh-hans/storageanalyzer)仍然可以工作：它会显示您的存储空间使用了多少，只是细节较少：文件夹大小以只读方式显示，且无法归属到单个应用。

## 无障碍服务
{: #accessibility-service }
无障碍服务允许 SD Maid 像人一样与您的设备交互，即查看屏幕和点按按钮。SD Maid 可以用它来自动执行繁琐的操作，例如在数百个应用上点按“清除缓存”。无障碍服务是一种应用自身无法启动的服务，必须由系统启动，因为这类服务会授予应用对您设备的额外访问权限。

### ACS：故障排除
{: #acs-troubleshooting }

在多种情况下，该服务可能会停止运行，需要您重新启用：
* 您更新了应用。
* 应用崩溃。如果发生这种情况，您应该将其作为错误报告。如果这种情况发生得太频繁，在设备重启之前，您可能无法通过关闭再打开的方式重新启动它。
* 系统由于过于激进的“电池优化”而终止了应用（另请参阅 <https://dontkillmyapp.com/>）
* 厂商自带的安全应用将其关闭（请参阅[被手机管家阻止](#acs-blocked-by-phone-manager-oneplus-oppo-realme)）

#### ACS：服务已停用
{: #acs-service-is-disabled }

某些 ROM（例如 MIUI）因会激进地停止所有未被设备厂商列入白名单的东西而臭名昭著。
如果系统不断要求您重新启用无障碍服务，那么可能是每当 SD Maid 不在活跃使用时，系统就会将其停用。

请尝试停用电池优化；在 MIUI ROM 上还有一个名为“Autostart”（自启动）的额外设置，请为 SD Maid 启用它。对于小米/POCO/红米设备：

* 前往 `Security>Manage applications>Auto start in background`（安全>应用管理>后台自启动），并为 SD Maid 允许此项。
* 前往 `Security>Increase speed>Lock apps`（安全>加速>锁定应用），然后选择 SD Maid。


您也可以考虑授予额外的权限，以[允许 SD Maid 自动重新启动该服务](#acs-automatic-service-restart)。

[<img src="/assets/images/setup/acs-disabled.webp" width="100">](/assets/images/setup/acs-disabled.webp)

#### ACS：被手机管家阻止（一加、OPPO、realme）
{: #acs-blocked-by-phone-manager-oneplus-oppo-realme }

在 OxygenOS 和 ColorOS 上，预装的“Phone Manager”（手机管家）应用可能会自行关闭无障碍服务，通常是在清理完成后一秒钟之内。之后 Android 的无障碍开关确实处于关闭状态，所以看起来就像 SD Maid 在运行中途停止了工作。

请在 `Phone Manager>Utilities>Block suspicious app activities`（手机管家>实用工具>拦截可疑应用行为）中关闭此功能。

同一界面中会保留一份它拦截过的记录列表。如果其中标记为“Abnormal device control”（异常设备控制）的条目时间与您运行清理的时间吻合，就可以确认这就是原因。旁边的“Blocking alerts”（拦截提醒）开关只控制您是否会收到通知，因此在关闭提醒的情况下，这一切都会悄无声息地发生。

#### ACS：通过 ADB 启用
{: #acs-enable-via-adb }

如果您的设备无法通过屏幕启用无障碍服务（例如电视设备），可以通过 ADB 运行以下命令来启用该服务：

_您也可以使用 [Web ADB Helper](https://d4rken.github.io/web-adb/) 运行此命令。_

```bash
adb shell settings put secure enabled_accessibility_services eu.darken.sdmse/.automation.core.AutomationService
```

#### ACS：自动重启服务
{: #acs-automatic-service-restart }

_注意：这是一项高级技巧，面向高级用户。_

您可以授予一项额外的权限，让 SD Maid 能够自行重新启动该服务。
该权限名为 `WRITE_SECURE_SETTINGS`，只能[通过 ADB](https://www.google.com/search?q=android+adb) 授予。
如果您已经配置好 ADB，请运行以下命令来授予该权限：

_您也可以使用 [Web ADB Helper](https://d4rken.github.io/web-adb/) 运行此命令。_

```bash
adb shell pm grant eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

授予此权限后，SD Maid 可以在必要时使用[此处所述](#acs-enable-via-adb)的命令来启用该服务。
只有在您至少手动启用过一次无障碍服务后，SD Maid 才会使用此方法。
如果您再次进入设置并告诉 SD Maid 不要使用无障碍服务，那么在您再次手动启用无障碍服务之前，将不会使用这种自动重启技巧。

要再次从 SD Maid 移除此权限，请使用以下命令：

_您也可以使用 [Web ADB Helper](https://d4rken.github.io/web-adb/) 运行此命令。_

```bash
adb shell pm revoke eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

如果您收到 `Operation not allowed: java.lang.SecurityException`，[请参阅此处](https://stackoverflow.com/questions/52079343)。有些设备会限制 ADB 权限。请查看开发者选项中是否有需要切换的额外设置（例如 `Disable Permission Monitoring`（停用权限监控）），使用命令之后，您可以再将其恢复。

#### ACS：高级保护
{: #acs-advanced-protection }

在 Android 17 及更高版本上，Google 的[**高级保护**](https://developer.android.com/privacy-and-security/advanced-protection-mode)（Advanced Protection）模式会阻止非专用无障碍工具（屏幕阅读器、开关控制等）的应用使用无障碍服务。SD Maid 是一款维护/清理工具，因此在启用高级保护期间，其无障碍服务**无法**启用：Android 会在系统层面拒绝该服务，即使您尝试通过 root 或 ADB 访问权限来授予也不行。

SD Maid 无法让自己免受此限制：只有被 Google 归类为无障碍工具的应用才符合条件，而清理工具被明确排除在外。

您有两个选择：

- **使用 root 或 ADB 访问权限**：配置好 root 或 ADB 访问权限后，SD Maid 的应用清理会通过直接的系统调用清除应用缓存，**不**需要无障碍服务。使用 **root** 时，这也涵盖了清理单独选择的应用。使用 **ADB 访问权限**时，完整的“清理所有应用”运行无需无障碍服务即可完成，但清除*特定*所选应用的缓存仍然依赖于它。请在 SD Maid 的设置 → [Root 权限](#root-access)或 [ADB 访问权限](#shizuku)中进行配置。
- **关闭高级保护**：如果您特别需要基于无障碍服务的清理，可以关闭它。它位于 `Settings → Security & privacy → Advanced Protection`（设置 → 安全和隐私 → 高级保护）下（确切路径因设备而异）。之后再次开启它会再次停用无障碍服务。

#### ACS：AppOps 限制
{: #acs-appops-restrictions }

Android 13+ 可能会对侧载的应用施加额外的限制。因此，如果 SD Maid 不是从 Google Play 或其他应用商店等（Google 所知的）已知来源安装的，那么您可能无法直接向 SD Maid 授予某些权限，例如启动无障碍服务。

##### OxygenOS 15 - 一加
{: #oxygenos-15---oneplus }

一加在 OxygenOS 15 的应用设置中忘了（或者干脆没有）加入`允许受限制的设置`操作。如果您不是通过 Google Play 安装，那么安装程序需要设置 `INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS` 标志。如果您已经配置好 Shizuku，可以使用 [“Install with Options”应用](https://github.com/zacharee/InstallWithOptions)。


##### 其他 ROM
{: #other-roms }

要解除这些限制，请在系统中打开 SD Maid 的应用设置界面。打开右上角的菜单并选择`允许受限制的设置`，之后您可以返回无障碍服务列表并启用 SD Maid 的服务。

**注意：**
为了提高易用性（这是讽刺），Google 决定只有在以下情况下才会显示此设置：

* 您在点击 SD Maid 的无障碍服务条目后，至少看到过一次“受限制的设置”
* 您是手动打开系统设置并导航到 SD Maid 的设置页面的，而不是通过其他应用或快捷方式进入。

😮‍💨


更多背景信息[请参阅此处](https://blog.esper.io/android-13-sideloading-restriction-harder-malware-abuse-accessibility-apis/)。

<a href="/assets/images/setup/acs-restricted-settings-1.webp"><img src="/assets/images/setup/acs-restricted-settings-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-2.webp"><img src="/assets/images/setup/acs-restricted-settings-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-3.webp"><img src="/assets/images/setup/acs-restricted-settings-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-4.webp"><img src="/assets/images/setup/acs-restricted-settings-4.webp" width="100" alt=""></a>

## Root 权限
{: #root-access }

拥有 root 权限时，SD Maid 可以完全访问您的设备，并执行尽可能彻底的扫描。这通常意味着您已经解锁了引导加载程序（bootloader）并安装了 Magisk。

如果 SD Maid 认为您的设备可能已经 root，就会首先请求 root 权限。如果获得了 root 权限，SD Maid 会利用它尽可能多地授予其他权限。root 权限是您能给予一个应用的最高级别的访问权限，此时其他权限只是为了易用性和性能才需要，因此如果 SD Maid 已经拥有 root 权限，在设置过程中就无需再额外征得同意。

## ADB 访问权限
{: #shizuku }

ADB 访问权限让 SD Maid 能够使用 Android 的调试接口（ADB）执行需要更高权限的操作。它需要一个单独的应用，该应用在您的设备上运行 ADB 服务，并向其他应用授予对该服务的访问权限：[Porter](#porter) 或 [Shizuku](#using-shizuku)。这是您可以对手机做的一项修改，其他应用也可以利用它。

它与 root 类似，但并不相同。其权限稍弱一些，但这项修改不是永久性的，也不需要您解锁引导加载程序。

### Porter
{: #porter }

[Porter](https://porter.darken.eu) 是我维护的 Shizuku 分支，带有可选的 root 支持。它的网站上介绍了如何安装和启动它。在 Android 11 及更高版本上，借助无线调试，您无需电脑即可启动它。

如果您是第一次设置 ADB 访问权限，请使用 Porter。在没有安装任何提供 ADB 访问权限的应用时，来自 GitHub 和 F-Droid 的 SD Maid 版本会引导您使用 Porter。Google Play 版本会引导您前往 Google Play 上的 Shizuku，但与 Porter 配合使用的方式完全相同。

如果 Porter 和 Shizuku 都已安装，SD Maid 会使用 Porter。此时设置卡片会显示`已安装 Porter，因此 SD Maid 会等待 Porter，而不是 Shizuku`。请启动 Porter 以继续，或者如果您希望 SD Maid 使用 Shizuku，就将 Porter 卸载。

### 使用 Shizuku
{: #using-shizuku }

Shizuku 有两个版本：

* [thedjchi 的分支](https://github.com/thedjchi/Shizuku)（[设置指南](https://github.com/thedjchi/Shizuku/wiki/setup)）一直在积极维护，据报告在原版[始终无法连接](#shizuku-never-connects)的设备上也能正常工作。
* [RikkaApps 的原版](https://shizuku.rikka.app/guide/setup/)在大多数设备上仍然运行良好。

两者不能同时安装。如果原版在您的设备上已经可以正常工作，就没有必要切换。

### 授予访问权限
{: #granting-access }

设置好 Porter 或 Shizuku 后：

* 在 SD Maid 的设置中，选择`尝试使用 ADB 访问权限`。这是 SD Maid 内部的选项，您可以用它来启用或停用 SD Maid 对 ADB 访问权限的使用
* 同意之后，Porter 或 Shizuku 会弹出一个对话框，询问是否允许 SD Maid 使用它
* 确认该对话框
* `ADB 访问权限`设置卡片会显示 SD Maid 是否能够连接

如果您已经授予 SD Maid root 权限，再设置 ADB 访问权限并不会启用额外的功能，但可以提高可靠性。如果使用 root 权限的操作失败，SD Maid 会改用 ADB 访问权限重试。

### ADB 访问权限：故障排除
{: #shizuku-troubleshooting }

您可以前往`设置 > 设置`并查看 `ADB 访问权限`设置卡片，以检查 SD Maid 的 ADB 访问权限状态。它应该显示 `SD Maid 现在可以通过 Porter 使用 ADB 访问权限`（或`通过 Shizuku`）。

* `Porter 尚未允许 SD Maid`：确认访问对话框，或在 Porter（或 Shizuku）应用中允许 SD Maid。
* `Porter 拒绝了 SD Maid 的访问`：SD Maid 已被永久拒绝。请打开 Porter（或 Shizuku），并在其中允许 SD Maid。
* `更新 Porter 以便与 SD Maid 配合使用`或`此版本的 SD Maid 对 Porter 来说过旧`：这两个应用的版本不兼容。请更新卡片中指出的那个应用。

#### Shizuku 始终无法连接
{: #shizuku-never-connects }

卡片显示 `ADB 访问失败`和 `Shizuku 正在运行，但从未将 SD Maid 连接到其辅助服务。`旧版本中显示的是 `Waiting for link with Shizuku`（正在等待与 Shizuku 建立连接）或 `Shizuku link failed`（Shizuku 连接失败）。

打开 Shizuku 应用。如果它显示正在运行，并且 SD Maid 在其已授权列表中，那么 Shizuku 的设置没有问题，重新设置也无济于事。出问题的是 Shizuku 的辅助服务：它在启动时就崩溃了，来不及回报，而 SD Maid 会在大约 15 秒后放弃。`重试`会执行同样的检查，也会以同样的方式失败。

这是 Shizuku 的错误（[RikkaApps/Shizuku#1198](https://github.com/RikkaApps/Shizuku/issues/1198)），而不是 SD Maid 的错误，它会影响所有使用 Shizuku 辅助服务的应用。受影响的有联发科（MediaTek）设备，以及运行 HyperOS 或 MIUI 的设备。

据报告，[thedjchi 的分支](https://github.com/thedjchi/Shizuku)修复了这个问题。要切换：

* 先卸载原版，两者无法共存
* 如果您使用过无线调试，请取消它的配对
* 从[发布页面](https://github.com/thedjchi/Shizuku/releases)安装该分支，并按照其[设置指南](https://github.com/thedjchi/Shizuku/wiki/setup)操作
* 重新向 SD Maid 以及其他之前使用过 Shizuku 的应用授予 Shizuku 权限
