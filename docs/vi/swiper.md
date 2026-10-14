---
title: "Vuốt"
lang: vi
translation_key: swiper
permalink: /vi/swiper
source_hash: 4c7d8b7a30d3a36e
---
# Vuốt (Swiper)
{: #swiper }

Vuốt (Swiper) giúp bạn dọn dẹp các thư mục cụ thể nhanh hơn so với một trình quản lý tệp. Bạn duyệt qua từng tệp một và vuốt từng tệp để giữ lại, xóa hoặc bỏ qua. Không có gì bị xóa cho đến khi bạn xem lại các lựa chọn của mình và xác nhận.

## Bắt đầu một phiên
{: #starting-a-session }

1. Mở Vuốt từ bảng điều khiển và nhấn `Chọn thư mục`. Bạn có thể chọn thư mục, từng tệp riêng lẻ, hoặc cả hai.
2. Tùy chọn đặt bộ lọc loại tệp và thứ tự sắp xếp trên thẻ phiên.
3. Nhấn `Quét`. Các thư mục được tìm kiếm bao gồm cả mọi thư mục con.
4. Nhấn `Bắt đầu vuốt`.

Bạn cũng có thể tạo một phiên từ [Phân tích bộ nhớ (StorageAnalyzer)](/vi/storageanalyzer): chọn các mục ở đó và chọn `Tạo phiên Swiper`.

Những nơi tốt để bắt đầu là thư viện ảnh từ camera, thư mục Downloads và ảnh chụp màn hình.

### Loại tệp
{: #file-types }

`Chỉ hiển thị các loại tệp này` giới hạn phiên ở hình ảnh, video, âm thanh, tài liệu hoặc tệp lưu trữ. Bạn cũng có thể nhập phần mở rộng của riêng mình, ví dụ `apk, apks`. Nếu không chọn gì, tất cả các tệp sẽ được hiển thị. Các tệp chỉ được khớp theo phần mở rộng.

### Thứ tự sắp xếp
{: #sort-order }

Theo mặc định, các tệp được hiển thị theo thứ tự cũ nhất trước, dựa trên ngày sửa đổi gần nhất. Bạn cũng có thể sắp xếp theo mới nhất trước, theo tên, hoặc lớn nhất trước.

Không thể thay đổi bộ lọc và thứ tự sắp xếp sau khi một lần quét đã tìm thấy tệp. Để thay đổi, hãy bắt đầu một phiên mới.

### Quét toàn bộ một bộ nhớ
{: #scanning-a-whole-storage }

Nếu bạn chọn cấp cao nhất của một bộ nhớ, ví dụ toàn bộ bộ nhớ trong, mọi tệp trên đó sẽ nằm trong phiên, bao gồm cả ảnh và media của ứng dụng. SD Maid sẽ yêu cầu bạn xác nhận trước khi quét. Khi sau đó bạn xóa tệp từ một phiên như vậy, trước tiên bạn phải đánh dấu `Tôi hiểu các tệp này sẽ bị xóa vĩnh viễn.`.

## Vuốt
{: #swiping }

| Cử chỉ | Thao tác mặc định |
|---|---|
| Vuốt sang phải | Giữ lại |
| Vuốt sang trái | Xoá |
| Vuốt lên | Bỏ qua |
| Vuốt xuống | Hoàn tác quyết định gần nhất |

Các nút ở phía dưới cũng làm điều tương tự. Khi bật [Đổi hướng vuốt](#swap-swipe-directions), trái và phải sẽ được đảo ngược.

* **Bỏ qua** để một tệp ở trạng thái chưa quyết định. Các tệp bị bỏ qua sẽ quay lại sau khi đã duyệt xong phần còn lại.
* **Hoàn tác** lùi lại qua các quyết định gần nhất của bạn, tối đa 50.
* **Nhấn giữ Bỏ qua** để loại trừ một tệp khỏi các lần quét Vuốt sau này. Thao tác này tạo một [loại trừ](/vi/exclusions) đường dẫn và xóa tệp khỏi phiên.
* Các nút trên thẻ mở tệp trong ứng dụng khác hoặc hiển thị bản xem trước toàn màn hình.
* Nhấn vào một hình thu nhỏ trong dải để chuyển đến tệp đó. Vuốt lại tệp đó sẽ thay thế quyết định trước đó của bạn.

Bạn có thể rời đi bất cứ lúc nào. Các quyết định của bạn được lưu cùng phiên và bạn có thể tiếp tục sau. Khi SD Maid khởi động lại, các tệp mà nó không còn tìm thấy sẽ bị loại khỏi phiên. Trước đó, phiên vẫn có thể liệt kê các tệp đã bị xóa hoặc di chuyển bởi thứ khác.

## Xem lại và xóa
{: #reviewing-and-deleting }

Nhấn `Đánh giá` để xem tất cả các quyết định. Khi không còn tệp nào chưa quyết định, màn hình xem lại sẽ tự mở.

* Thay đổi quyết định cho từng tệp, hoặc nhấn giữ để chọn nhiều tệp cùng lúc.
* Nhấn vào một tệp để quay lại tệp đó.
* `Xoá` cho biết có bao nhiêu tệp và bao nhiêu dung lượng bị ảnh hưởng, và ở những thư mục nào, trước khi bất cứ điều gì xảy ra.

Việc xóa là vĩnh viễn. Các tệp không được chuyển vào thư mục thùng rác và SD Maid không thể khôi phục chúng.

Sau khi xóa, các tệp được giữ lại và đã xóa sẽ rời khỏi phiên. Các tệp chưa quyết định vẫn ở lại để xử lý sau. Khi không còn gì, phiên sẽ bị xóa. Nếu một số tệp không thể xóa được, chúng vẫn ở lại trong phiên và bạn có thể thử lại.

Xóa một phiên chỉ hủy bỏ các quyết định của bạn. Nó không đụng đến bất kỳ tệp nào.

### Những điều cần biết
{: #things-to-know }

* **Giữ lại chỉ áp dụng cho phiên hiện tại.** Nếu bạn bắt đầu một phiên mới trên cùng thư mục, các tệp đã giữ lại sẽ xuất hiện lại. Để ẩn một tệp vĩnh viễn, hãy loại trừ nó (nhấn giữ Bỏ qua).
* **Một phiên không nhận thêm tệp mới.** Các tệp được thêm vào thư mục sau khi quét sẽ không xuất hiện. Hãy bắt đầu một phiên mới để đưa chúng vào.
* **Lưu trữ bao gồm cả tệp APK.**
* **Các tệp ẩn cũng được bao gồm.**

## Phiên bản miễn phí
{: #free-version }

Không có bản nâng cấp, Vuốt bị giới hạn ở 2 phiên với 50 tệp mỗi phiên. Khi đã đặt thứ tự sắp xếp, bạn sẽ nhận được 50 tệp đầu tiên theo thứ tự đó.

## Cài đặt
{: #settings }

### Đổi hướng vuốt
{: #swap-swipe-directions }

Vuốt sang trái để giữ lại, sang phải để xóa. Mặc định tắt.

### Hiển thị chi tiết tệp
{: #show-file-details }

Hiển thị tên tệp, dung lượng, ngày và thư mục trên màn hình vuốt. Mặc định bật.

### Phản hồi xúc giác
{: #haptic-feedback }

Rung khi bạn giữ lại hoặc xóa một tệp. Mặc định bật.
