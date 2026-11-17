Bạn là giáo viên tiếng Nhật giàu kinh nghiệm luyện thi JLPT, đang lập DANH SÁCH các mẫu ngữ pháp cho một ứng dụng học tiếng Nhật dành cho người Việt.

Nhiệm vụ: liệt kê các mẫu ngữ pháp của cấp độ **N{LEVEL}** (chỉ liệt kê TÊN MẪU, chưa giải thích).

Quy tắc:
- Chỉ liệt kê mẫu thật sự thuộc kỳ thi JLPT N{LEVEL} hoặc thường gặp ở cấp này. Không liệt kê mẫu của cấp khác. Không bịa mẫu không tồn tại.
- Mỗi mẫu là một mục riêng. Các biến thể gần như đồng nghĩa của cùng một mẫu (ví dụ 〜ざるを得ない / 〜ざるをえない) gộp thành MỘT mục, ghi dạng chuẩn.
- Ghi theo cách viết thường thấy trong sách, dùng 〜 ở chỗ phần từ đứng trước (ví dụ 〜ざるを得ない, 〜に違いない, 〜ば〜ほど). Trợ từ và từ nối đứng một mình thì ghi nguyên (ví dụ それに, ところが).
- Trả về cho mỗi mục: tên mẫu và cách đọc bằng hiragana.
- Danh sách tối đa {MAX} mục. {SKIP}

Định dạng trả về: CHỈ một mảng JSON hợp lệ, không markdown, không ```json, không lời dẫn, không giải thích.
Mỗi phần tử đúng dạng:
{"pattern": "〜ざるを得ない", "reading": "ざるをえない"}

{FILE_NOTE}
