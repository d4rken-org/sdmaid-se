---
title: "Quản lý tệp trùng lặp"
lang: vi
translation_key: deduplicator
permalink: /vi/deduplicator
source_hash: 08f0bb39baf9fb1e
---
# Quản lý tệp trùng lặp (Deduplicator)
{: #deduplicator }

Quản lý tệp trùng lặp (Deduplicator) tìm các tệp trùng lặp trên thiết bị của bạn: các bản sao giống hệt nhau, và tùy chọn cả hình ảnh, video hoặc âm thanh trông giống hoặc nghe giống nhau. Công cụ nhóm chúng thành các bộ và xóa tất cả trừ một tệp trong mỗi bộ.

Việc quét là miễn phí. Việc xóa cần bản nâng cấp.

## Phương pháp phát hiện
{: #detection-methods }

Bạn có thể bật từng phương pháp trong `Cài đặt > Quản lý tệp trùng lặp`.

### Tổng kiểm tra nội dung
{: #content-checksum }

Mặc định bật. Tìm các tệp có nội dung giống hệt nhau, từng byte một, bất kể tên của chúng. Đây là phương pháp an toàn: mọi tệp trong một bộ như vậy đều là bản sao thật sự.

### Perceptual hash
{: #perceptual-hash }

Mặc định tắt. Tìm các hình ảnh trông giống nhau, ví dụ cùng một ảnh được lưu ở định dạng hoặc kích thước khác, hoặc có chỉnh sửa nhỏ. Hình ảnh được so sánh ở dạng thang xám, nên những ảnh chỉ khác nhau về màu sắc có thể bị coi là khớp.

### Dấu vân tay phương tiện
{: #media-fingerprint }

Mặc định tắt. Tìm các tệp video và âm thanh nghe giống nhau, và các video không tiếng trông giống nhau. Chỉ những tệp có độ dài gần bằng nhau mới được so sánh.

Các tệp tương tự không phải là tệp giống hệt nhau. Ảnh chụp liên tiếp, phiên bản đã chỉnh sửa hoặc bản sao đã thay đổi kích thước có thể nằm chung một bộ. **Hãy kiểm tra các bộ được tìm thấy bằng Perceptual hash hoặc Dấu vân tay phương tiện trước khi xóa chúng.** Mỗi tệp tương tự đều hiển thị mức độ khớp của nó.

## Những gì được quét
{: #what-gets-scanned }

Theo mặc định, Quản lý tệp trùng lặp tìm kiếm trên toàn bộ bộ nhớ có thể truy cập: bộ nhớ trong, thẻ SD, bộ nhớ USB, `Android/data` và `Android/media`. `Android/obb` không được bao gồm. Bạn có thể giới hạn phạm vi tìm kiếm trong [Vị trí tìm kiếm](#search-locations).

Việc truy cập `Android/data` trên Android 13 trở lên cần [root](/vi/setup#root-access) hoặc [quyền truy cập ADB](/vi/setup#shizuku).

Chỉ những tệp lớn hơn [kích thước tối thiểu](#minimum-size) mới được xem xét. Theo mặc định chỉ các loại tệp phổ biến được bao gồm, xem [Bỏ qua các loại tệp không phổ biến](#skip-uncommon-file-types).

## Tệp nào được giữ lại
{: #which-file-is-kept }

Khi xóa một bộ, SD Maid giữ lại một tệp và xóa phần còn lại. Tệp nào được giữ lại do chiến lược xóa quyết định, trong `Cài đặt > Quản lý tệp trùng lặp > Chiến lược xóa`.

SD Maid kiểm tra lần lượt từng quy tắc theo thứ tự. Quy tắc đầu tiên cho ra một tệp thắng rõ ràng sẽ quyết định. Kéo các quy tắc để sắp xếp lại, nhấn vào một quy tắc để thay đổi chế độ của nó.

| Quy tắc | Mặc định |
|---|---|
| Kiểu trùng lặp | Ưu tiên giữ các tệp khớp chính xác hơn các tệp tương tự |
| Vị trí ưa thích | Giữ các tệp trong những thư mục bạn chọn. Mặc định không có thư mục nào được đặt. |
| Lập chỉ mục Media | Giữ các tệp mà thư viện ảnh và các ứng dụng media khác của bạn biết đến |
| Vị trí lưu trữ | Giữ các tệp trên bộ nhớ trong. Thẻ SD và bộ nhớ USB được tính là bộ nhớ ngoài. |
| Độ sâu của thư mục | Giữ các tệp trong các thư mục nông |
| Ngày sửa đổi | Giữ các tệp cũ hơn |
| Kích thước tệp | Giữ các tệp lớn hơn |

**Vị trí ưa thích** giúp chọn tệp nào được giữ lại trong một nhóm, cùng với các quy tắc khác. Nó không chọn giữa các nhóm. Trong một bộ có cả bản sao giống hệt lẫn tệp tương tự, Kiểu trùng lặp quyết định nhóm nào được giữ lại: theo mặc định, mọi tệp trong nhóm tương tự đều bị xóa, kể cả khi nằm ở vị trí ưa thích. Ví dụ, nếu ảnh chụp từ camera của bạn và hai bản sao giống hệt nhau, đã thay đổi kích thước, từ một ứng dụng nhắn tin nằm chung một bộ, thiết lập mặc định sẽ giữ một bản sao từ ứng dụng nhắn tin và xóa ảnh gốc từ camera. Hãy kiểm tra những bộ như vậy trước khi xóa, và [loại trừ](/vi/exclusions) các thư mục có tệp không bao giờ được phép xóa.

Không có cách nào để đánh dấu một tệp cụ thể là tệp cần giữ lại. Để giữ lại một tệp khác, hãy thay đổi chiến lược xóa, hoặc tự chọn các tệp cần xóa.

Thay đổi chiến lược xóa sẽ hủy bỏ kết quả hiện tại. Hãy quét lại sau đó.

## Xóa
{: #deleting }

### Xóa toàn bộ một bộ
{: #deleting-a-whole-set }

Xóa một bộ sẽ giữ lại một tệp và xóa **mọi tệp khác trong bộ đó**. Trong một bộ có các tệp tương tự, điều này bao gồm cả những tệp chỉ tương tự với tệp được giữ lại, chứ không giống hệt.

Chuyển danh sách kết quả sang bố cục danh sách để thấy biểu tượng xóa trên mỗi tệp sẽ bị xóa khi xóa toàn bộ bộ. Bố cục lưới mặc định không hiển thị các biểu tượng này.

### Xóa từng tệp riêng lẻ
{: #deleting-individual-files }

Chọn các tệp bằng cách nhấn giữ rồi xóa chúng. Mỗi bộ phải còn lại ít nhất một tệp, trừ khi bạn bật [Làm có thể "Xóa tất cả"](#make-delete-all-possible).

Trong phần chi tiết của bộ, chế độ xem `Thư mục` nhóm các tệp theo thư mục. Xóa một thư mục ở đó sẽ xóa các bản trùng lặp trong thư mục đó. Nếu thư mục chứa mọi tệp của một bộ, tệp mà thao tác xóa toàn bộ bộ sẽ giữ lại sẽ được chừa ra. Khi bật [Làm có thể "Xóa tất cả"](#make-delete-all-possible), hộp xác nhận cho phép bạn xóa tất cả chúng.

### Xóa từ bảng điều khiển
{: #deleting-from-the-dashboard }

Xóa từ thẻ Quản lý tệp trùng lặp trên bảng điều khiển sẽ xóa tất cả trừ một tệp trong mỗi bộ, sau khi xác nhận. Việc nút chính của bảng điều khiển có chạy cả Quản lý tệp trùng lặp hay không được đặt trong [Một chạm](#one-tap).

## Loại trừ
{: #exclusions }

Các đường dẫn bị loại trừ cho Quản lý tệp trùng lặp, hoặc bị loại trừ chung, sẽ không được quét. Loại trừ một bộ khỏi kết quả sẽ loại trừ mọi tệp trong bộ đó, kể cả tệp lẽ ra sẽ được giữ lại. Xem [Loại trừ](/vi/exclusions).

Quản lý tệp trùng lặp không thể được chạy bởi [Lịch trình](/vi/scheduler).

## Một chạm
{: #one-tap }

`Cài đặt > Tổng quan > Công cụ một chạm` quyết định nút chính của bảng điều khiển, lối tắt `Quét + Xóa` và tiện ích (widget) có bao gồm Quản lý tệp trùng lặp hay không. Mặc định tùy chọn này tắt. Lối tắt và tiện ích sẽ quét và xóa mà không cần xác nhận, dùng chiến lược xóa của bạn. Trên bảng điều khiển, `Chế độ một chạm` cũng làm điều tương tự, nhưng nếu đã có kết quả đang chờ, nút Xoá vẫn hỏi trước. Chỉ bao gồm Quản lý tệp trùng lặp khi [Tổng kiểm tra nội dung](#content-checksum) là phương pháp phát hiện duy nhất.

## Cài đặt
{: #settings }

### Vị trí tìm kiếm
{: #search-locations }

Các thư mục cần tìm kiếm. Mặc định là tất cả các vùng có thể truy cập. Nhấn giữ để đặt lại.

### Chiến lược xóa
{: #deletion-strategy }

Tệp nào trong một bộ được giữ lại, xem [Tệp nào được giữ lại](#which-file-is-kept).

### Làm có thể "Xóa tất cả"
{: #make-delete-all-possible }

Hiển thị các tùy chọn để xóa mọi tệp trong một bộ, để không còn lại bản sao nào. Mặc định tắt.

### Kích thước tối thiểu
{: #minimum-size }

Bỏ qua các tệp nhỏ hơn kích thước này. Mặc định 512 KB.

### Bỏ qua các loại tệp không phổ biến
{: #skip-uncommon-file-types }

Chỉ bao gồm các tệp hình ảnh, video, âm thanh, tài liệu, tệp lưu trữ và tệp APK phổ biến. Mặc định bật. Loại tệp được xác định dựa trên phần mở rộng của tệp.

### Phương pháp phát hiện
{: #detection-method }

Bật [Tổng kiểm tra nội dung](#content-checksum), [Perceptual hash](#perceptual-hash) và [Dấu vân tay phương tiện](#media-fingerprint).
