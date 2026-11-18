/** Prompt nhờ AI chuyển một đề JLPT (PDF hoặc ảnh) thành JSON import và danh sách ảnh cần cắt. Giữ khớp với docs/JLPT_IMPORT_FORMAT.md. */

export interface ExamPromptOptions {
  level: number; // 1 đến 5
  year: string;
  month: string;
}

/** Tiền tố tên file, ví dụ n3_2023_07, dùng cho audio và ảnh của đề. */
export function examFilePrefix({ level, year, month }: ExamPromptOptions): string {
  const y = year.trim() || "2023";
  const m = month.trim().padStart(2, "0") || "07";
  return `n${level}_${y}_${m}`;
}

export function buildExamPrompt(opts: ExamPromptOptions): string {
  const p = examFilePrefix(opts);
  return String.raw`Hãy chuyển đề thi JLPT N${opts.level} đính kèm thành MỘT file JSON hợp lệ theo đúng định dạng dưới đây, kèm danh sách ảnh cần cắt.

CÁCH TRẢ LỜI (đúng 2 khối, không lời dẫn)
- Khối 1: một khối code JSON duy nhất, chứa TOÀN BỘ đề. Không bỏ bớt câu, không viết "tương tự", không dùng dấu ba chấm.
- Khối 2: mục "DANH SÁCH ẢNH CẦN CẮT" dạng bảng (cột: tên file, số thứ tự câu, trang trong file đề, vị trí trên trang, mô tả ngắn). Không có ảnh nào cần cắt thì ghi "Không có".
- Cuối cùng, nếu có, liệt kê "CÂU CHƯA CHẮC CHẮN" (số thứ tự câu và lý do).
- Nếu đề quá dài để trả lời một lần, hãy dừng ở cuối một nhóm câu và nói rõ đã làm đến đâu, tôi sẽ nhắn "tiếp tục". Đừng cắt ngang giữa một câu hay một nhóm.

CẤU TRÚC
- Gốc có 3 khóa: "test" (level, title, year, month, audio_url), "questions", "groups".
- "test": {"level": ${opts.level}, "title": "JLPT N${opts.level} ${opts.year.trim() || "2023"}-${opts.month.trim().padStart(2, "0") || "07"}", "year": ..., "month": ..., "audio_url": "${p}_listening.mp3"}. audio_url là tên file audio của CẢ phần nghe. Nếu đề không có phần nghe thì bỏ audio_url.
- Câu đơn vào "questions". Các câu dùng chung một đoạn văn, bảng, thông báo hoặc ảnh vào "groups", mỗi nhóm có "type", "passage" (hoặc "image_url") và "questions".
- Giữ đúng thứ tự như trong đề. Câu nghe PHẢI theo đúng thứ tự trong audio (1番, 2番, 3番...), và không cần audio_url riêng.

MỖI CÂU
- "type", "question", "options" (3 hoặc 4 chuỗi, KHÔNG kèm số thứ tự 1 2 3 4), "correct" (chữ cái A, B, C hoặc D), "explanation" (giải thích ngắn bằng tiếng Việt).
- Từ được gạch chân trong đề thì bọc bằng <u>...</u>. Chỗ trống giữ nguyên là （　）. Câu sắp xếp có ★ giữ nguyên ★.
- KHÔNG ghi số 問題N và câu hướng dẫn, hệ thống tự thêm.
- Câu nghe: "question" ghi 質問を聞いてください。 và "options" ghi "1","2","3","4" nếu đề không in chữ. Nếu đề in sẵn đáp án thì ghi đúng chữ.
- Câu nghe có script (スクリプト) trong tài liệu: ghi nguyên văn tiếng Nhật vào "transcript", mỗi lượt nói một dòng (男：..., 女：...). KHÔNG tự nghe hoặc đoán, không có script thì bỏ trường này.

"type" CHỈ DÙNG CÁC MÃ
kanji_reading, orthography, word_formation, context_word, synonym, word_usage, grammar_choice, grammar_ordering, grammar_text, reading_short, reading_medium, reading_long, reading_integrated, reading_argument, info_search, listen_task, listen_point, listen_overview, listen_expression, listen_quick, listen_integrated.
Các dạng nhóm (grammar_text, reading_*, info_search, listen_integrated) bắt buộc đặt trong "groups".
Chọn "type" cho phần 読解 theo tên 問題 trong đề: 内容理解(短文) là reading_short, 内容理解(中文) là reading_medium, 内容理解(長文) là reading_long (chỉ N3 và N1), 統合理解 là reading_integrated (N2, N1), 主張理解(長文) là reading_argument (N2, N1), 情報検索 là info_search. N3 KHÔNG có reading_argument.
Phần 聴解: 課題理解 là listen_task, ポイント理解 là listen_point, 概要理解 là listen_overview (N3 trở lên), 発話表現 là listen_expression (N3 trở xuống), 即時応答 là listen_quick, 統合理解 là listen_integrated (N2, N1).

HÌNH TRONG ĐỀ (tôi sẽ tự cắt ảnh từ PDF, bạn chỉ ghi tên file)
- Tên file ảnh theo mẫu ${p}_q03.png (số câu), nếu nhiều hình trong một câu thì ${p}_q10_a.png, ${p}_q10_b.png.
- Hình nằm ở đề bài: viết [[img:${p}_q03.png]] vào "question". Có thể kèm chữ ở dòng sau, ví dụ "[[img:${p}_q10_scene.png]]\n矢印の人は何と言いますか。".
- 4 đáp án là một hình ghép đánh số 1 đến 4: "options" là "1","2","3","4" và đặt hình vào "question".
- Mỗi đáp án là một hình riêng: "options" là [[img:${p}_q10_a.png]], [[img:${p}_q10_b.png]]...
- Đáp án bằng chữ thì giữ nguyên chữ, không đổi thành ảnh.
- Mỗi hình chỉ một dòng trong bảng ảnh, tên file phải trùng đúng với tên trong JSON.

ĐỊNH DẠNG BÀI ĐỌC (trong "passage", chỉ dùng các ký hiệu này, KHÔNG dùng HTML hay Markdown khác)
- **chữ** để in đậm, <u>chữ</u> để gạch chân.
- Bảng: mỗi hàng một dòng dạng | ô 1 | ô 2 |. Nếu hàng đầu là tiêu đề thì thêm dòng |---|---| ngay sau nó.
- [[box]] ... [[/box]] cho phần có khung viền (thông báo, tờ rơi, quảng cáo).
- [[center]] ... [[/center]] cho tiêu đề căn giữa, [[right]] ... [[/right]] cho ngày tháng, tên người gửi hoặc người ký ở bên phải.
- Có thể lồng các thẻ vào nhau. Xuống dòng trong chuỗi JSON bằng \n.
- Email: ghi đủ các dòng 宛先, 件名, nội dung, người gửi như bản gốc, mỗi dòng một dòng.
- Nếu một bài có hình ảnh, biểu đồ hoặc bố cục quá phức tạp không thể diễn tả bằng các ký hiệu trên, hãy ĐỂ "passage" là "[ẢNH]" và ghi "image_url" theo mẫu ${p}_reading_q45.png, rồi thêm ảnh đó vào bảng ảnh.

ĐÁP ÁN
- Phần chữ Hán, từ vựng, ngữ pháp, đọc hiểu: nếu đề không có đáp án kèm theo, hãy tự giải và ghi "correct" cẩn thận, ghi những câu chưa chắc chắn vào "explanation" và vào mục "CÂU CHƯA CHẮC CHẮN".
- Phần NGHE (聴解): bạn KHÔNG nghe được audio, nên TUYỆT ĐỐI không đoán đáp án. Chỉ ghi "correct" khi đề có bảng đáp án hoặc bản chép lời (スクリプト) để suy ra được. Câu nào không có căn cứ thì ghi "correct": "?" và liệt kê số thứ tự các câu nghe thiếu đáp án ở mục "CÂU CHƯA CHẮC CHẮN" để tôi tự điền.

QUY TẮC JSON
- JSON hợp lệ: nháy kép chuẩn cho mọi chuỗi, không có dấu phẩy thừa ở cuối, không có chú thích. Nếu trong chuỗi cần nháy kép thì dùng 「」.`;
}
