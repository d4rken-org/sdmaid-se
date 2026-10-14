---
title: "Kiểm soát ứng dụng"
lang: vi
translation_key: appcontrol
permalink: /vi/appcontrol
source_hash: 1a4a7450fc5b86ca
---
# Kiểm soát ứng dụng (AppControl)
{: #appcontrol }
Kiểm soát ứng dụng (AppControl) là nơi tập trung mọi thao tác liên quan đến việc kiểm soát các ứng dụng đã cài đặt. Sau khi quét, bạn sẽ có một danh sách có thể tìm kiếm gồm các ứng dụng đã cài đặt. Ứng dụng hệ thống mặc định không được hiển thị, bạn phải bật tùy chọn này trong phần cài đặt.
Một số tùy chọn có thể chỉ khả dụng (khả thi) nếu SD Maid đã được cấp quyền root.

Một số thao tác có thể thực hiện trên nhiều ứng dụng, một số chỉ trên từng mục riêng lẻ. Để thực hiện thao tác trên nhiều ứng dụng, hãy nhấn giữ một ứng dụng rồi nhấn để chọn thêm các ứng dụng khác.

## Thao tác
{: #actions }
Một số thao tác này chỉ truy cập được trong phần chi tiết ứng dụng, có thể mở bằng cách nhấn vào một mục trong danh sách.

### Chạy ứng dụng
{: #run-app }
Khởi chạy ứng dụng. Đó là activity chính của nó, thường cũng là thao tác mà trình khởi chạy (launcher) sẽ thực hiện.

### Buộc dừng
{: #force-stop }
Cố gắng buộc dừng ứng dụng. Hoạt động tốt nhất với Root hoặc ADB/Shizuku, nếu không sẽ dùng dịch vụ trợ năng.

### Xuất APK
{: #export-apk }
Cố gắng sao chép tệp `.apk` của ứng dụng từ bộ nhớ riêng tư bên trong sang thư mục tải xuống trong bộ nhớ công khai của bạn. Nếu ứng dụng được mã hóa thì thao tác này cần root, các ứng dụng không mã hóa có thể được xuất mà không cần root.

### Đóng băng ứng dụng
{: #freeze-app }
Cần root hoặc ADB/Shizuku. Vô hiệu hóa ứng dụng. Ứng dụng sẽ không hiển thị và không thể tự khởi động. Ứng dụng phải được bật lại mới hoạt động được. Không xảy ra mất dữ liệu, và trạng thái này vẫn giữ nguyên ngay cả khi bạn gỡ cài đặt SD Maid. Bất kỳ ứng dụng bật/tắt ứng dụng tốt nào cũng có thể bật các ứng dụng đã bị SD Maid vô hiệu hóa và ngược lại.

### Xóa ứng dụng
{: #delete-app }
Xóa ứng dụng và dữ liệu của nó.


## Cài đặt
{: #settings }
### Xác định kích thước
{: #determine-size }
Có thể tắt để tăng tốc độ tải danh sách ứng dụng. Việc xác định dung lượng của một ứng dụng tốn rất nhiều thời gian.

### Xác định hoạt động
{: #determine-activity }
Có thể tắt để tăng tốc độ tải danh sách ứng dụng. Việc xác định một ứng dụng có hoạt động gần đây hay không tốn rất nhiều thời gian.
