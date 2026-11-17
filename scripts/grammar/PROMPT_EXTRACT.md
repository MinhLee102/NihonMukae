Tôi đính kèm một file PDF (bản scan) là bảng tổng hợp ngữ pháp JLPT N{LEVEL}. Mỗi dòng của bảng có: số nhóm và tên nhóm ý nghĩa (chữ Nhật, ví dụ 時間関係), số thứ tự, tên mẫu, cách nối, giải thích tiếng Việt và câu ví dụ.

Nhiệm vụ: chỉ xử lý các trang **{PAGES}** của file, và trích ra DANH SÁCH các mẫu ngữ pháp. Chỉ lấy những thông tin sau cho mỗi dòng:
- "pattern": tên mẫu đúng như trong cột tên mẫu. Nếu một dòng có nhiều dạng (ví dụ 〜にあって / 〜にあっても) thì ghi chúng trong MỘT chuỗi, ngăn bằng " / ".
- "reading": cách đọc của mẫu bằng hiragana.
- "category": tên nhóm ý nghĩa bằng chữ Nhật mà dòng đó thuộc về (ví dụ 時間関係, 逆接条件). Các dòng cùng nhóm dùng chung một tên.
- "hint": cách nối, chép từ cột cách nối (ví dụ "V辞書形 + が早いか"). Chỉ cần ngắn gọn, bỏ chú thích thừa.

TUYỆT ĐỐI KHÔNG chép giải thích tiếng Việt, không chép câu ví dụ và bản dịch trong file. Chỉ lấy bốn mục trên.

Quy tắc:
- Giữ nguyên thứ tự xuất hiện trong file, không bỏ sót dòng nào, không thêm dòng không có trong file.
- Nếu chữ mờ không đọc chắc được thì bỏ qua dòng đó và không đoán.
- Mỗi dòng của bảng là đúng một phần tử.

Định dạng trả về: CHỈ một mảng JSON hợp lệ, không markdown, không ```json, không lời dẫn, không giải thích. Mỗi phần tử đúng dạng:
{"pattern": "〜が早いか", "reading": "がはやいか", "category": "時間関係", "hint": "V辞書形 + が早いか"}
