---
title: "Bản dịch"
lang: vi
translation_key: translation
permalink: /vi/translation
source_hash: 2d81db0b448d96f7
---
# Bản dịch
{: #translations }

Các bản dịch được quản lý qua Crowdin:
<https://crowdin.com/project/sdmaid-se>

Bản dịch được tải về thường xuyên trước khi phát hành các bản cập nhật ứng dụng mới. Các chuỗi đã hoàn thành trên Crowdin sẽ tự động được đưa vào các bản phát hành sắp tới.

## Câu hỏi & vấn đề
{: #questions--issues }

Nếu bạn có câu hỏi hoặc phát hiện vấn đề, vui lòng bình luận trực tiếp trên chuỗi liên quan trong Crowdin.  
Bình luận trên chuỗi cung cấp ngữ cảnh và giúp giải quyết các vấn đề dịch thuật nhanh hơn.

Chỉ dùng GitHub issues cho các vấn đề dịch thuật chung, không dùng cho từng chuỗi riêng lẻ.

## Lỗi thường gặp
{: #common-pitfalls }

**Placeholder phải được giữ nguyên**

Không dịch hoặc thay đổi thứ tự các placeholder.

Ví dụ:
- `%s`, `%d`, `%1$s`
- `{0}`, `{name}`
- `$value`

Đúng:
`Delete %s files` → `Supprimer %s fichiers`

Sai:
`Supprimer %d fichiers`

**Ký tự thoát (escape)**

Giữ nguyên các chuỗi thoát đúng như ban đầu.

Ví dụ:
- `\n` = xuống dòng
- `\t` = tab
- `\"` = dấu ngoặc kép bên trong chuỗi
- `\\` = dấu gạch chéo ngược

Không xóa hoặc nhân đôi chúng.

**Xuống dòng**

Nếu chuỗi gốc có ngắt dòng, hãy giữ nguyên chúng.

Ví dụ:
`Line1\nLine2`

Không gộp thành một câu trừ khi hướng dẫn cho ngôn ngữ đó nói rõ như vậy.

**Định dạng & markup**

Không làm hỏng:
- Thẻ HTML (`<b>`, `<i>`, `<br>`)
- Ký hiệu Markdown
- Định dạng Android (`%1$s`, `&lt;`, `&amp;`)

## Ngôn ngữ
{: #languages }

Một số ngôn ngữ đòi hỏi cách dùng từ, định dạng hoặc giọng văn riêng. Hãy xem hướng dẫn riêng cho từng ngôn ngữ trong wiki này trước khi dịch để giữ thuật ngữ và văn phong nhất quán. Nếu chưa có hướng dẫn, người đóng góp có thể đề xuất một hướng dẫn.

### Tiếng Pháp
{: #french }

Điều phối: 

* Alain-Olivier ([Github](https://github.com/AO-LocLab), [Crowdin](https://crowdin.com/profile/yahoe-001))

Hướng dẫn:
* [Tài liệu PDF tham khảo](https://github.com/user-attachments/files/25190189/Directives.generales.de.traduction.en.Francais.pdf)
