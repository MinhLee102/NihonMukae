/** Prompt cho AI ở trang Admin, Ngữ pháp. Nội dung do AI viết mới, không chép từ sách hay file tham khảo. */
import type { GrammarBatchItem } from "@/lib/api/adminGrammar";

const MAX_PER_ROUND = 150;

/** Bước 1, cách A: nhờ AI liệt kê tên các mẫu của một cấp. `existing`: các mẫu đã có, để AI liệt kê tiếp phần còn thiếu. */
export function buildListPrompt(level: number, existing: string[], withFile: boolean): string {
  const skip =
    existing.length > 0
      ? `Các mẫu sau ĐÃ có, tuyệt đối không liệt kê lại, hãy liệt kê tiếp những mẫu CÒN THIẾU của cấp này (nếu cấp này đã đủ thì trả về mảng rỗng []): ${existing.join("、")}`
      : "Hãy cố gắng liệt kê đầy đủ nhất có thể.";
  const fileNote = withFile
    ? "\nTôi có đính kèm một file tham khảo. Chỉ dùng nó để biết có những mẫu nào; tuyệt đối không chép lại câu ví dụ hay lời giải thích trong file."
    : "";
  return `Bạn là giáo viên tiếng Nhật giàu kinh nghiệm luyện thi JLPT, đang lập DANH SÁCH các mẫu ngữ pháp cho một ứng dụng học tiếng Nhật dành cho người Việt.

Nhiệm vụ: liệt kê các mẫu ngữ pháp của cấp độ N${level} (chỉ liệt kê TÊN MẪU, chưa giải thích).

Quy tắc:
- Chỉ liệt kê mẫu thật sự thuộc kỳ thi JLPT N${level} hoặc thường gặp ở cấp này. Không liệt kê mẫu của cấp khác. Không bịa mẫu không tồn tại.
- Mỗi mẫu là một mục riêng. Các biến thể gần như đồng nghĩa của cùng một mẫu (ví dụ 〜ざるを得ない / 〜ざるをえない) gộp thành MỘT mục, ghi dạng chuẩn.
- Ghi theo cách viết thường thấy trong sách, dùng 〜 ở chỗ phần từ đứng trước (ví dụ 〜ざるを得ない, 〜に違いない, 〜ば〜ほど). Trợ từ và từ nối đứng một mình thì ghi nguyên (ví dụ それに, ところが).
- Trả về cho mỗi mục: tên mẫu và cách đọc bằng hiragana.
- Danh sách tối đa ${MAX_PER_ROUND} mục. ${skip}

Định dạng trả về: CHỈ một mảng JSON hợp lệ, không markdown, không \`\`\`json, không lời dẫn, không giải thích.
Mỗi phần tử đúng dạng:
{"pattern": "〜ざるを得ない", "reading": "ざるをえない"}${fileNote}`;
}

/** Bước 1, cách B: trích tên mẫu, nhóm ý nghĩa và cách nối từ một file PDF tham khảo (đính kèm file khi dán). */
export function buildExtractPrompt(level: number, pages: string): string {
  return `Tôi đính kèm một file PDF (bản scan) là bảng tổng hợp ngữ pháp JLPT N${level}. Mỗi dòng của bảng có: số nhóm và tên nhóm ý nghĩa (chữ Nhật, ví dụ 時間関係), số thứ tự, tên mẫu, cách nối, giải thích tiếng Việt và câu ví dụ.

Nhiệm vụ: chỉ xử lý các trang ${pages.trim() || "1-10"} của file, và trích ra DANH SÁCH các mẫu ngữ pháp. Chỉ lấy những thông tin sau cho mỗi dòng:
- "pattern": tên mẫu đúng như trong cột tên mẫu. Nếu một dòng có nhiều dạng (ví dụ 〜にあって / 〜にあっても) thì ghi chúng trong MỘT chuỗi, ngăn bằng " / ".
- "reading": cách đọc của mẫu bằng hiragana.
- "category": tên nhóm ý nghĩa bằng chữ Nhật mà dòng đó thuộc về (ví dụ 時間関係, 逆接条件). Các dòng cùng nhóm dùng chung một tên.
- "hint": cách nối, chép từ cột cách nối (ví dụ "V辞書形 + が早いか"). Chỉ cần ngắn gọn, bỏ chú thích thừa.

TUYỆT ĐỐI KHÔNG chép giải thích tiếng Việt, không chép câu ví dụ và bản dịch trong file. Chỉ lấy bốn mục trên.

Quy tắc:
- Giữ nguyên thứ tự xuất hiện trong file, không bỏ sót dòng nào, không thêm dòng không có trong file.
- Nếu chữ mờ không đọc chắc được thì bỏ qua dòng đó và không đoán.
- Mỗi dòng của bảng là đúng một phần tử.

Định dạng trả về: CHỈ một mảng JSON hợp lệ, không markdown, không \`\`\`json, không lời dẫn, không giải thích. Mỗi phần tử đúng dạng:
{"pattern": "〜が早いか", "reading": "がはやいか", "category": "時間関係", "hint": "V辞書形 + が早いか"}`;
}

