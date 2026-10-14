---
title: "Phân tích bộ nhớ"
lang: vi
translation_key: storageanalyzer
permalink: /vi/storageanalyzer
source_hash: 2dbf8557900cc687
---
# Phân tích bộ nhớ (StorageAnalyzer)
{: #storage-analyzer }

Phân tích bộ nhớ (StorageAnalyzer) trả lời câu hỏi "Cái gì đang chiếm nhiều dung lượng như vậy trên bộ nhớ của tôi?"

Nó có thể trông giống một trình duyệt tệp, nhưng nó không phải là trình duyệt tệp. Mọi thứ đều được sắp xếp theo dung lượng, nên bạn có thể lần theo các mục lớn nhất để tìm ra thứ thực sự đang làm đầy bộ nhớ của mình.

## Bộ nhớ
{: #storages }

Màn hình đầu tiên hiển thị từng bộ nhớ trên thiết bị của bạn:

* **Bộ nhớ chính**: bộ nhớ tích hợp của thiết bị, bao gồm ứng dụng và dữ liệu hệ thống.
* **Bộ nhớ thứ cấp**: thường là thẻ SD tháo rời được.
* **Bộ lưu trữ di động**: ví dụ một ổ flash được kết nối qua USB.

Mỗi thẻ hiển thị dung lượng trống và đã dùng, cùng mức thay đổi của bộ nhớ trong 7 ngày qua, xem [Xu hướng bộ nhớ](#storage-trend).

## Danh mục
{: #categories }

Mở một bộ nhớ sẽ quét nó và chia dung lượng đã dùng thành các danh mục:

* **Ứng Dụng**: các ứng dụng đã cài đặt cùng mã, dữ liệu, bộ nhớ đệm và media của chúng.
* **Tập tin người dùng**: ảnh, video, tệp tải xuống và mọi dữ liệu khác không thuộc về một ứng dụng cụ thể.
* **Dữ liệu hệ thống**: hệ điều hành và mọi thứ không thể gán vào nơi khác.
* **Người dùng khác**: ứng dụng và tệp của những người dùng khác hoặc hồ sơ công việc trên thiết bị này.

Dữ liệu hệ thống và Người dùng khác chỉ có ở bộ nhớ chính.

### Ứng dụng
{: #apps }

Các ứng dụng được sắp xếp theo dung lượng và có thể tìm kiếm theo tên. Ứng dụng hệ thống cũng có thể được liệt kê ở đây, kèm theo dữ liệu của chúng. Với các ứng dụng hệ thống chưa cài bản cập nhật, mã ứng dụng tích hợp sẵn được tính vào Dữ liệu hệ thống. Mở một ứng dụng sẽ cho thấy dung lượng của nó được dùng vào đâu:

* **Mã ứng dụng**: chính ứng dụng đó. Gỡ cài đặt hoặc lưu trữ ứng dụng sẽ giải phóng dung lượng này.
* **Dữ liệu ứng dụng**: dữ liệu, cài đặt và bộ nhớ đệm.
* **Phương tiện ứng dụng**: các tệp media của ứng dụng này, ví dụ ảnh WhatsApp đã nhận.
* **Tệp ứng dụng bổ sung**: dữ liệu mà ứng dụng đặt bên ngoài các thư mục của chính nó, ví dụ ảnh hoặc tệp tải xuống trong một thư mục cấp cao nhất.

`Hiển thị cài đặt` mở trang cài đặt hệ thống của ứng dụng.

### Vì sao các con số không khớp với phần cài đặt của Android
{: #why-the-numbers-dont-match-androids-settings }

Các danh mục của Phân tích bộ nhớ sẽ không khớp chính xác với cách phân chia trong phần cài đặt của Android.

**Dữ liệu hệ thống** là phần dung lượng đã dùng còn lại sau khi đã tính Ứng Dụng, Tập tin người dùng và Người dùng khác. Mọi thứ SD Maid không thể thấy hoặc không thể gán đều nằm ở đó: các vùng hệ thống được bảo vệ, bộ nhớ của những người dùng khác không đo được, phần chi phí của hệ thống tệp, và phần chênh lệch giữa dung lượng quảng cáo và dung lượng thực tế của bộ nhớ.

Nếu không có quyền [Khoảng không quảng cáo ứng dụng (App inventory)](/vi/setup#app-inventory) và [Thống kê sử dụng](/vi/setup#usage-statistics), các ứng dụng không thể được đo. Khi đó dung lượng của chúng sẽ hiển thị là Dữ liệu hệ thống.

## Quyền truy cập
{: #access }

Những gì Phân tích bộ nhớ có thể thấy phụ thuộc vào quyền truy cập mà SD Maid có:

* **Không có root**, mã ứng dụng và dữ liệu ứng dụng riêng tư chỉ được hiển thị dưới dạng tổng và không thể duyệt vào bên trong. Trên Android 13 trở lên, `Android/data` cũng cần root hoặc quyền truy cập ADB.
* **Có [root](/vi/setup#root-access) hoặc [quyền truy cập ADB](/vi/setup#shizuku)**, bạn có thể duyệt Dữ liệu hệ thống. Root còn cho phép duyệt mã ứng dụng và dữ liệu ứng dụng riêng tư, và đo đầy đủ những người dùng khác.
* **Không có quyền Khoảng không quảng cáo ứng dụng (App inventory)**, các tệp không thể được ghép với ứng dụng sở hữu chúng. Khi đó Tập tin người dùng chỉ được hiển thị ở chế độ chỉ đọc: bạn có thể xem dung lượng, nhưng việc xóa, loại trừ, tạo bộ lọc và tạo phiên Vuốt (Swiper) đều bị tắt.

Các mục SD Maid không thể truy cập sẽ hiển thị `Không có quyền truy cập trực tiếp` khi được nhấn vào.

Các thư mục có hơn 100.000 mục chỉ được tính tổng và không thể duyệt.

## Thao tác
{: #actions }

Nhấn vào một tệp để mở nó trong ứng dụng khác. Nhấn giữ các mục để chọn chúng, rồi:

* **Xoá**: xóa các mục đã chọn sau khi xác nhận. Dùng miễn phí.
* **Loại trừ**: tạo một [loại trừ](/vi/exclusions) cho các đường dẫn đã chọn. Các loại trừ này áp dụng cho mọi công cụ. Bản thân Phân tích bộ nhớ không dùng loại trừ, vì nó cần thấy mọi thứ để tính đúng tổng dung lượng.
* **Bộ lọc**: tạo một [bộ lọc tùy chỉnh của Dọn dẹp hệ thống](/vi/systemcleaner#custom-filters) cho các mục đã chọn. Cần bản nâng cấp.
* **Tạo phiên Swiper**: bắt đầu một phiên [Vuốt](/vi/swiper) với các mục đã chọn.

Dữ liệu hệ thống và nội dung của người dùng khác chỉ có thể được duyệt. Không thể xóa ở đó.

## Xu hướng bộ nhớ
{: #storage-trend }

SD Maid ghi lại dung lượng trống của các bộ nhớ vài giờ một lần và sau mỗi lần dọn dẹp. `Xu hướng bộ nhớ` hiển thị dữ liệu này dưới dạng biểu đồ, với các điểm đánh dấu cho biết mỗi lần dọn dẹp đã giải phóng bao nhiêu.

* Các thẻ bộ nhớ cho biết mỗi bộ nhớ đã thay đổi bao nhiêu trong 7 ngày qua.
* Bảng điều khiển hiển thị tổng mức thay đổi của tất cả bộ nhớ, hoặc thay vào đó là ước tính số ngày còn lại khi bộ nhớ đang đầy dần.
* Thời gian lưu giữ các bản ghi nhanh được đặt trong `Cài đặt > Lịch sử > Lịch sử dung lượng`. Mặc định 90 ngày, tối đa 365.

Biểu đồ Xu hướng bộ nhớ cần bản nâng cấp. Mức thay đổi 7 ngày trên các thẻ bộ nhớ và bảng điều khiển, cùng ước tính số ngày còn lại, là miễn phí.

## Cài đặt
{: #settings }

### Ngưỡng bộ nhớ thấp
{: #low-storage-threshold }

Khi dung lượng trống giảm xuống dưới mức này, SD Maid coi bộ nhớ là sắp hết. Mặc định là `Tự động`: 5% dung lượng bộ nhớ, tối đa 2 GB.

### Cảnh báo bộ nhớ thấp
{: #low-space-warning }

Cần bản nâng cấp. Cảnh báo khi dung lượng trống trên bộ nhớ chính đạt đến ngưỡng bộ nhớ thấp. Khi có ít nhất 5 ngày lịch sử liên tục, tính năng này cũng có thể cảnh báo sớm hơn: khi bộ nhớ đang có xu hướng chạm ngưỡng trong vòng 14 ngày và dung lượng trống đã còn tối đa 20% dung lượng bộ nhớ, giới hạn ở mức 25 GB. Mặc định tắt.
