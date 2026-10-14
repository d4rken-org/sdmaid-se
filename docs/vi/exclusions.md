---
title: "Loại trừ"
lang: vi
translation_key: exclusions
permalink: /vi/exclusions
source_hash: d5c3aea2efa43796
---
# Loại trừ
{: #exclusions }

Chức năng loại trừ của SD Maid 2/SE cho phép người dùng chỉ định các thư mục hoặc tệp cụ thể mà ứng dụng sẽ bỏ qua trong quá trình quét hệ thống và các tác vụ bảo trì. Điều này hữu ích nếu có dữ liệu bạn muốn giữ nguyên, không bị ảnh hưởng bởi các quy trình của ứng dụng. Khi đặt loại trừ, SD Maid 2/SE sẽ bỏ qua các thư mục hoặc tệp này trong các tác vụ như Tìm kiếm tàn dư (CorpseFinder), Dọn dẹp ứng dụng (AppCleaner) và các tính năng quản lý hệ thống khác.

Cách dễ nhất để thêm một loại trừ là thêm trực tiếp từ kết quả. Bạn có thể dùng nút "Loại trừ" trên từng mục, hoặc nhấn giữ/chọn nhiều mục rồi chọn tùy chọn "Loại trừ". Ngoài ra, loại trừ cũng có thể được thêm thủ công trong phần cài đặt của ứng dụng bằng cách chỉ định đường dẫn hoặc mẫu ký tự đại diện (wildcard) để kiểm soát tốt hơn.

Có thể xuất và nhập loại trừ từ menu. Việc nhập loại trừ chỉ được hỗ trợ trên các phiên bản SD Maid tương tự nhau. Nếu giữa phiên bản nhập và phiên bản xuất có quá nhiều bản cập nhật, có thể xảy ra lỗi. Khi đó, bạn có thể thử nhập trên một phiên bản cũ hơn, rồi xuất lại các loại trừ.

<img src="/assets/images/exclusions/overview-1.webp" width="150">
<img src="/assets/images/exclusions/overview-2.webp" width="150">
<img src="/assets/images/exclusions/overview-3.webp" width="150">
<img src="/assets/images/exclusions/overview-4.webp" width="150">

## Các loại
{: #types }

SD Maid hỗ trợ 3 loại loại trừ khác nhau:

### Loại trừ ứng dụng
{: #app-exclusions }

Loại trừ ứng dụng dựa trên tên gói của từng ứng dụng. Có thể tạo chúng qua công cụ Kiểm soát ứng dụng (AppControl). Chỉ cần chọn một ứng dụng trong danh sách và dùng thao tác `Loại trừ` tương ứng.

### Loại trừ đường dẫn
{: #path-exclusions }

Có thể tạo loại trừ đường dẫn bằng cách chọn (nhấn giữ) các đường dẫn trong công cụ Phân tích bộ nhớ (StorageAnalyzer).

### Loại trừ phân đoạn
{: #segment-exclusions }
Loại trừ phân đoạn là một kiểu loại trừ đường dẫn không đòi hỏi khớp chính xác. Một phân đoạn là một phần của đường dẫn, chẳng hạn một thư mục. Đường dẫn `/sdcard/DCIM/Camera` gồm 3 phân đoạn. Bạn có thể loại trừ toàn bộ ảnh chụp từ camera ở cả bộ nhớ trong (`/storage/emulated/0/DCIM/Camera`) lẫn bộ nhớ ngoài (`/sdcard/DCIM/Camera`) bằng cách tạo một loại trừ phân đoạn cho `DCIM/Camera`.

Nếu bạn đánh dấu tùy chọn `Cho phép khớp một phần`, bạn có thể tạo một loại trừ cho `DCIM/Cam` để khớp cả thư mục `Cam` lẫn thư mục `Camera`.

## Công cụ
{: #tools }

Mỗi công cụ của SD Maid có một cách nhìn khác nhau về dữ liệu. Không phải loại trừ nào cũng ảnh hưởng như nhau đến mọi công cụ.

### Tìm kiếm tàn dư
{: #corpsefinder }

Công cụ Tìm kiếm tàn dư biết "ứng dụng" là gì và những tệp nào thuộc về một ứng dụng. Nó tuân theo mọi loại loại trừ. Tạo một loại trừ ứng dụng có nghĩa là bất kỳ tệp nào mà Tìm kiếm tàn dư tìm thấy và cho là thuộc về một ứng dụng bị loại trừ đều sẽ được loại trừ.

Các loại loại trừ được hỗ trợ: `APP`, `PATH`, `SEGMENTS`

### Dọn dẹp hệ thống
{: #systemcleaner }

Công cụ Dọn dẹp hệ thống (SystemCleaner) chỉ làm việc với dữ liệu đường dẫn. Nó không biết "ứng dụng" là gì hay những tệp nào thuộc về một ứng dụng.

Các loại loại trừ được hỗ trợ: `PATH`, `SEGMENTS`

### Dọn dẹp ứng dụng
{: #appcleaner }

Công cụ Dọn dẹp ứng dụng tuân theo mọi loại loại trừ. Bạn có thể dùng loại trừ để tăng tốc độ quét nếu có những ứng dụng có nhiều tệp cần quét.

Các loại loại trừ được hỗ trợ: `APP`, `PATH`, `SEGMENTS`

### Quản lý tệp trùng lặp
{: #deduplicator }

Công cụ Quản lý tệp trùng lặp (Deduplicator) không biết "ứng dụng" là gì và chỉ làm việc với dữ liệu tệp.

Các loại loại trừ được hỗ trợ: `PATH`, `SEGMENTS`

### Nén media
{: #media-squeeze }

Nén media (Media Squeeze) chỉ làm việc với dữ liệu tệp. Các tệp bị loại trừ sẽ không được nén.

Các loại loại trừ được hỗ trợ: `PATH`, `SEGMENTS`

### Vuốt
{: #swiper }

Vuốt (Swiper) chỉ làm việc với dữ liệu tệp. Các tệp bị loại trừ không được đưa vào các phiên mới. Nhấn giữ `Bỏ qua` trong khi vuốt sẽ loại trừ tệp hiện tại.

Các loại loại trừ được hỗ trợ: `PATH`, `SEGMENTS`

### Phân tích bộ nhớ
{: #storageanalyzer }

Phân tích bộ nhớ không hỗ trợ loại trừ, vì toàn bộ nội dung cần được quét để tính đúng tổng dung lượng.