/** Bước 2: nhờ AI viết nội dung cho một batch mẫu. */
export function buildExplainPrompt(items: GrammarBatchItem[]): string {
  const list = items
    .map((i) =>
      JSON.stringify(
        Object.fromEntries(
          Object.entries({
            id: i.id,
            level: i.level,
            pattern: i.pattern,
            reading: i.reading ?? "",
            category: i.category ?? "",
            hint: i.hint ?? "",
          }).filter(([, v]) => v !== "")
        )
      )
    )
    .join(",\n");
  return `Bạn là giáo viên tiếng Nhật giàu kinh nghiệm luyện thi JLPT, soạn nội dung ngữ pháp cho một ứng dụng học tiếng Nhật dành cho người Việt.

Bên dưới là danh sách các mẫu ngữ pháp (có id, cấp độ JLPT, tên mẫu; có thể có "category" là nhóm ý nghĩa và "hint" là gợi ý cách nối, chỉ để tham khảo và đối chiếu, không bắt buộc theo). Với MỖI mẫu, hãy viết nội dung theo đúng định dạng JSON ở cuối.

Nguyên tắc nội dung:
1. Tự viết bằng lời của bạn, ngắn gọn, chính xác. Không sao chép nguyên văn giải thích hay câu ví dụ từ sách, trang web hay đề thi nào.
2. Giải thích bằng tiếng Việt tự nhiên, dễ hiểu với người học đúng cấp độ được ghi.
3. Ví dụ: đúng 3 câu tiếng Nhật tự nhiên, ngắn (tối đa khoảng 40 chữ), mỗi câu dùng đúng mẫu ngữ pháp đó, từ vựng không khó hơn cấp độ được ghi. Ba câu nên khác ngữ cảnh nhau (ví dụ đời thường, công việc, bày tỏ cảm xúc). Không viết furigana trong câu. Mỗi câu kèm bản dịch tiếng Việt tự nhiên (dịch ý, không dịch từng chữ).
4. Cách chia (connection): liệt kê mỗi cách nối thành một chuỗi, dạng công thức rõ ràng, ví dụ "Vない(bỏ ない) + ざるを得ない", "Nの + 恐れがある", "イ形容詞(い) + そうだ". Không có thì để mảng chỉ 1 chuỗi mô tả.
5. similar: chỉ ghi mẫu thực sự dễ nhầm với mẫu này (tối đa 2), kèm một câu tiếng Việt nêu khác biệt. Không có mẫu dễ nhầm thì để [].
6. notes: sắc thái (trang trọng, văn viết, văn nói, tiêu cực...), hạn chế dùng, lỗi hay gặp. Không có gì đáng nói thì để chuỗi rỗng "".
7. Nếu một mục không phải mẫu ngữ pháp có thật hoặc bạn không chắc về nghĩa, KHÔNG đoán: trả {"id": "...", "invalid": true}.

Định dạng trả về: CHỈ một mảng JSON hợp lệ, không markdown, không \`\`\`json, không lời dẫn, không giải thích thêm. Có đúng một phần tử cho mỗi mục trong danh sách, giữ nguyên thứ tự và giữ nguyên "id". Dùng dấu nháy kép chuẩn cho mọi chuỗi; trong chuỗi nếu cần nháy kép thì dùng 「」 thay thế.
Mỗi phần tử đúng dạng:
{
  "id": "G1",
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
[
${list}
]`;
}
