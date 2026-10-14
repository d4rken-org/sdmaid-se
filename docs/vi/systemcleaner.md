---
title: "Dọn dẹp hệ thống"
lang: vi
translation_key: systemcleaner
permalink: /vi/systemcleaner
source_hash: 1ced6df9b00f1e05
---
# Dọn dẹp hệ thống (SystemCleaner)
{: #system-cleaner }

Công cụ Dọn dẹp hệ thống (SystemCleaner) về cơ bản là một công cụ tìm kiếm đa năng. Nó sẽ duyệt qua toàn bộ bộ nhớ có thể truy cập và đối chiếu các tệp và thư mục tìm thấy với một tập hợp bộ lọc.

Khác với các công cụ khác như Tìm kiếm tàn dư (CorpseFinder) hay Dọn dẹp ứng dụng (AppCleaner), nó không biết về các ứng dụng đã cài đặt. Nó chỉ làm việc với đường dẫn và thuộc tính của tệp. Đó là lý do chỉ có loại trừ đường dẫn và loại trừ phân đoạn mới áp dụng được cho Dọn dẹp hệ thống.

Mọi tệp tìm thấy đều được đưa qua các bộ lọc đang bật, và sẽ được gán cho bộ lọc đầu tiên khớp với nó.

## Cài đặt
{: #settings }

### Bộ lọc chung
{: #general-filters }

Bộ lọc chung tìm kiếm **trên tất cả các vùng bộ nhớ có thể truy cập** để tìm những loại tệp nhất định có thể xuất hiện ở bất kỳ đâu trên thiết bị.

| Bộ lọc | Mô tả |
|--------|-------------|
| **Thư mục trống** | Các thư mục trống ở khắp thiết bị. Các thư mục lồng nhau có thể cần quét nhiều lượt. |
| **Ảnh chụp màn hình** | Ảnh chụp màn hình cũ hơn một độ tuổi có thể cấu hình, từ nhiều ứng dụng và vị trí. |
| **Các tập tin đã xóa** | Các tệp trong thùng rác của nhiều ứng dụng và vị trí, đã được đánh dấu để xóa nhưng chưa bị xóa vĩnh viễn. |
| **APK thừa** | Các tệp cài đặt (APK) có phiên bản cũ hơn hoặc bằng phiên bản ứng dụng đã được cài đặt. |
| **Các thư mục LOST.DIR** | Các tệp tạm được tạo ra khi chuyển dữ liệu giữa các thiết bị Android. |
| **Tệp Linux** | Các tệp và thư mục được tạo ra khi bộ nhớ được kết nối với một máy tính Linux. |
| **Mac files** | Các tệp và thư mục được tạo ra khi duyệt bộ nhớ bằng ứng dụng Finder của Apple (ví dụ: `.DS_Store`). |
| **Tệp Windows** | Dữ liệu mà trình quản lý tệp Windows có thể tạo ra trên bộ nhớ được kết nối (ví dụ: `Thumbs.db`). |
| **Hình thu nhỏ** | Bản xem trước được lưu đệm của ảnh và video (ví dụ: thư mục `.thumbnails`). |
| **Các tập tin hệ thống tạm thời** | Các tệp tạm liên quan đến hệ thống, thường là bộ nhớ đệm tồn tại ngắn cho các thao tác đang diễn ra. |
| **Tệp phân tích** | Dữ liệu liên quan đến phân tích (analytics) và theo dõi lỗi. |
| **Tệp quảng cáo** | Các tệp được tạo ra cho hoặc bởi quảng cáo. |
| **Tệp nhật ký hệ thống** | Các tệp nhật ký của hệ thống và dịch vụ hệ thống (ví dụ: `*.log`) ở nhiều vị trí khác nhau. |

### Bộ lọc cụ thể
{: #specific-filters }

Bộ lọc cụ thể nhắm đến **các đường dẫn cố định, đã biết** trên thiết bị, nơi lưu trữ một số loại dữ liệu nhất định.

| Bộ lọc | Mô tả |
|--------|-------------|
| **Dư lượng sự cố hệ thống** | Nếu các tiến trình native bị crash (ví dụ: trò chơi), các tệp "tombstone" sẽ được tạo ra ở đây. |
| **Thống kê sử dụng** | Các tệp liên quan đến thống kê sử dụng ứng dụng. |
| **Lỗi ANR** | Dấu vết từ các sự kiện "Application Not Responding" (ứng dụng không phản hồi). |
| **Tệp tạm thời cục bộ** | Các tệp tạm liên quan đến hệ thống (ví dụ: từ Android Studio). |
| **Tệp nhật ký hệ thống (datalogger)** | Các tệp nhật ký riêng của dịch vụ hệ thống. |
| **Bộ đệm tải xuống** | Thư mục bộ nhớ đệm hệ thống dùng cho nhiều việc khác nhau (ví dụ: bản cập nhật OTA). |
| **Nhật ký drop box** | Một vùng để hệ thống đặt các tệp nhật ký vào. |
| **Bộ nhớ đệm cài đặt** | Dữ liệu được lưu đệm liên quan đến các ứng dụng đã cài đặt, được trình quản lý gói của hệ thống sử dụng. |
| **Tác vụ gần đây** | Dữ liệu liên quan đến lịch sử tác vụ trong trình chuyển đổi tác vụ của hệ thống. |

### Bộ lọc tùy chỉnh
{: #custom-filters }

Ngoài các bộ lọc chung do ứng dụng cung cấp, bạn có thể tạo bộ lọc tùy chỉnh với tiêu chí của riêng mình. Có thể tạo bộ lọc tùy chỉnh từ **Cài đặt > Dọn dẹp hệ thống > Quản lý bộ lọc tùy chỉnh**.

> **Cảnh báo**: Bộ lọc tùy chỉnh có thể gây rủi ro. Luôn kiểm tra kết quả quét trước khi xóa!

#### Các tiêu chí có sẵn
{: #available-criteria }

| Tiêu chí | Mô tả |
|-----------|-------------|
| **Nhãn** | Tên cho bộ lọc của bạn |
| **Vùng dữ liệu** | Các vị trí bộ nhớ cần quét (để trống để quét tất cả các vùng có thể truy cập) |
| **Loại tệp** | Nhắm đến tệp, thư mục hoặc liên kết tượng trưng (để trống để chọn tất cả) |
| **Tiêu chí đường dẫn** | Quy tắc để khớp đường dẫn tệp |
| **Loại trừ đường dẫn** | Các đường dẫn cần loại khỏi việc khớp |
| **Tiêu chí tên** | Quy tắc để khớp tên tệp |
| **Kích thước tối thiểu** | Chỉ khớp các tệp lớn hơn mức này |
| **Kích thước tối đa** | Chỉ khớp các tệp nhỏ hơn mức này |
| **Độ tuổi tối thiểu** | Chỉ khớp các tệp cũ hơn mức này |
| **Độ tuổi tối đa** | Chỉ khớp các tệp mới hơn mức này |
| **Regex đường dẫn** | Nâng cao: Biểu thức chính quy để khớp đường dẫn |

#### Chế độ tiêu chí đường dẫn
{: #path-criteria-modes }

| Chế độ | Mô tả | Ví dụ |
|------|-------------|---------|
| **Bắt đầu bằng** | Đường dẫn bắt đầu bằng từ khóa | `Downloads/temp` khớp với `/sdcard/Downloads/temp/file.txt` |
| **Chứa** | Đường dẫn có chứa từ khóa | `cache` khớp với `/sdcard/app/cache/data.bin` |
| **Kết thúc bằng** | Đường dẫn kết thúc bằng từ khóa | `backup/old` khớp với `/sdcard/backup/old` |
| **Bằng** | Đường dẫn khớp chính xác với từ khóa | `Download/temp` chỉ khớp với đúng đường dẫn đó |

#### Chế độ tiêu chí tên
{: #name-criteria-modes }

| Chế độ | Mô tả | Ví dụ |
|------|-------------|---------|
| **Bắt đầu bằng** | Tên tệp bắt đầu bằng từ khóa | `temp_` khớp với `temp_file.txt` |
| **Chứa** | Tên tệp có chứa từ khóa | `.bak` khớp với `document.bak.old` |
| **Kết thúc bằng** | Tên tệp kết thúc bằng từ khóa | `.tmp` khớp với `cache.tmp` |
| **Bằng** | Tên tệp khớp chính xác | `thumbs.db` chỉ khớp với `thumbs.db` |

Theo mặc định, mọi phép khớp đều **không phân biệt chữ hoa/chữ thường**.

#### Vùng dữ liệu
{: #data-areas }

Các vùng thường dùng mà bạn có thể nhắm đến:

| Vùng | Mô tả |
|------|-------------|
| **SDCARD** | Bộ nhớ chính (bộ nhớ trong hoặc thẻ SD) |
| **PUBLIC_MEDIA** | Các thư mục media (DCIM, Pictures, Music, v.v.) |
| **PUBLIC_DATA** | Dữ liệu ứng dụng dùng chung |
| **PRIVATE_DATA** | Bộ nhớ riêng tư của ứng dụng (cần root) |
| **DOWNLOAD_CACHE** | Bộ đệm tải xuống của hệ thống (cần root) |
| **PORTABLE** | Bộ nhớ tháo rời được (USB/microSD) |

#### Mẹo
{: #tips }

- **Hiệu năng**: Chỉ định nghĩa những tiêu chí cần thiết. Quá nhiều mẫu regex sẽ làm chậm quá trình quét.
- **Nhiều tiêu chí**: Tất cả các tiêu chí đã chỉ định đều phải khớp (logic AND).
- **Nhấn giữ**: Nhấn giữ các mục để có thêm tùy chọn.
- **Thử nghiệm**: Dùng bản xem trước kết quả quét trước khi bật tính năng xóa.
- **Nhập/Xuất**: Bộ lọc có thể được xuất ra và chia sẻ với người dùng khác.

#### Tùy chọn đã ngừng hỗ trợ
{: #deprecated-options }

Tùy chọn `rootOnly` từ SD Maid 1 **không còn khả dụng**. Thay vào đó, hãy dùng **Vùng dữ liệu** để kiểm soát những vị trí bộ nhớ nào được quét.
