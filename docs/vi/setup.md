---
title: "Thiết lập SD Maid SE"
lang: vi
translation_key: setup
permalink: /vi/setup
source_hash: a14003a95dd2336d
---
# Thiết lập SD Maid SE
{: #setting-up-sd-maid-se }
Thông tin về các bước thiết lập khác nhau của SD Maid SE.

SD Maid thích ứng với những quyền truy cập hiện có. Mặc dù mọi quyền đều là tùy chọn, nhưng nếu bạn không cấp quyền nào, hầu hết các tính năng sẽ không hoạt động. SD Maid sẽ hoạt động tốt nhất khi tất cả các mục thiết lập đều được hoàn tất.

SD Maid sẽ tự động cố gắng hoàn tất một số bước thiết lập nếu có sẵn [root](#root-access) hoặc [quyền truy cập ADB](#shizuku).

Một số quyền chồng chéo nhau hoặc là tập bao của nhau, ví dụ bạn có thể cấp cho SD Maid cả quyền root lẫn quyền truy cập ADB. SD Maid sẽ luôn cố gắng dùng phương thức truy cập tốt nhất có thể cho mọi thao tác, và nếu thất bại, sẽ thử lại với phương thức tốt thứ hai, rồi thứ ba, v.v. Cấp cho SD Maid các quyền chồng chéo nhau sẽ cải thiện độ tin cậy.

> **Mẹo:** Một số bước thiết lập bên dưới cần chạy lệnh ADB. Nếu bạn chưa thiết lập ADB trên máy tính, bạn có thể dùng [Web ADB Helper](https://d4rken.github.io/web-adb/) để chạy chúng trực tiếp từ trình duyệt.

## Quản lý bộ nhớ
{: #manage-storage }

Quyền "Quản lý bộ nhớ" là một quyền truy cập bộ nhớ có phạm vi rộng.

* Trên Android 12 trở xuống, quyền này cấp quyền truy cập vào toàn bộ bộ nhớ công khai.
* Trên Android 13+, quyền này vẫn cấp quyền truy cập vào toàn bộ bộ nhớ công khai, nhưng không bao gồm các thư mục con trong `Android/data`.

## Thống kê sử dụng
{: #usage-statistics }

Quyền thống kê sử dụng cho phép SD Maid truy cập dung lượng dữ liệu và bộ nhớ đệm, và biết một ứng dụng có đang chạy hay không hoặc được dùng lần cuối khi nào.

## Storage Access Framework
{: #storage-access-framework }
SD Maid dùng [Storage Access Framework (SAF) của Android](http://developer.android.com/guide/topics/providers/document-provider.html) để có quyền truy cập vào các vùng bộ nhớ bổ sung. SD Maid sẽ yêu cầu bạn cấp quyền truy cập vào những đường dẫn cụ thể mà chỉ có quyền "Quản lý bộ nhớ" thì không truy cập được (Just Android Things™️).

* Nhấn nút `Cấp quyền truy cập` 
* Một cửa sổ mới sẽ mở ra
* Đường dẫn đúng có thể đã được chọn sẵn, nếu không bạn phải điều hướng đến đường dẫn mà SD Maid cần.
* Sau đó nhấn nút `Use this folder` ("Sử dụng thư mục này"), mục thiết lập cho đường dẫn này sẽ được mở khóa và chuyển sang màu xanh lá.

Lặp lại quá trình này cho đến khi tất cả các đường dẫn đều có màu xanh lá.

<a href="/assets/images/setup/saf-1.webp"><img src="/assets/images/setup/saf-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-2.webp"><img src="/assets/images/setup/saf-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-3.webp"><img src="/assets/images/setup/saf-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-4.webp"><img src="/assets/images/setup/saf-4.webp" width="100" alt=""></a>

### SAF: Khắc phục sự cố
{: #saf-troubleshooting }
Dành cho khi mọi thứ không diễn ra như dự kiến.

#### SAF: Truy cập `Android/data|obb`
{: #saf-androiddataobb-access }

Không thể cấp quyền truy cập `Android/data|obb` với các phiên bản mới hơn của ứng dụng `Files` (Tệp) của hệ thống (`com.google.android.documentsui`) trên Android 11 và Android 12. Nếu ứng dụng `Files` đã cài đặt nhắm đến Android 14 (API level 34) trở lên, SD Maid sẽ ẩn yêu cầu truy cập SAF cho các đường dẫn này (giống như trên Android 13+).

Google Play cập nhật ứng dụng `Files` thông qua dịch vụ `Google Play system update` (Bản cập nhật hệ thống của Google Play). Ứng dụng "Files" gốc (bản dựng cho API level 31, ví dụ `aml_doc_310851020 (310851020)`) đi kèm hệ điều hành Android 12 cho phép SD Maid mở cửa sổ chọn đường dẫn SAF tại `Android/Data`, trong khi ứng dụng "Files" mới nhất (bản dựng cho API level 34 trở lên, ví dụ `14-10492947 (340916000)`) ngăn chặn điều này.

**Cách khắc phục tạm thời:**

Bạn có thể gỡ cài đặt bản cập nhật của `Files` rồi thiết lập SD Maid. Cập nhật lại ứng dụng `Files` sau đó sẽ không làm mất các quyền đã cấp.

* Mở phần cài đặt của thiết bị
* Vào "Apps & notifications" (Ứng dụng và thông báo) hoặc "Application Manager" (Trình quản lý ứng dụng)
* Tìm ứng dụng hệ thống `Files`
* Nhấn vào menu ba chấm, thường ở góc trên bên phải
* Chọn "Uninstall updates" (Gỡ cài đặt bản cập nhật)

hoặc

_Bạn cũng có thể chạy lệnh này bằng [Web ADB Helper](https://d4rken.github.io/web-adb/)._

`adb uninstall com.google.android.documentsui`

#### SAF: OPEN_DOCUMENT_TREE ActivityNotFoundException
{: #saf-open_document_tree-activitynotfoundexception }

Việc cấp quyền truy cập đường dẫn qua Storage Access Framework cần một ứng dụng cho phép người dùng xác nhận những đường dẫn sẽ được cấp quyền truy cập. Ứng dụng này là ứng dụng "Documents" (Tài liệu). Đây là một ứng dụng hệ thống được tích hợp sẵn trên mọi thiết bị Android kể từ Android 5.0  (`com.android.documentsui` hoặc `com.google.android.documentsui`). Nếu bạn gặp lỗi này, thì có ai đó đã xóa hoặc vô hiệu hóa ứng dụng này. Trong hầu hết các trường hợp, ai đó đã chạy một công cụ "gỡ bloatware" quá tay và vô hiệu hóa nó. Bạn có thể bật lại nó qua ADB:

_Bạn cũng có thể chạy các lệnh này bằng [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```
adb shell pm enable com.android.documentsui
or
adb shell pm enable com.google.android.documentsui
```

#### SAF: Cửa sổ trống
{: #saf-empty-window }
Nếu cửa sổ dùng để cấp quyền truy cập đường dẫn bị trống, có thể gói trình cung cấp bộ nhớ ngoài (`com.android.externalstorage`) của bạn đã bị vô hiệu hóa (nhiều khả năng do một "công cụ gỡ bloatware" quá tay). Bạn có thể bật lại nó qua ADB:

_Bạn cũng có thể chạy lệnh này bằng [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```
adb shell pm enable com.android.externalstorage
```

Chạy lại `Google Play system update` sẽ khôi phục bản cập nhật. Bạn cũng có thể tự tải xuống một phiên bản đã cập nhật từ [APKMirror.com](https://www.apkmirror.com/apk/google-inc/files-6/).

## Thông báo
{: #notifications }

Không có quyền này, SD Maid sẽ không thể đăng thông báo. Trên một số ROM, điều này có thể ảnh hưởng xấu đến độ tin cậy khi chạy trong nền.

## Khoảng không quảng cáo ứng dụng (App inventory)
{: #app-inventory }

SD Maid cần quyền `QUERY_ALL_PACKAGES` để kiểm tra những ứng dụng nào được cài đặt trên thiết bị của bạn (tức là ["khả năng xem danh mục các ứng dụng đã cài đặt"](https://support.google.com/googleplay/android-developer/answer/10158779?hl=en)). Xem thêm ["Quyền thống kê sử dụng"](/vi/setup#usage-statistics)

Quyền `QUERY_ALL_PACKAGES` là bắt buộc đối với:

* [Tìm kiếm tàn dư (CorpseFinder)](/vi/corpsefinder): Để biết những tệp nào có thể xóa, SD Maid cần biết bạn có cài các ứng dụng có thể cần đến chúng hay không.
* [Dọn dẹp ứng dụng (AppCleaner)](/vi/appcleaner): Xác định bộ nhớ đệm cho từng ứng dụng
* [Kiểm soát ứng dụng (AppControl)](/vi/appcontrol): Có thể liệt kê tất cả các ứng dụng đã cài đặt
* [Phân tích bộ nhớ (StorageAnalyzer)](/vi/storageanalyzer): Gán dung lượng đã dùng cho các ứng dụng đã cài đặt

Quyền này tồn tại từ [Android 11](https://developer.android.com/training/package-visibility) và thường được cấp sẵn theo mặc định. Kể từ Android 14, có một số thiết bị (ví dụ [#1015](https://github.com/d4rken-org/sdmaid-se/issues/1015), [#1354](https://github.com/d4rken-org/sdmaid-se/issues/1354)) mà quyền này không được cấp sẵn theo mặc định hoặc có thể bị thu hồi.

[<img src="/assets/images/setup/app-inventory.webp" width="100">](/assets/images/setup/app-inventory.webp)

### Danh sách ứng dụng không hợp lệ
{: #invalid-app-list }

Trên một số thiết bị, quyền "App list" ("Danh sách ứng dụng", `QUERY_ALL_PACKAGES` / `GET_INSTALLED_APPS`) đã được cấp, nhưng hệ thống vẫn trả về một **danh sách ứng dụng đã cài đặt không đầy đủ**, ví dụ một danh sách thiếu các gói hệ thống cốt lõi như `android`. Khi SD Maid phát hiện điều này, nó sẽ hiển thị cảnh báo **"Invalid app list"** (Danh sách ứng dụng không hợp lệ) và ghi chú **"Có thể là giới hạn của thiết bị"** trên thẻ thiết lập thay vì tiếp tục.

SD Maid cố ý dừng lại ở đây: nếu không thấy được danh sách đầy đủ các ứng dụng đã cài đặt, nó không thể xác định một cách đáng tin cậy những tệp nào thuộc về một ứng dụng vẫn còn được cài đặt. Nếu vẫn tiếp tục, có thể sẽ xóa dữ liệu thuộc về một ứng dụng mà SD Maid đơn giản là không nhìn thấy, vì vậy các tính năng liên quan đến ứng dụng vẫn bị tắt cho đến khi có được danh sách đầy đủ.

**Những gì bạn có thể thử:**

* Mở phần cài đặt hệ thống và đảm bảo SD Maid được cấp quyền "App list" ("Danh sách ứng dụng"). Ở một số nhà sản xuất, công tắc này có tên khác (ví dụ "Query all packages", "Truy vấn tất cả các gói") hoặc nằm trong một màn hình "Privacy" (Quyền riêng tư) hay "Permission manager" (Trình quản lý quyền) riêng.
* Kiểm tra xem tính năng tiết kiệm pin, bảo vệ quyền riêng tư hay "ẩn ứng dụng" tích hợp sẵn có đang lọc danh sách ứng dụng mà SD Maid thấy được hay không, và loại trừ SD Maid khỏi tính năng đó.
* Khởi động lại thiết bị và mở lại SD Maid để trạng thái quyền được đánh giá lại.

Nếu quyền "App list" ("Danh sách ứng dụng") đã được cho phép mà danh sách vẫn bị báo là không hợp lệ, thì đây thường là một **giới hạn của thiết bị** chứ không phải điều bạn có thể khắc phục: một số hệ thống (ví dụ HarmonyOS, hoặc các bản Android bị sửa đổi nhiều) không bao giờ cung cấp danh sách ứng dụng đầy đủ cho các ứng dụng bên thứ ba. Xem [#2441](https://github.com/d4rken-org/sdmaid-se/issues/2441) để biết một ví dụ.

Trong trường hợp đó, các công cụ liên quan đến ứng dụng ([Tìm kiếm tàn dư](/vi/corpsefinder), [Dọn dẹp ứng dụng](/vi/appcleaner), [Kiểm soát ứng dụng](/vi/appcontrol)) vẫn bị tắt để bảo vệ dữ liệu của bạn, nhưng [Phân tích bộ nhớ](/vi/storageanalyzer) vẫn hoạt động: nó cho thấy bộ nhớ của bạn đang dùng bao nhiêu dung lượng, chỉ là với ít chi tiết hơn: dung lượng thư mục được hiển thị ở chế độ chỉ đọc và không thể gán cho từng ứng dụng riêng lẻ.

## Dịch vụ trợ năng
{: #accessibility-service }
Dịch vụ trợ năng cho phép SD Maid tương tác với thiết bị của bạn như con người, tức là xem màn hình và nhấn nút. SD Maid có thể dùng nó để tự động hóa những thao tác tẻ nhạt như nhấn "Clear cache" ("Xóa bộ nhớ đệm") trên hàng trăm ứng dụng. Dịch vụ trợ năng là một dịch vụ mà chính ứng dụng không thể tự khởi chạy, nó phải được hệ thống khởi chạy, vì loại dịch vụ này cấp cho ứng dụng thêm quyền truy cập vào thiết bị của bạn.

### ACS: Khắc phục sự cố
{: #acs-troubleshooting }

Dịch vụ có thể ngừng chạy và yêu cầu bạn bật lại trong nhiều trường hợp:
* Bạn cập nhật ứng dụng.
* Ứng dụng bị crash. Nếu điều này xảy ra, bạn nên báo cáo đó là một lỗi. Nếu điều này xảy ra quá thường xuyên, bạn có thể không khởi động lại được dịch vụ bằng cách tắt/bật lại cho đến khi khởi động lại thiết bị.
* Hệ thống tắt ứng dụng do "tối ưu hóa pin" quá gắt gao (xem thêm <https://dontkillmyapp.com/>)
* Ứng dụng bảo mật riêng của nhà sản xuất tắt dịch vụ (xem [Bị Phone Manager chặn](#acs-blocked-by-phone-manager-oneplus-oppo-realme))

#### ACS: Dịch vụ bị vô hiệu hóa
{: #acs-service-is-disabled }

Một số ROM (ví dụ MIUI) nổi tiếng với việc dừng mạnh tay mọi thứ không nằm trong danh sách trắng của nhà sản xuất thiết bị.
Nếu bạn liên tục bị yêu cầu bật lại dịch vụ trợ năng, có thể hệ thống đang vô hiệu hóa nó mỗi khi SD Maid không được sử dụng.

Hãy thử tắt tối ưu hóa pin, và trên các ROM MIUI có thêm một cài đặt tên là "Autostart" (Tự khởi động), hãy bật nó cho SD Maid. Với các thiết bị Xiaomi/POCO/Redmi:

* Vào `Security>Manage applications>Auto start in background` (Bảo mật > Quản lý ứng dụng > Tự khởi động trong nền) và cho phép tùy chọn này cho SD Maid.
* Vào `Security>Increase speed>Lock apps` (Bảo mật > Tăng tốc > Khóa ứng dụng) và chọn SD Maid.


Bạn cũng có thể cân nhắc cấp thêm quyền [cho phép SD Maid tự động khởi động lại dịch vụ](#acs-automatic-service-restart).

[<img src="/assets/images/setup/acs-disabled.webp" width="100">](/assets/images/setup/acs-disabled.webp)

#### ACS: Bị Phone Manager chặn (OnePlus, OPPO, Realme)
{: #acs-blocked-by-phone-manager-oneplus-oppo-realme }

Trên OxygenOS và ColorOS, ứng dụng "Phone Manager" (Trình quản lý điện thoại) được cài sẵn có thể tự tắt dịch vụ trợ năng, thường là trong vòng một giây sau khi quá trình dọn dẹp kết thúc. Sau đó, công tắc trợ năng của Android thực sự bị tắt, nên trông như thể SD Maid ngừng hoạt động giữa chừng.

Tắt tính năng này trong `Phone Manager>Utilities>Block suspicious app activities` (Trình quản lý điện thoại > Tiện ích > Chặn hoạt động đáng ngờ của ứng dụng).

Chính màn hình đó lưu một danh sách những gì nó đã chặn. Các mục có nhãn "Abnormal device control" (Điều khiển thiết bị bất thường) với dấu thời gian trùng với các lần dọn dẹp bạn đã chạy xác nhận đây là nguyên nhân. Công tắc "Blocking alerts" (Cảnh báo chặn) bên cạnh chỉ kiểm soát việc bạn có được thông báo hay không, nên khi tắt cảnh báo, việc này diễn ra một cách âm thầm.

#### ACS: Bật qua ADB
{: #acs-enable-via-adb }

Nếu thiết bị của bạn không có cách bật dịch vụ trợ năng qua màn hình (ví dụ thiết bị TV), có thể bật dịch vụ bằng ADB bằng cách chạy:

_Bạn cũng có thể chạy lệnh này bằng [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell settings put secure enabled_accessibility_services eu.darken.sdmse/.automation.core.AutomationService
```

#### ACS: Tự động khởi động lại dịch vụ
{: #acs-automatic-service-restart }

_Lưu ý: Đây là một kỹ thuật nâng cao, dành cho người dùng chuyên sâu._

Bạn có thể cấp thêm một quyền cho phép SD Maid tự khởi động lại dịch vụ.
Quyền này có tên là `WRITE_SECURE_SETTINGS` và chỉ có thể được cấp [qua ADB](https://www.google.com/search?q=android+adb).
Nếu bạn đã thiết lập ADB, hãy chạy lệnh này để cấp quyền:

_Bạn cũng có thể chạy lệnh này bằng [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell pm grant eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

Khi đã được cấp quyền này, SD Maid có thể dùng lệnh [được mô tả ở đây](#acs-enable-via-adb) để bật dịch vụ khi cần thiết.
SD Maid chỉ dùng cách này nếu bạn đã bật dịch vụ trợ năng thủ công ít nhất một lần.
Nếu bạn vào lại phần thiết lập và yêu cầu SD Maid không dùng dịch vụ trợ năng, kỹ thuật tự động khởi động lại này sẽ không được dùng cho đến khi bạn bật lại dịch vụ trợ năng thủ công.

Để thu hồi quyền này khỏi SD Maid, hãy dùng lệnh sau:

_Bạn cũng có thể chạy lệnh này bằng [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell pm revoke eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

Nếu bạn gặp lỗi `Operation not allowed: java.lang.SecurityException`, [xem tại đây](https://stackoverflow.com/questions/52079343). Một số thiết bị hạn chế quyền ADB. Hãy xem có cài đặt bổ sung nào (ví dụ `Disable Permission Monitoring`) trong phần tùy chọn nhà phát triển cần được bật/tắt hay không; sau khi chạy lệnh, bạn có thể tắt lại nó.

#### ACS: Advanced Protection
{: #acs-advanced-protection }

Trên Android 17 trở lên, chế độ [**Advanced Protection**](https://developer.android.com/privacy-and-security/advanced-protection-mode) (Bảo vệ nâng cao) của Google chặn các ứng dụng không phải là công cụ trợ năng chuyên dụng (trình đọc màn hình, tiếp cận bằng công tắc, v.v.) sử dụng dịch vụ trợ năng. SD Maid là một công cụ bảo trì/dọn dẹp, nên khi Advanced Protection đang bật, dịch vụ trợ năng của nó **không thể** được bật: Android từ chối dịch vụ ở cấp hệ thống, ngay cả khi bạn cố cấp nó qua root hoặc quyền truy cập ADB.

SD Maid không thể tự miễn trừ khỏi việc này: Chỉ những ứng dụng được Google phân loại là công cụ trợ năng mới đủ điều kiện, và các ứng dụng dọn dẹp bị loại trừ một cách rõ ràng.

Bạn có hai lựa chọn:

- **Dùng root hoặc quyền truy cập ADB** Khi đã thiết lập root hoặc quyền truy cập ADB, Dọn dẹp ứng dụng (AppCleaner) của SD Maid sẽ xóa bộ nhớ đệm ứng dụng thông qua một lệnh gọi hệ thống trực tiếp và **không** cần dịch vụ trợ năng. Với **root**, điều này cũng áp dụng cho việc dọn dẹp từng ứng dụng được chọn riêng lẻ. Với **quyền truy cập ADB**, một lần chạy "dọn dẹp tất cả ứng dụng" đầy đủ sẽ hoạt động mà không cần dịch vụ trợ năng, nhưng việc xóa bộ nhớ đệm của các ứng dụng *cụ thể* được chọn vẫn phụ thuộc vào nó. Thiết lập điều này trong phần Thiết lập của SD Maid → [Truy cập root](#root-access) hoặc [Truy cập ADB](#shizuku).
- **Tắt Advanced Protection** nếu bạn đặc biệt muốn dọn dẹp dựa trên trợ năng. Tùy chọn này nằm trong `Settings → Security & privacy → Advanced Protection` (Cài đặt → Bảo mật và quyền riêng tư → Bảo vệ nâng cao; đường dẫn chính xác khác nhau tùy thiết bị). Bật lại nó sau này sẽ vô hiệu hóa dịch vụ trợ năng một lần nữa.

#### ACS: Hạn chế AppOps
{: #acs-appops-restrictions }

Android 13+ có thể áp dụng thêm các hạn chế đối với những ứng dụng được cài đặt thủ công (sideload). Vì vậy, nếu SD Maid không được cài từ một nguồn đã biết (đối với Google) như Google Play hoặc các cửa hàng ứng dụng khác, bạn có thể không cấp trực tiếp được một số quyền cho SD Maid, ví dụ khởi động dịch vụ trợ năng.

##### OxygenOS 15 - OnePlus
{: #oxygenos-15---oneplus }

OnePlus đã quên (hoặc đơn giản là không) đưa thao tác `Allow restricted settings` (Cho phép cài đặt bị hạn chế) vào phần cài đặt ứng dụng trong OxygenOS 15. Nếu bạn không cài đặt qua Google Play, trình cài đặt cần đặt cờ `INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS`. Nếu bạn đã thiết lập Shizuku, bạn có thể dùng [ứng dụng "Install with Options"](https://github.com/zacharee/InstallWithOptions).


##### Các ROM khác
{: #other-roms }

Để gỡ bỏ các hạn chế này, hãy mở màn hình cài đặt ứng dụng của SD Maid trong hệ thống. Mở menu ở góc trên bên phải và chọn `Allow restricted settings` (Cho phép cài đặt bị hạn chế), sau đó bạn có thể quay lại danh sách dịch vụ trợ năng và bật dịch vụ của SD Maid.

**Lưu ý:**
Để cải thiện trải nghiệm sử dụng (đây là mỉa mai đấy), Google đã quyết định rằng cài đặt này chỉ hiển thị nếu:

* Bạn đã thấy thông báo "Restricted setting" (Cài đặt bị hạn chế) ít nhất một lần sau khi nhấn vào mục dịch vụ trợ năng của SD Maid
* Bạn đã mở phần cài đặt hệ thống và tự điều hướng đến trang cài đặt của SD Maid, chứ không phải thông qua ứng dụng khác hoặc lối tắt.

😮‍💨


Một số thông tin nền bổ sung [có thể xem tại đây](https://blog.esper.io/android-13-sideloading-restriction-harder-malware-abuse-accessibility-apis/).

<a href="/assets/images/setup/acs-restricted-settings-1.webp"><img src="/assets/images/setup/acs-restricted-settings-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-2.webp"><img src="/assets/images/setup/acs-restricted-settings-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-3.webp"><img src="/assets/images/setup/acs-restricted-settings-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-4.webp"><img src="/assets/images/setup/acs-restricted-settings-4.webp" width="100" alt=""></a>

## Truy cập root
{: #root-access }

Với quyền root, SD Maid có toàn quyền truy cập vào thiết bị của bạn và có thể thực hiện lần quét kỹ lưỡng nhất có thể. Thường thì điều này có nghĩa là bạn đã mở khóa bootloader và cài đặt Magisk.

Nếu SD Maid cho rằng thiết bị của bạn có thể đã được root, SD Maid sẽ yêu cầu quyền root trước tiên. Nếu quyền root được cấp, SD Maid sẽ dùng quyền root để tự cấp càng nhiều quyền khác càng tốt. Quyền root là mức truy cập cao nhất mà bạn có thể cấp cho một ứng dụng; khi đó các quyền khác chỉ cần thiết để thuận tiện sử dụng và cải thiện hiệu năng, đó là lý do chúng không cần thêm sự đồng ý trong quá trình thiết lập nếu SD Maid đã có root.

## Truy cập ADB
{: #shizuku }

Quyền truy cập ADB cho phép SD Maid dùng giao diện gỡ lỗi của Android (ADB) cho các thao tác cần quyền nâng cao. Nó cần một ứng dụng riêng chạy dịch vụ ADB trên thiết bị của bạn và cấp cho các ứng dụng khác quyền truy cập vào dịch vụ đó: [Porter](#porter) hoặc [Shizuku](#using-shizuku). Đó là một sự sửa đổi bạn có thể thực hiện trên điện thoại mà các ứng dụng khác có thể tận dụng.

Nó tương tự root, nhưng không giống hệt. Các quyền có phần kém mạnh hơn một chút, nhưng sự sửa đổi này không vĩnh viễn và không yêu cầu bạn mở khóa bootloader.

### Porter
{: #porter }

[Porter](https://porter.darken.eu) là bản fork của Shizuku do tôi duy trì, có hỗ trợ root tùy chọn. Trang web của nó giải thích cách cài đặt và khởi động. Trên Android 11 trở lên, gỡ lỗi không dây cho phép bạn khởi động nó mà không cần máy tính.

Nếu bạn thiết lập quyền truy cập ADB lần đầu tiên, hãy dùng Porter. Các bản dựng SD Maid từ GitHub và F-Droid sẽ hướng bạn đến Porter khi chưa có ứng dụng truy cập ADB nào được cài đặt. Bản dựng Google Play hướng bạn đến Shizuku trên Google Play, nhưng vẫn hoạt động với Porter theo cách tương tự.

Nếu cả Porter và Shizuku đều được cài đặt, SD Maid sẽ dùng Porter. Khi đó thẻ thiết lập sẽ hiển thị `Porter được cài đặt, vì vậy SD Maid chờ Porter thay vì Shizuku`. Hãy khởi động Porter để tiếp tục, hoặc gỡ cài đặt nó nếu bạn muốn SD Maid dùng Shizuku.

### Sử dụng Shizuku
{: #using-shizuku }

Có hai bản dựng của Shizuku:

* [Bản fork của thedjchi](https://github.com/thedjchi/Shizuku) ([hướng dẫn thiết lập](https://github.com/thedjchi/Shizuku/wiki/setup)) đang được duy trì tích cực và được báo cáo là hoạt động trên các thiết bị mà bản gốc [không bao giờ kết nối được](#shizuku-never-connects).
* [Bản gốc của RikkaApps](https://shizuku.rikka.app/guide/setup/) vẫn hoạt động tốt trên hầu hết các thiết bị.

Không thể cài đặt cả hai cùng lúc. Nếu bản gốc đã hoạt động tốt với bạn, không cần chuyển đổi.

### Cấp quyền truy cập
{: #granting-access }

Sau khi bạn đã thiết lập Porter hoặc Shizuku:

* Trong phần thiết lập của SD Maid, chọn `Thử sử dụng quyền truy cập ADB`. Đây là tùy chọn nội bộ của SD Maid, bạn có thể dùng nó để bật/tắt việc SD Maid sử dụng quyền truy cập ADB
* Sau khi bạn đồng ý, một hộp thoại từ Porter hoặc Shizuku sẽ hỏi có cho phép SD Maid sử dụng nó hay không
* Xác nhận hộp thoại
* Thẻ thiết lập `Truy cập ADB` cho biết SD Maid có thể kết nối hay không

Thiết lập quyền truy cập ADB khi bạn đã cấp quyền root cho SD Maid sẽ không mở thêm tính năng nào, nhưng cải thiện độ tin cậy. Nếu các thao tác thất bại với quyền root, SD Maid sẽ thử lại với quyền truy cập ADB.

### Truy cập ADB: Khắc phục sự cố
{: #shizuku-troubleshooting }

Bạn có thể kiểm tra trạng thái truy cập ADB của SD Maid bằng cách vào `Cài đặt > Thiết lập` và xem thẻ thiết lập `Truy cập ADB`. Thẻ sẽ hiển thị `SD Maid bây giờ có thể sử dụng quyền truy cập ADB qua Porter` (hoặc `qua Shizuku`).

* `Porter chưa cho phép SD Maid`: hãy xác nhận hộp thoại truy cập, hoặc cho phép SD Maid trong ứng dụng Porter (hoặc Shizuku).
* `Porter từ chối quyền truy cập SD Maid`: SD Maid đã bị từ chối vĩnh viễn. Hãy mở Porter (hoặc Shizuku) và cho phép SD Maid ở đó.
* `Cập nhật Porter để sử dụng nó với SD Maid` hoặc `Phiên bản SD Maid này quá cũ cho Porter`: hai ứng dụng không có phiên bản tương thích với nhau. Hãy cập nhật ứng dụng được nêu tên trên thẻ.

#### Shizuku không bao giờ kết nối được
{: #shizuku-never-connects }

Thẻ hiển thị `Truy cập ADB thất bại` và `Shizuku đang chạy, nhưng nó chưa bao giờ kết nối SD Maid với dịch vụ trợ giúp của nó.` Các phiên bản cũ hơn hiển thị `Waiting for link with Shizuku` (Đang chờ liên kết với Shizuku) hoặc `Shizuku link failed` (Liên kết Shizuku thất bại).

Mở ứng dụng Shizuku. Nếu nó cho biết đang chạy và SD Maid nằm trong danh sách được ủy quyền, thì Shizuku đã được thiết lập đúng và việc thiết lập lại sẽ không giúp ích gì. Thứ đang thất bại là dịch vụ trợ giúp của Shizuku: nó chết ngay khi khởi động trước khi kịp phản hồi, và SD Maid bỏ cuộc sau khoảng 15 giây. `Thử lại` chạy lại cùng một bước kiểm tra và thất bại theo cùng một cách.

Đây là lỗi của Shizuku ([RikkaApps/Shizuku#1198](https://github.com/RikkaApps/Shizuku/issues/1198)), không phải của SD Maid, và nó làm hỏng mọi ứng dụng sử dụng dịch vụ trợ giúp của Shizuku. Lỗi này ảnh hưởng đến các thiết bị MediaTek, và các thiết bị chạy HyperOS hoặc MIUI.

[Bản fork của thedjchi](https://github.com/thedjchi/Shizuku) được báo cáo là đã sửa lỗi này. Để chuyển đổi:

* Gỡ cài đặt bản gốc trước, hai bản không thể cùng tồn tại
* Hủy ghép nối nó khỏi gỡ lỗi không dây nếu bạn đã dùng cách đó
* Cài đặt bản fork từ trang [phát hành](https://github.com/thedjchi/Shizuku/releases) của nó và làm theo [hướng dẫn thiết lập](https://github.com/thedjchi/Shizuku/wiki/setup) của nó
* Cấp lại quyền Shizuku cho SD Maid và mọi ứng dụng khác đã dùng nó
