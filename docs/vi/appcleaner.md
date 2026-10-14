---
title: "Dọn dẹp ứng dụng"
lang: vi
translation_key: appcleaner
permalink: /vi/appcleaner
source_hash: 505b031f74bf71a2
---
# Dọn dẹp ứng dụng (AppCleaner)
{: #app-cleaner }

Công cụ Dọn dẹp ứng dụng (AppCleaner) là một công cụ nhận biết ứng dụng, chuyên tìm các tệp "có thể bỏ đi". Một tệp được coi là có thể bỏ đi nếu nó có thể bị xóa mà không gây tác động xấu, và hoặc là không cần thiết, hoặc sẽ được tạo lại khi cần. Hầu hết các loại tệp trong "bộ nhớ đệm" (cache) đều thuộc định nghĩa này.

## Cài đặt
{: #settings }

### Bao gồm các ứng dụng hệ thống
{: #include-system-apps }

Cài đặt này sẽ đưa các ứng dụng hệ thống vào kết quả. Tùy chọn này không được bật mặc định vì một số ứng dụng hệ thống (tùy theo ROM) phản ứng xấu khi bộ nhớ đệm của chúng bị xóa. Một số ứng dụng hệ thống cũng không có nút "Xóa bộ nhớ đệm", điều này có thể khiến SD Maid bị kẹt khi xóa bộ nhớ đệm qua dịch vụ trợ năng.

### Bao gồm những người dùng khác
{: #include-other-users }

Cần root. Nếu được bật, SD Maid sẽ tìm kiếm cả trong thư mục của những người dùng khác, ngoài người dùng hiện đang chạy SD Maid.

### Bao gồm các app đang chạy
{: #include-running-apps }

Có đưa các ứng dụng đang chạy vào hay không.

### Kích thước tối thiểu
{: #minimum-size }

Bộ nhớ đệm của một ứng dụng phải lớn đến mức nào thì mới được liệt kê. Đặt kích thước tối thiểu là một sự cân bằng tốt giữa tính hữu ích và tốc độ. Xóa bộ nhớ đệm của một ứng dụng khi nó chỉ có vài KB thường không mang lại lợi ích gì. Điều này áp dụng cho tổng dung lượng bộ nhớ đệm tìm thấy của một ứng dụng (tính theo tất cả các bộ lọc đang bật)

### Thời gian tối thiểu
{: #minimum-age }

Có thể dùng để chỉ xóa các tệp bộ nhớ đệm cũ. Được áp dụng cho từng tệp hoặc thư mục trong kết quả.

### Bao gồm các bộ đệm không thể truy cập
{: #include-inaccessible-caches }

Tùy theo quyền mà SD Maid có, một số tệp có thể không truy cập trực tiếp được. Thường thì đây là bộ nhớ đệm mặc định riêng tư của ứng dụng (ví dụ `/data/data/eu.darken.sdmse/cache`). Nếu cài đặt này được bật, SD Maid sẽ thử dùng dịch vụ trợ năng để điều hướng đến phần cài đặt hệ thống và nhấn nút `Clear cache` ("Xóa bộ nhớ đệm") của ứng dụng đích.

### Bộ lọc
{: #filter }

Bạn có thể bật/tắt các bộ lọc khác nhau để điều chỉnh những kết quả mà công cụ Dọn dẹp ứng dụng sẽ tìm ra.

#### Bộ lọc chung
{: #common-filters }

Bộ lọc chung nhắm đến nhiều loại tệp khác nhau trên tất cả ứng dụng.

#### Bộ lọc cụ thể
{: #specific-filters }

Bộ lọc cụ thể nhắm đến các ứng dụng cụ thể, ví dụ WhatsApp hoặc Telegram.

## Khắc phục sự cố
{: #troubleshooting }
### `com.miui.securitycenter`
{: #commiuisecuritycenter }
#### Thiếu quyền `GET_USAGE_STATS`
{: #missing-get_usage_stats-permission }

MIUI và HyperOS dùng thêm một ứng dụng tên là "Security" hoặc "Security Center" ("Bảo mật", tên gói: `com.miui.securitycenter`) để hiển thị thông tin hệ thống của một ứng dụng. Trình tự thao tác thông thường để xóa bộ nhớ đệm của một ứng dụng là: Nhấn `Clear data` ("Xóa dữ liệu"), chọn `Clear cache` ("Xóa bộ nhớ đệm"), xác nhận thao tác.

Nếu nút 'Clear data' ("Xóa dữ liệu") bị mờ đi ở tất cả ứng dụng và không thể nhấn được, thì ứng dụng "Security Center" đang thiếu quyền `GET_USAGE_STATS`. Bình thường quyền này được cấp sẵn theo mặc định. Không rõ vì sao điều này xảy ra, nhưng cách khắc phục là cấp lại quyền đó.

Đi đến: `System Settings` > `Security and privacy` > `More security and privacy` > `Special permissions` > `Usage access` (Cài đặt hệ thống > Bảo mật và quyền riêng tư > Bảo mật và quyền riêng tư khác > Quyền đặc biệt > Quyền truy cập dữ liệu sử dụng) > Tìm ứng dụng `Security` ("Bảo mật") > Cấp quyền

Ngoài ra, nếu bạn có quyền truy cập ADB trở lên, hãy chạy lệnh `pm grant com.miui.securitycenter android.permission.PACKAGE_USAGE_STATS`

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
