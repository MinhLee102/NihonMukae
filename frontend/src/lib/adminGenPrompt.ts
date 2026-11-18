/** Prompt nhờ AI soạn MỚI một đề thi thử (không có phần nghe, phần nghe được mượn từ đề khác cùng level khi thi). */

export type GenPart = "language" | "grammar" | "reading";

export const GEN_PARTS: { value: GenPart; label: string }[] = [
  { value: "language", label: "文字・語彙 (chữ Hán và từ vựng)" },
  { value: "grammar", label: "文法 (ngữ pháp)" },
  { value: "reading", label: "読解 (đọc hiểu)" },
];

interface GenType {
  code: string;
  nameVi: string;
  nameJa: string;
  part: GenPart;
  group: boolean;
  /** Số câu mỗi level. Giữ khớp với exam_blueprints trong DB (V9, V14); đổi blueprint thì sửa ở đây. */
  qty: Partial<Record<number, number>>;
  spec: string;
}

const GEN_TYPES: GenType[] = [
  {
    code: "kanji_reading", nameVi: "Đọc Hán tự", nameJa: "漢字読み", part: "language", group: false,
    qty: { 5: 7, 4: 7, 3: 8, 2: 5, 1: 6 },
    spec: "Một câu có từ viết bằng Hán tự được gạch chân <u>...</u>. 4 đáp án là 4 cách đọc bằng hiragana, chỉ một cách đúng.",
  },
  {
    code: "orthography", nameVi: "Viết Hán tự", nameJa: "表記", part: "language", group: false,
    qty: { 5: 5, 4: 5, 3: 6, 2: 5 },
    spec: "Một câu có từ viết bằng hiragana được gạch chân <u>...</u>. 4 đáp án là 4 cách viết Hán tự (hoặc katakana), chỉ một cách đúng.",
  },
  {
    code: "word_formation", nameVi: "Tiền tố, hậu tố", nameJa: "語形成", part: "language", group: false,
    qty: { 2: 3 },
    spec: "Câu có chỗ trống （　） cần điền tiền tố hoặc hậu tố (ví dụ 〜的, 〜化, 不〜, 再〜). 4 đáp án là 4 tiền tố hoặc hậu tố.",
  },
  {
    code: "context_word", nameVi: "Chọn từ theo ngữ cảnh", nameJa: "文脈規定", part: "language", group: false,
    qty: { 5: 6, 4: 8, 3: 11, 2: 7, 1: 7 },
    spec: "Câu có chỗ trống （　）. 4 đáp án là 4 từ cùng loại, chỉ một từ hợp ngữ cảnh và đi đúng với các từ xung quanh.",
  },
  {
    code: "synonym", nameVi: "Từ đồng nghĩa", nameJa: "言い換え類義", part: "language", group: false,
    qty: { 5: 3, 4: 4, 3: 5, 2: 5, 1: 6 },
    spec: "Câu có một từ hoặc cụm từ gạch chân <u>...</u>. 4 đáp án là 4 cách nói khác, chỉ một cái có nghĩa gần nhất với từ gạch chân.",
  },
  {
    code: "word_usage", nameVi: "Cách dùng từ", nameJa: "用法", part: "language", group: false,
    qty: { 4: 4, 3: 5, 2: 5, 1: 6 },
    spec: '"question" chỉ ghi MỘT từ (ví dụ 工夫). 4 đáp án là 4 câu hoàn chỉnh dùng từ đó, chỉ một câu dùng từ đúng nghĩa và đúng cách, ba câu còn lại dùng sai.',
  },
  {
    code: "grammar_choice", nameVi: "Chọn ngữ pháp", nameJa: "文の文法1", part: "grammar", group: false,
    qty: { 5: 9, 4: 13, 3: 13, 2: 12, 1: 10 },
    spec: "Câu có chỗ trống （　） cần điền trợ từ, mẫu ngữ pháp hoặc cách chia. 4 đáp án là 4 lựa chọn, chỉ một đúng.",
  },
  {
    code: "grammar_ordering", nameVi: "Sắp xếp câu", nameJa: "文の文法2", part: "grammar", group: false,
    qty: { 5: 4, 4: 4, 3: 5, 2: 5, 1: 5 },
    spec: '"question" là câu có 4 chỗ trống ＿ và một chỗ là ★, ví dụ 「私は＿＿＿ ＿＿＿ ★ ＿＿＿ 思う。」. 4 đáp án là 4 cụm từ phải xếp vào 4 chỗ trống. "correct" là đáp án nằm ở vị trí ★ sau khi xếp đúng. Trong "explanation" ghi rõ thứ tự đúng của cả 4 cụm.',
  },
  {
    code: "grammar_text", nameVi: "Ngữ pháp đoạn văn", nameJa: "文章の文法", part: "grammar", group: true,
    qty: { 5: 4, 4: 4, 3: 4, 2: 4, 1: 4 },
    spec: 'Một nhóm: "passage" là đoạn văn có các chỗ trống đánh số （　1　）（　2　）..., "questions" mỗi câu ứng với một chỗ trống, "question" ghi （　1　）... và 4 đáp án. Chỗ trống hỏi về ngữ pháp, từ nối hoặc cách diễn đạt phù hợp mạch văn.',
  },
  {
    code: "reading_short", nameVi: "Đoản văn", nameJa: "内容理解(短文)", part: "reading", group: true,
    qty: { 5: 2, 4: 3, 3: 4, 2: 5, 1: 4 },
    spec: "Các nhóm đoạn văn ngắn (khoảng 80 đến 250 chữ tùy level: thư, thông báo, bài viết ngắn). Mỗi đoạn có 1 đến 3 câu hỏi về nội dung.",
  },
  {
    code: "reading_medium", nameVi: "Trung văn", nameJa: "内容理解(中文)", part: "reading", group: true,
    qty: { 5: 2, 4: 3, 3: 6, 2: 8, 1: 8 },
    spec: "Các nhóm đoạn văn vừa (khoảng 250 đến 500 chữ). Mỗi đoạn có 2 đến 4 câu hỏi về lý do, ý nghĩa của cụm từ gạch chân, nội dung đúng với bài.",
  },
  {
    code: "reading_long", nameVi: "Trường văn (hiểu nội dung)", nameJa: "内容理解(長文)", part: "reading", group: true,
    qty: { 3: 4, 1: 3 },
    spec: "Một nhóm đoạn văn dài (khoảng 500 đến 900 chữ), có 3 đến 4 câu hỏi.",
  },
  {
    code: "reading_integrated", nameVi: "Bài so sánh", nameJa: "統合理解", part: "reading", group: true,
    qty: { 2: 2, 1: 2 },
    spec: 'Một nhóm gồm hai bài ngắn A và B cùng chủ đề nhưng khác quan điểm; ghi trong "passage" dưới dạng "A\\n...\\n\\nB\\n...". Câu hỏi so sánh hoặc tổng hợp ý của A và B.',
  },
  {
    code: "reading_argument", nameVi: "Trường văn (hiểu luận điểm)", nameJa: "主張理解(長文)", part: "reading", group: true,
    qty: { 2: 3, 1: 3 },
    spec: "Một nhóm bài luận dài (khoảng 600 đến 1000 chữ) có luận điểm của tác giả, kèm 3 câu hỏi về chủ trương và lý lẽ của tác giả.",
  },
  {
    code: "info_search", nameVi: "Tìm kiếm thông tin", nameJa: "情報検索", part: "reading", group: true,
    qty: { 5: 1, 4: 2, 3: 2, 2: 2, 1: 2 },
    spec: "Một nhóm tài liệu có bảng hoặc khung (bảng giá, lịch, thông báo có điều kiện) viết bằng [[box]] và bảng | ... |. 1 đến 2 câu hỏi đặt tình huống, người học phải đối chiếu điều kiện để chọn đáp án.",
  },
];

