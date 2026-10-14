---
title: "Lỗi"
lang: vi
translation_key: bugs
permalink: /vi/bugs
source_hash: a218be31a6c4ae63
---
# Lỗi
{: #bugs }

🐌 🦋 🐛 🐜 🐝 🐞

Lỗi (bug) là một phần cuộc sống của mọi ứng dụng. Chúng không có ý hại bạn, nhưng cũng chẳng được ở lại lâu đâu 😠.

Lỗi là gì?

* Nếu ứng dụng bị crash thì rõ ràng đó là lỗi
* Nếu có gì đó hoạt động không như mong đợi, đó có thể là lỗi.

Trong mọi trường hợp, hãy kiểm tra xem bạn đang dùng bản cập nhật mới nhất chưa, có thể lỗi đã được sửa rồi.

Nếu lỗi chưa được sửa, hãy báo cáo nó :grin:.

## Báo cáo lỗi
{: #reporting-a-bug }

Tạo một issue trên GitHub:
<https://github.com/d4rken-org/sdmaid-se/issues>

* Mô tả điều bạn mong đợi và điều thực sự đã xảy ra
* Liệt kê các bước mà người khác cần làm để tái hiện sự cố
* Mô tả thiết lập của bạn
    * Loại thiết bị của bạn (ví dụ: Pixel 5)
    * Phiên bản Android (ví dụ: Android 13.1)
    * Nếu thiết bị đã root, phiên bản Magisk

Hãy đính kèm một [nhật ký gỡ lỗi](#debug-log) nếu bạn có thể tái hiện sự cố. 

Mẹo: Bạn có thể [nhấn giữ mục "Nhật ký thay đổi"](https://github.com/d4rken-org/sdmaid-se/pull/940) trong phần cài đặt của SD Maid để sao chép phiên bản vào bộ nhớ tạm.

## Nhật ký gỡ lỗi
{: #debug-log }

Nhật ký gỡ lỗi là bản ghi lại các hoạt động của SD Maid. Bạn bắt đầu ghi, tái hiện sự cố, sau đó dừng ghi và có thể chia sẻ một tệp zip chứa diễn biến theo thứ tự thời gian những gì SD Maid đã làm.

Để bắt đầu ghi nhật ký gỡ lỗi:
* Vào phần cài đặt của SD Maid bằng cách nhấn biểu tượng cài đặt ở góc dưới bên phải của bảng điều khiển
* Tìm mục `Hỗ trợ` trong danh sách cài đặt
* Nhấn `Ghi nhật ký gỡ lỗi` trên màn hình hỗ trợ, rồi nhấn `Ghi nhật ký gỡ lỗi` một lần nữa trong hộp thoại
* Trong khi ghi, bảng điều khiển hiển thị thẻ `Ghi nhật ký gỡ lỗi`
* **Bây giờ hãy tái hiện sự cố mà bạn muốn ghi lại**
* Sau khi đã tái hiện sự cố, hãy nhấn `Dừng ghi` trên thẻ đó ở bảng điều khiển hoặc trên màn hình hỗ trợ. Nếu bản ghi rất ngắn, SD Maid sẽ hỏi trước; nhấn `Vẫn dừng lại` để dừng.
* Màn hình `Nhật ký gỡ lỗi` sẽ mở ra. Khi tệp nhật ký đã được nén xong, nhấn `Chia sẻ` để gửi.

Nhật ký gỡ lỗi sẽ được ghi cho đến khi bạn dừng nó, việc ghi sẽ tiếp tục ngay cả khi bạn buộc dừng và khởi động lại SD Maid.

Nếu bạn đang khắc phục các sự cố liên quan đến chức năng cốt lõi, ví dụ phát hiện root hoặc các vùng bộ nhớ được phát hiện, sẽ hữu ích nếu bạn buộc dừng và khởi động lại SD Maid trong khi đang ghi, vì một số dữ liệu chỉ được khởi tạo một lần trong mỗi phiên của ứng dụng.

Các tệp nhật ký gỡ lỗi được lưu trong `/storage/emulated/0/Android/data/eu.darken.sdmse/cache/debug/logs`.

Bằng hình ảnh:

<a href="/assets/images/bugs/debug-log-1.webp"><img src="/assets/images/bugs/debug-log-1.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-2.webp"><img src="/assets/images/bugs/debug-log-2.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-3.webp"><img src="/assets/images/bugs/debug-log-3.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-4.webp"><img src="/assets/images/bugs/debug-log-4.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-5.webp"><img src="/assets/images/bugs/debug-log-5.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-6.webp"><img src="/assets/images/bugs/debug-log-6.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-7.webp"><img src="/assets/images/bugs/debug-log-7.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-8.webp"><img src="/assets/images/bugs/debug-log-8.webp" width="100" alt=""></a>
