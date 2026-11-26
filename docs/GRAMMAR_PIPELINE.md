# Soạn ngữ pháp

Làm toàn bộ trên **Admin, Ngữ pháp** (chỉ tài khoản ADMIN). Nội dung (giải thích, cách chia, ví dụ) do AI viết mới, không sao chép từ sách hay file tham khảo. Không cần chạy script hay SSH vào VPS.

Phía trên trang là năm ô N5 đến N1, mỗi ô cho biết bao nhiêu mẫu đã có nội dung, bao nhiêu đang chờ viết và bao nhiêu bị AI gác lại. Bấm vào một ô để làm việc với cấp đó.

## Bước 1: danh sách tên mẫu

Có hai cách, dùng riêng hoặc kết hợp:

- **AI tự liệt kê:** copy prompt, dán vào AI (Gemini, ChatGPT...), dán câu trả lời vào ô bên dưới rồi bấm **Lưu danh sách**. Nếu AI liệt kê chưa đủ, copy prompt lần nữa: prompt tự kèm các mẫu đã có để AI liệt kê tiếp phần còn thiếu.
- **Trích từ file PDF:** khi có file tổng hợp (ví dụ bảng ngữ pháp N1 dạng scan), chọn cách này, nhập khoảng trang (ví dụ `1-11`), đính kèm file khi dán prompt. AI chỉ lấy tên mẫu, nhóm ý nghĩa (`category`) và cách nối (`hint`), không lấy giải thích hay ví dụ của file. File dài thì chia trang, mỗi phần lưu một lần.

Lưu bao nhiêu lần cũng được: mẫu trùng tên với mẫu đã có (bỏ qua khác biệt `~`, `〜`, khoảng trắng) tự bị bỏ qua, mẫu mới thêm vào cuối danh sách.

## Bước 2: nội dung từng mẫu

Trang tự lấy 15 mẫu kế tiếp chưa có nội dung và dựng sẵn prompt. Copy prompt, dán vào AI, dán câu trả lời vào ô rồi bấm **Kiểm tra và lưu**. Hệ thống kiểm tra từng mục:

- **Lỗi** (không lưu, mẫu đó vào lại batch sau): sai hoặc thiếu `id`, thiếu nghĩa hay giải thích, nghĩa không phải tiếng Việt, thiếu cách chia, ít hơn 2 ví dụ hợp lệ.
- **Cảnh báo** (vẫn lưu, nên xem qua): ví dụ không thấy chứa mẫu, ít hơn 3 ví dụ, có furigana trong ngoặc.
- Mục AI trả `"invalid": true` (không chắc mẫu có thật) bị gác lại, không vào batch nữa. Ở danh sách mẫu bên dưới có nút cho vào lại hàng chờ.

Lưu xong trang tự chuyển sang batch kế tiếp. Lặp lại đến khi hết mẫu chờ viết.

## Danh sách mẫu

Cuối trang có danh sách mọi mẫu của cấp đang chọn, kèm trạng thái. Có thể xoá mẫu nhầm (xoá cả nội dung và ví dụ của mẫu đó).

## Ghi chú kỹ thuật

- Bảng `grammar_points` và `grammar_examples` (V27, V28, V31). Mẫu chưa có nội dung có `meaning` là NULL; trang học chỉ nên hiện mẫu đã có nội dung.
- API: `/admin/grammar/summary`, `/points`, `/list`, `/batch`, `/content`, `/points/{id}` (xoá), `/points/{id}/restore`.
- Prompt nằm ở `frontend/src/lib/adminGrammarPrompts.ts`, kiểm tra nội dung ở `backend/.../grammar/GrammarValidator.java`.
- Tab Ngữ pháp phía người học vẫn khoá cho đến khi có trang đọc hai bảng này.
