---
title: "Tìm kiếm tàn dư"
lang: vi
translation_key: corpsefinder
permalink: /vi/corpsefinder
source_hash: 9e16286f0fda1dc8
---
# Tìm kiếm tàn dư (CorpseFinder)
{: #corpse-finder }

Công cụ `Tìm kiếm tàn dư` (CorpseFinder) tìm các tệp thuộc về những ứng dụng không còn được cài đặt.


## Mức độ rủi ro
{: #risklevel }

Theo mặc định, tất cả tùy chọn mức độ rủi ro đều tắt. Chỉ bật chúng nếu bạn biết mình đang làm gì, và trước khi xóa bất cứ thứ gì, hãy kiểm tra kết quả trước.
Bật một tùy chọn mức độ rủi ro sẽ làm tăng khả năng xóa phải những tệp mà bạn không muốn xóa.

Mức độ rủi ro chủ yếu áp dụng cho các vị trí theo danh sách trắng, chẳng hạn như thư mục gốc của bộ nhớ công khai (`/storage/emulated/0`). Những vị trí như vậy không có quy tắc đặt tên chung, và SD Maid chỉ có thể xác định chủ sở hữu thông qua một cơ sở dữ liệu được duy trì thủ công.


### Bao gồm tàn dư mong muốn
{: #include-desirable-remnants }

Tàn dư mong muốn là các tệp có chủ sở hữu đã biết nhưng chưa được cài đặt, và được SD Maid đánh dấu nội bộ là `KEEPER`. Thường đây là các tệp do người dùng tạo, ví dụ ảnh bạn đã chụp.

### Bao gồm tên phổ biến
{: #include-common-names }

Một số ứng dụng dùng những cái tên rất phổ biến. Có lẽ có hàng trăm ứng dụng ghi chú tạo một thư mục `/sdcard/Notes` để lưu dữ liệu. SD Maid có thể biết một trong những ứng dụng đó và nó không được cài trên điện thoại của bạn, nhưng SD Maid không thể chắc chắn rằng không còn ứng dụng nào khác trên điện thoại đang dùng thư mục đó mà SD Maid không biết là ứng dụng này cũng dùng thư mục đó. 

## Bộ lọc
{: #filter }

Mỗi bộ lọc chịu trách nhiệm cho một vùng dữ liệu cụ thể. Khi bật một bộ lọc, Tìm kiếm tàn dư sẽ tìm kiếm thêm vùng đó.

### Thẻ SD
{: #sdcard }

* Thường là nơi người dùng đặt tệp
* Bộ nhớ công khai (cả bộ nhớ trong lẫn bộ nhớ tháo rời được), không bao gồm các thư mục con cụ thể trong `Android/data`.
* Tệp ở đây không có quy tắc đặt tên.

Ví dụ:

```bash
/storage/emulated/0/SDMaid
/storage/DEAD-BEEF/SDMaid
/sdcard/SDMaid
```

### Đa phương tiện công khai
{: #public-media }

* Lưu trữ media công khai của ứng dụng, ví dụ media nhận được qua WhatsApp. Các thư mục được đặt tên theo tên gói của ứng dụng.
* Thư mục con của bộ nhớ công khai, có ý nghĩa đặc biệt với hệ điều hành. 

Ví dụ:

```bash
/storage/emulated/0/Android/media/eu.darken.sdmse
/storage/DEAD-BEEF/Android/media/eu.darken.sdmse
/sdcard/Android/media/eu.darken.sdmse
```

### Dữ liệu ứng dụng công khai
{: #public-app-data }

* Các tệp bổ sung của ứng dụng.
* Các thư mục được đặt tên theo tên gói của ứng dụng.
* Thư mục con của bộ nhớ công khai, có ý nghĩa đặc biệt với hệ điều hành.

Ví dụ:

```bash
/storage/emulated/0/Android/data/eu.darken.sdmse
/storage/DEAD-BEEF/Android/data/eu.darken.sdmse
/sdcard/Android/data/eu.darken.sdmse
```

### Tài nguyên OBB
{: #obb-resources }

* Thường là các tệp tài nguyên bổ sung cho trò chơi.
* Các thư mục và tệp được đặt tên theo tên gói của ứng dụng.
* Thư mục con của bộ nhớ công khai, có ý nghĩa đặc biệt với hệ điều hành.

Ví dụ:

```bash
/storage/emulated/0/Android/obb/eu.darken.sdmse
/storage/DEAD-BEEF/Android/obb/eu.darken.sdmse
/sdcard/Android/obb/eu.darken.sdmse
```

### Dữ liệu ứng dụng riêng tư
{: #private-app-data }

* Các thư mục và tệp được đặt tên theo tên gói của ứng dụng.
* Thư mục con của bộ nhớ công khai, có ý nghĩa đặc biệt với hệ điều hành.

Ví dụ:

```bash
/data/data/eu.darken.sdmse
```

### Dalvik cache
{: #dalvik-cache }

* Lưu trữ các phiên bản đã tối ưu hóa của mã ứng dụng để thực thi nhanh hơn.
* Nằm trong phân vùng hệ thống, thường dưới các thư mục theo kiến trúc cụ thể.
* Các tệp được đặt tên theo tên gói của ứng dụng.

Ví dụ:

```bash
/data/dalvik-cache/arm64/data@app@eu.darken.sdmse@base.apk@classes.dex
/data/dalvik-cache/arm/data@app@eu.darken.sdmse@base.apk@classes.dex
```

### Thư viện ứng dụng
{: #app-libraries }

* Chứa các thư viện native được ứng dụng sử dụng, chẳng hạn tệp `.so`.
* Thường nằm trong thư mục dữ liệu của ứng dụng, dưới thư mục `lib`.
* Các tệp được đặt tên theo kiến trúc và yêu cầu của thư viện.

Ví dụ:

```bash
/data/app/eu.darken.sdmse-1/lib/arm64/libexample.so
/data/app/eu.darken.sdmse-2/lib/arm/libexample.so
```

### Nguồn ứng dụng
{: #app-sources }

* Chỉ các tệp APK đóng vai trò là mã nguồn của ứng dụng.
* Được lưu trong các thư mục cụ thể dành cho ứng dụng hệ thống, ứng dụng người dùng hoặc ứng dụng của nhà cung cấp (vendor).
* Các tệp được đặt tên theo tên gói và phiên bản của ứng dụng.

Ví dụ:

```bash
/data/app/eu.darken.sdmse-1/base.apk
/system/app/ExampleApp/ExampleApp.apk
```

### Nguồn ứng dụng riêng tư
{: #private-app-sources }

* Tương tự nguồn ứng dụng nhưng được lưu trong các thư mục được bảo vệ để tăng cường bảo mật.
* Chỉ hệ thống hoặc chính ứng dụng tương ứng mới truy cập được.
* Các tệp được đặt tên theo tên gói của ứng dụng.

Ví dụ:

```bash
/data/app-private/eu.darken.sdmse/base.apk
```
