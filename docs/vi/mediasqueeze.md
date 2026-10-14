---
title: "Nén media"
lang: vi
translation_key: mediasqueeze
permalink: /vi/mediasqueeze
source_hash: 1d878fd5a77634c5
---
# Nén media (Media Squeeze)
{: #media-squeeze }

Nén media (Media Squeeze) nén ảnh và video để chúng chiếm ít dung lượng hơn. Bạn đánh đổi một phần chất lượng để lấy lại dung lượng.

**Việc nén làm giảm chất lượng vĩnh viễn và không thể hoàn tác.** Tệp gốc bị thay thế và không có bản sao nào được giữ lại. Hãy kiểm tra cài đặt và bản xem trước trước khi bắt đầu.

Việc quét là miễn phí. Việc nén cần bản nâng cấp.

## Cách hoạt động
{: #how-it-works }

1. Chọn các thư mục cần tìm kiếm. Không có vị trí mặc định, ví dụ hãy chọn thư mục camera của bạn.
2. Đặt chất lượng nén và độ tuổi tối thiểu.
3. Quét. Mỗi kết quả hiển thị dung lượng của nó và mức tiết kiệm ước tính.
4. Nhấn vào một mục, chọn nhiều mục bằng cách nhấn giữ, hoặc dùng `Nén tất cả`. Trước tiên, một hộp xác nhận sẽ hiển thị tổng dung lượng và mức tiết kiệm ước tính.

Nếu lựa chọn chỉ gồm ảnh hoặc chỉ gồm video, `Xem so sánh` trong hộp xác nhận sẽ hiển thị bản gốc và bản đã nén cạnh nhau. Với video, phần so sánh chỉ là một khung hình duy nhất và chỉ mang tính gần đúng.

### Điều gì xảy ra với một tệp
{: #what-happens-to-a-file }

* Tệp được mã hóa lại theo chính định dạng của nó: JPEG vẫn là JPEG, WebP vẫn là WebP, MP4 vẫn là MP4.
* Độ phân giải ảnh được giữ nguyên, ngoại trừ [ảnh rất lớn](#compress-very-large-photos).
* Với video, chỉ có luồng hình được mã hóa lại ở bitrate thấp hơn. Luồng âm thanh được sao chép nguyên vẹn.
* Ngày, vị trí, thông tin camera và các siêu dữ liệu EXIF khác của ảnh được giữ lại. Nếu SD Maid không thể đọc hoặc giữ lại siêu dữ liệu EXIF của một ảnh, nó sẽ để nguyên ảnh đó.
* Siêu dữ liệu XMP không được giữ lại, ví dụ thông tin ảnh cầu (photo sphere) và ảnh toàn cảnh hoặc điểm đánh giá từ các trình chỉnh sửa ảnh.
* Ngày sửa đổi của tệp được giữ lại, nên thư viện ảnh vẫn sắp xếp tệp ở đúng vị trí cũ.
* Nếu bản nén không nhỏ hơn, bản gốc sẽ được giữ nguyên.

Mức tiết kiệm ước tính chỉ là phỏng đoán sơ bộ dựa trên loại tệp và chất lượng, không phải là phép đo. Mức tiết kiệm thực tế có thể khác.

### Vị trí
{: #locations }

Nén media cần quyền truy cập trực tiếp vào các tệp và quyền ghi vào thư mục chứa chúng. Công cụ không dùng root hay ADB. Một số vị trí được chọn qua trình chọn thư mục của Android, ví dụ ổ USB, không thể sử dụng và sẽ bị xóa khỏi lựa chọn.

## Các tệp đã nén trước đó
{: #previously-compressed-files }

SD Maid ghi nhớ những tệp nó đã nén, và những tệp không nhỏ đi, rồi bỏ qua chúng trong các lần quét sau. Nó nhận diện một tệp dựa trên nội dung, nên cách này vẫn hoạt động sau khi tệp được đổi tên hoặc di chuyển. Nén lại một tệp sẽ làm giảm chất lượng thêm mà tiết kiệm được rất ít.

Lịch sử được lưu trên thiết bị của bạn và là một phần của bản sao lưu cài đặt SD Maid. Xóa lịch sử sẽ khiến các tệp đã nén trước đó đủ điều kiện được nén lại, trừ khi chúng mang dấu EXIF và [Ghi dấu EXIF](#write-exif-marker) đang bật. Xóa lịch sử không thay đổi bất kỳ tệp nào.

## Ảnh bị bỏ qua theo mặc định
{: #photos-that-are-skipped-by-default }

Một số ảnh chứa dữ liệu mà việc nén không thể giữ lại. SD Maid bỏ qua chúng nếu phát hiện dữ liệu đó, trừ khi bạn bật cài đặt tương ứng. Việc phát hiện không hoàn chỉnh: những ảnh ở định dạng mà nó không nhận ra sẽ bị nén và mất dữ liệu đó.

* **Ảnh HDR và ảnh có dữ liệu độ sâu**: Ảnh HDR trở thành ảnh thường, và ảnh chân dung mất dữ liệu dùng cho hiệu ứng làm mờ hậu cảnh.
* **Motion Photo**: ảnh tĩnh được giữ lại, đoạn video bị xóa.
* **Ảnh rất lớn** (chiều rộng hoặc chiều cao từ 8192 pixel trở lên): chiều rộng và chiều cao của chúng bị giảm một nửa, và lại giảm một nửa nếu cần, cho đến khi cả hai đều dưới 8192 pixel. Một ảnh toàn cảnh rộng 20.000 pixel sẽ còn rộng 5.000 pixel.

Các tệp HEIC chứa nhiều hình ảnh luôn bị bỏ qua.

Phần tóm tắt kết quả quét đếm số ảnh bị bỏ qua do có dữ liệu HDR hoặc độ sâu, do là Motion Photo và do kích thước rất lớn, cùng các tệp không truy cập được. Các tệp bị bỏ qua vì đã được nén trước đó không được đếm, và các tệp HEIC nhiều hình ảnh cũng không được đếm, trừ khi chúng cũng có dữ liệu HDR hoặc độ sâu.

## Cài đặt
{: #settings }

Chất lượng nén, vị trí tìm kiếm và độ tuổi tối thiểu được đặt trên màn hình Nén media trước khi quét. Mọi thứ khác nằm trong `Cài đặt > Nén media`.

### Chất lượng nén
{: #compression-quality }

Mặc định 80. Chất lượng thấp hơn nghĩa là tệp nhỏ hơn nhưng chất lượng hình ảnh giảm. Dưới 40, hình ảnh có thể trở nên không dùng được. Từ 95 trở lên thì tiết kiệm được rất ít.

### Độ tuổi tối thiểu
{: #minimum-age }

Chỉ bao gồm các tệp cũ hơn mức này. Mặc định 90 ngày, dựa trên ngày sửa đổi của tệp.

### Kích thước tệp tối thiểu
{: #minimum-file-size }

Bỏ qua các tệp nhỏ hơn mức này. Mặc định 512 KB.

### JPEG
{: #jpeg }

Bao gồm ảnh JPEG (`.jpg`, `.jpeg`). Mặc định bật.

### WebP
{: #webp }

Bao gồm ảnh WebP (`.webp`). Mặc định bật. Tệp WebP động không được phát hiện và sẽ bị mất hiệu ứng động.

### HEIC/HEIF
{: #heicheif }

Bao gồm ảnh HEIC/HEIF (`.heic`, `.heif`). Mặc định tắt. Cần Android 9 trở lên. Với HEIC, không thể giữ lại HDR gain map, dữ liệu độ sâu và các phần Live/Motion Photo.

### Video MP4
{: #mp4-video }

Bao gồm video MP4 (`.mp4`). Mặc định tắt. Nén một video cần dung lượng trống khoảng 110% kích thước của nó trên cùng bộ nhớ.

### Nén các ảnh HDR & ảnh chụp độ sâu
{: #compress-hdr--depth-photos }

Xem [Ảnh bị bỏ qua theo mặc định](#photos-that-are-skipped-by-default). Mặc định tắt.

### Nén Motion Photo
{: #compress-motion-photos }

Giữ lại ảnh tĩnh và xóa đoạn video. Mặc định tắt.

### Nén ảnh rất lớn
{: #compress-very-large-photos }

Bao gồm các ảnh có chiều rộng hoặc chiều cao từ 8192 pixel trở lên, và giảm độ phân giải của chúng, xem [Ảnh bị bỏ qua theo mặc định](#photos-that-are-skipped-by-default). Mặc định tắt.

### Bỏ qua phần đã nén trước đó
{: #skip-previously-compressed }

Bỏ qua các tệp mà SD Maid đã nén. Mặc định bật.

### Ghi dấu EXIF
{: #write-exif-marker }

Thêm một dấu vào trường chú thích người dùng (user comment) trong EXIF của ảnh, để SD Maid nhận ra ảnh đã được nén ngay cả sau khi lịch sử nén đã bị xóa. SD Maid chỉ kiểm tra dấu này khi cài đặt này được bật. Mặc định tắt. Chỉ áp dụng cho JPEG và WebP.

### Lịch sử nén
{: #compression-history }

Hiển thị số lượng tệp có trong lịch sử. Nhấn vào để xóa lịch sử.

## Loại trừ
{: #exclusions }

Nén media bỏ qua các đường dẫn bị [loại trừ](/vi/exclusions) chung. Bạn cũng có thể loại trừ kết quả từ danh sách kết quả, việc này chỉ ảnh hưởng đến Nén media.

Nén media không thể được chạy bởi [Lịch trình](/vi/scheduler).