export type GenTypeRow = GenType & { count: number };

export function genTypesFor(level: number, part: GenPart): GenTypeRow[] {
  return GEN_TYPES.filter((t) => t.part === part && t.qty[level] !== undefined).map((t) => ({
    ...t,
    count: t.qty[level] as number,
  }));
}

export interface GenPromptOptions {
  level: number;
  number: string; // số thứ tự đề, ví dụ 01
  part: GenPart;
}

/** Tên đề dùng chung cho cả ba phần, để import ghép các phần vào cùng một đề. */
export function genTestTitle({ level, number }: Pick<GenPromptOptions, "level" | "number">): string {
  return `N${level} AI tạo ${number.trim().padStart(2, "0") || "01"}`;
}

export function buildGeneratePrompt(opts: GenPromptOptions): string {
  const types = genTypesFor(opts.level, opts.part);
  const title = genTestTitle(opts);
  const total = types.reduce((sum, t) => sum + t.count, 0);
  const partLabel = GEN_PARTS.find((p) => p.value === opts.part)?.label ?? "";
  const lines = types.map((t, i) => {
    const where = t.group ? 'đặt trong "groups"' : 'đặt trong "questions"';
    return `${i + 1}. "${t.code}" (${t.nameJa}, ${t.nameVi}): ĐÚNG ${t.count} câu, ${where}.\n   ${t.spec}`;
  });
  const groupCodes = types.filter((t) => t.group).map((t) => t.code);

  return String.raw`Bạn là giáo viên luyện thi JLPT giàu kinh nghiệm. Hãy soạn MỚI phần ${partLabel} của một đề thi thử JLPT N${opts.level}, gồm ${total} câu, trả về MỘT file JSON hợp lệ theo định dạng dưới đây.

NGUYÊN TẮC NỘI DUNG
- Tự sáng tác hoàn toàn, KHÔNG chép câu hỏi, đoạn văn hay đáp án từ đề thi, sách hay trang web nào. Độ khó, từ vựng, Hán tự và ngữ pháp phải đúng trình độ N${opts.level} theo phạm vi thi JLPT.
- Mỗi câu chỉ có MỘT đáp án đúng, các đáp án sai phải hợp lý nhưng chắc chắn sai (không có hai đáp án cùng đúng, không mơ hồ). Trước khi ghi "correct", hãy tự kiểm tra lại từng câu.
- Vị trí đáp án đúng phân bố đều giữa A, B, C, D trong cả phần, không để dồn vào một chữ cái.
- Các câu trong cùng một dạng phải khác nhau về chủ đề và từ vựng, không lặp lại.
- Mỗi câu có "explanation" bằng tiếng Việt, 1 đến 3 câu: nói vì sao đáp án đúng và (nếu cần) vì sao một đáp án gây nhầm lại sai. Có thể ghi cách đọc hoặc nghĩa của từ khóa.
- KHÔNG ghi số 問題N và câu hướng dẫn, hệ thống tự thêm. KHÔNG tạo phần nghe. KHÔNG cần hình ảnh: mọi thứ phải diễn đạt được bằng chữ.

SỐ CÂU VÀ DẠNG BÀI (đúng số câu, đúng thứ tự dưới đây)
${lines.join("\n")}
Tổng cộng ${total} câu. Với dạng đặt trong "groups", số câu là TỔNG số câu hỏi của tất cả các nhóm thuộc dạng đó cộng lại, chia thành nhóm như mô tả.

ĐỊNH DẠNG JSON
- Gốc có 3 khóa: "test", "questions", "groups" (khóa nào không có câu thì để mảng rỗng []).
- "test" LUÔN ghi y hệt: {"level": ${opts.level}, "title": "${title}", "source_note": "Do AI soạn, không phải đề thi thật"}. Không ghi year, month, audio_url. Giữ nguyên title để các phần soạn ở những lần khác ghép chung vào cùng một đề.
- Mỗi câu: "type", "question", "options" (đúng 4 chuỗi, KHÔNG kèm số thứ tự 1 2 3 4 hay A B C D), "correct" (một chữ cái A, B, C hoặc D), "explanation".
- Từ cần gạch chân thì bọc bằng <u>...</u>. Chỗ trống viết （　）. Câu sắp xếp có ★ giữ nguyên ★.
- Nhóm: {"type": "...", "title": "(tùy chọn, tiêu đề nhỏ)", "passage": "...", "questions": [ ... ]}. Các dạng nhóm trong phần này: ${groupCodes.length > 0 ? groupCodes.join(", ") : "không có"}.
- "type" chỉ dùng đúng các mã đã nêu ở trên.
- Trong "passage": **chữ** in đậm, <u>chữ</u> gạch chân, bảng mỗi hàng một dòng dạng | ô 1 | ô 2 | (thêm dòng |---|---| sau hàng tiêu đề), [[box]] ... [[/box]] khung viền, [[center]] ... [[/center]] căn giữa, [[right]] ... [[/right]] căn phải. Có thể lồng nhau. Xuống dòng trong chuỗi JSON bằng \n. KHÔNG dùng HTML hay Markdown khác.
- JSON hợp lệ: nháy kép chuẩn cho mọi chuỗi, không có dấu phẩy thừa ở cuối, không có chú thích. Nếu trong chuỗi cần nháy kép thì dùng 「」.

CÁCH TRẢ LỜI
- Chỉ trả về MỘT khối code JSON duy nhất chứa đủ ${total} câu, không lời dẫn, không giải thích thêm. Không bỏ bớt câu, không viết "tương tự", không dùng dấu ba chấm.
- Sau khối JSON, một dòng duy nhất: "Đã soạn ${total} câu" kèm số câu thực tế của từng dạng bài.`;
}
