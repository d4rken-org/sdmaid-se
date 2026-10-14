---
title: "Lịch trình"
lang: vi
translation_key: scheduler
permalink: /vi/scheduler
source_hash: 74e1452091eaf142
---
# Lịch trình (Scheduler)
{: #scheduler }

Tính năng **Lịch trình (Scheduler)** trong SD Maid 2 cho phép bạn tự động hóa các tác vụ bảo trì theo những khoảng thời gian nhất định, giúp thiết bị luôn được tối ưu mà không cần thao tác thủ công. Tính năng này hỗ trợ nhiều công cụ như **Dọn dẹp ứng dụng (AppCleaner)**, **Tìm kiếm tàn dư (CorpseFinder)** và **Dọn dẹp hệ thống (SystemCleaner)**, có thể được cấu hình để chạy định kỳ hoặc vào những thời điểm cụ thể.

<img src="/assets/images/scheduler/overview-1.webp" width="150">
<img src="/assets/images/scheduler/overview-2.webp" width="150">
<img src="/assets/images/scheduler/overview-3.webp" width="150">
<img src="/assets/images/scheduler/overview-4.webp" width="150">
<img src="/assets/images/scheduler/overview-5.webp" width="150">
<img src="/assets/images/scheduler/overview-6.webp" width="150">


## Cách thiết lập một lịch trình
{: #how-to-set-up-a-schedule }

1. Vào **Cài đặt** và chọn **Lịch trình**.
2. Nhấn nút **+** để tạo lịch trình mới hoặc quản lý các lịch trình hiện có.
3. Chọn những công cụ nào (ví dụ: **Dọn dẹp ứng dụng**, **Tìm kiếm tàn dư**, **Dọn dẹp hệ thống**) sẽ chạy theo lịch.
4. Đặt **khoảng thời gian lặp lại** (ví dụ: mỗi 3 ngày) và **thời gian xấp xỉ** để thực hiện.
5. Bật lịch trình bằng công tắc.

## Chỉnh sửa hoặc xóa lịch trình
{: #editing-or-deleting-schedules }

1. Vào phần cài đặt **Lịch trình** để quản lý các lịch trình của bạn.
2. Bạn có thể sửa các lịch trình hiện có, điều chỉnh khoảng thời gian, hoặc xóa chúng nếu không còn cần.

Khi một lịch trình đang hoạt động, SD Maid sẽ chạy các tác vụ đã chọn vào những thời điểm đã định.

## Tùy chọn bổ sung
{: #additional-options }

Được cấu hình qua menu cài đặt chính: `Cài đặt > Lịch trình`, ở đó có một vài tùy chọn bổ sung.

- **Bỏ qua khi pin yếu**: Nếu thiết bị đang ở chế độ tiết kiệm pin, các tác vụ đã lên lịch sẽ bị bỏ qua.
- **Bỏ qua khi không sạc**: Các tác vụ đã lên lịch chỉ chạy khi thiết bị đang sạc, tránh làm hao pin.
- **Các lệnh sau lịch trình**: Bạn có thể đặt các lệnh shell sẽ được thực thi sau khi các tác vụ đã lên lịch hoàn tất.
- **Dịch vụ trợ năng**: Theo mặc định, Lịch trình sẽ bỏ qua các tính năng dựa trên dịch vụ trợ năng vì chúng không hoạt động khi thiết bị bị khóa. Nếu bạn bật tùy chọn này mà không thay đổi gì khác trên thiết bị, Lịch trình sẽ thất bại. Vì vậy, trừ khi bạn biết mình đang làm gì, hãy để tùy chọn này tắt. Nó chỉ hữu ích trong những trường hợp đặc biệt như một thiết bị Android TV luôn bật.

## Khắc phục sự cố
{: #troubleshooting }

### Lịch trình không chạy
{: #scheduler-is-not-executing }

Hãy phân biệt giữa việc Lịch trình hoàn toàn không chạy và việc các thao tác đã chạy nhưng không đem lại kết quả mong muốn. Bạn có thể xem lịch sử thực thi trong [mục Lịch sử](/vi/stats) của SD Maid. Nếu Lịch trình đã chạy nhưng thất bại, sẽ có một thông báo lỗi.

Nếu Lịch trình hoàn toàn không chạy, có thể hệ thống đã ngăn SD Maid khởi chạy để tiết kiệm pin. Sau khi kích hoạt lịch trình đầu tiên, lẽ ra bạn đã thấy một lời nhắc có tiêu đề "Lời khuyên" với thao tác "Sửa lỗi", yêu cầu bạn tắt tối ưu hóa pin. Nếu bạn lỡ bỏ qua lời nhắc đó, bạn sẽ cần tự vào phần cài đặt hệ thống dành cho SD Maid và kiểm tra xem SD Maid có đang bị hệ thống "tối ưu hóa" hay không.

### Dịch vụ trợ năng
{: #accessibility-service }
Các thao tác xóa dựa trên trợ năng, ví dụ Dọn dẹp ứng dụng xóa "các mục không thể truy cập", không hoạt động qua Lịch trình. Khi Lịch trình chạy, thiết bị thường đang bị khóa, và cũng như bạn không thể nhấn vào gì trên một màn hình đã tắt hoặc bị khóa, SD Maid cũng không thể.

Theo mặc định, Lịch trình không thực hiện các thao tác dựa trên trợ năng. Tuy nhiên, bạn có thể bật tùy chọn "Dịch vụ trợ năng" trong cài đặt Lịch trình nếu bạn biết mình đang làm gì.

Cách khắc phục tạm thời: Root thiết bị (ứng dụng người dùng + ứng dụng hệ thống) hoặc cài Shizuku (chỉ ứng dụng người dùng). Cả hai cách đều cho phép SD Maid thực hiện các thao tác tương tự mà không cần dùng tính năng dịch vụ trợ năng.
