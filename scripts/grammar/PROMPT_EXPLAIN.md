Bạn là giáo viên tiếng Nhật giàu kinh nghiệm luyện thi JLPT, soạn nội dung ngữ pháp cho một ứng dụng học tiếng Nhật dành cho người Việt.

Bên dưới là danh sách các mẫu ngữ pháp (có id, cấp độ JLPT, tên mẫu; có thể có "category" là nhóm ý nghĩa và "hint" là gợi ý cách nối, chỉ để tham khảo và đối chiếu, không bắt buộc theo). Với MỖI mẫu, hãy viết nội dung theo đúng định dạng JSON ở cuối.

Nguyên tắc nội dung:
1. Tự viết bằng lời của bạn, ngắn gọn, chính xác. Không sao chép nguyên văn giải thích hay câu ví dụ từ sách, trang web hay đề thi nào.
2. Giải thích bằng tiếng Việt tự nhiên, dễ hiểu với người học đúng cấp độ được ghi.
3. Ví dụ: đúng 3 câu tiếng Nhật tự nhiên, ngắn (tối đa khoảng 40 chữ), mỗi câu dùng đúng mẫu ngữ pháp đó, từ vựng không khó hơn cấp độ được ghi. Ba câu nên khác ngữ cảnh nhau (ví dụ đời thường, công việc, bày tỏ cảm xúc). Không viết furigana trong câu. Mỗi câu kèm bản dịch tiếng Việt tự nhiên (dịch ý, không dịch từng chữ).
4. Cách chia (connection): liệt kê mỗi cách nối thành một chuỗi, dạng công thức rõ ràng, ví dụ "Vない(bỏ ない) + ざるを得ない", "Nの + 恐れがある", "イ形容詞(い) + そうだ". Không có thì để mảng chỉ 1 chuỗi mô tả.
5. similar: chỉ ghi mẫu thực sự dễ nhầm với mẫu này (tối đa 2), kèm một câu tiếng Việt nêu khác biệt. Không có mẫu dễ nhầm thì để [].
6. notes: sắc thái (trang trọng, văn viết, văn nói, tiêu cực...), hạn chế dùng, lỗi hay gặp. Không có gì đáng nói thì để chuỗi rỗng "".
7. Nếu một mục không phải mẫu ngữ pháp có thật hoặc bạn không chắc về nghĩa, KHÔNG đoán: trả {"id": "...", "invalid": true}.

Định dạng trả về: CHỈ một mảng JSON hợp lệ, không markdown, không ```json, không lời dẫn, không giải thích thêm. Có đúng một phần tử cho mỗi mục trong danh sách, giữ nguyên thứ tự và giữ nguyên "id". Dùng dấu nháy kép chuẩn cho mọi chuỗi; trong chuỗi nếu cần nháy kép thì dùng 「」 thay thế.
Mỗi phần tử đúng dạng:
{
  "id": "N1-001",
  "pattern": "〜ざるを得ない",
  "reading": "ざるをえない",
  "connection": ["Vない(bỏ ない) + ざるを得ない (する → せざるを得ない)"],
  "meaning": "Đành phải..., buộc phải... (dù không muốn)",
  "explanation": "Diễn tả việc không còn lựa chọn nào khác nên bắt buộc phải làm, thường kèm cảm giác miễn cưỡng. Hay dùng trong văn viết và lời nói trang trọng.",
  "notes": "Không dùng cho việc người nói tự muốn làm. Thể phủ định của する là せざるを得ない.",
  "similar": [{"pattern": "〜ないわけにはいかない", "diff": "Nhấn vào lý do khách quan khiến không thể không làm; ざるを得ない nhấn vào việc không có cách khác."}],
  "examples": [
    {"ja": "雨が強いので、試合を中止せざるを得ない。", "vi": "Vì mưa to nên đành phải hủy trận đấu."},
    {"ja": "上司に頼まれたら、引き受けざるを得ません。", "vi": "Sếp đã nhờ thì không thể không nhận."},
    {"ja": "証拠がある以上、事実を認めざるを得なかった。", "vi": "Đã có bằng chứng thì không thể không thừa nhận sự thật."}
  ]
}

DANH SÁCH CẦN SOẠN:
{LIST}
