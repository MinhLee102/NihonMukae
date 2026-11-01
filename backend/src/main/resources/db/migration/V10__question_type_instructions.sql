-- Đề bài chung của từng dạng bài (hiện phía trên câu hỏi, đánh số "問題 N" như đề JLPT thật).
-- Admin sửa được; câu hỏi không cần lặp lại đề bài trong nội dung.
ALTER TABLE question_types
    ADD COLUMN instruction_ja TEXT,
    ADD COLUMN instruction_vi TEXT;

UPDATE question_types AS t SET instruction_ja = v.ja, instruction_vi = v.vi
FROM (VALUES
 ('kanji_reading',      '＿＿＿の言葉の読み方として最もよいものを、1・2・3・4から一つ選びなさい。',
                        'Chọn cách đọc đúng nhất của từ gạch chân, trong 1・2・3・4.'),
 ('orthography',        '＿＿＿の言葉を漢字で書くとき、最もよいものを、1・2・3・4から一つ選びなさい。',
                        'Chọn cách viết Hán tự đúng nhất cho từ gạch chân, trong 1・2・3・4.'),
 ('word_formation',     '（　）に入れるのに最もよいものを、1・2・3・4から一つ選びなさい。',
                        'Chọn đáp án điền vào chỗ trống (tiền tố, hậu tố) trong 1・2・3・4.'),
 ('context_word',       '（　）に入れるのに最もよいものを、1・2・3・4から一つ選びなさい。',
                        'Chọn từ phù hợp nhất để điền vào chỗ trống, trong 1・2・3・4.'),
 ('synonym',            '＿＿＿の言葉に意味が最も近いものを、1・2・3・4から一つ選びなさい。',
                        'Chọn từ có nghĩa gần nhất với từ gạch chân, trong 1・2・3・4.'),
 ('word_usage',         '次の言葉の使い方として最もよいものを、1・2・3・4から一つ選びなさい。',
                        'Chọn câu dùng từ đã cho đúng nhất, trong 1・2・3・4.'),
 ('grammar_choice',     '次の文の（　）に入れるのに最もよいものを、1・2・3・4から一つ選びなさい。',
                        'Chọn đáp án điền vào chỗ trống trong câu, trong 1・2・3・4.'),
 ('grammar_ordering',   '次の文の＿★＿に入る最もよいものを、1・2・3・4から一つ選びなさい。',
                        'Sắp xếp các cụm cho thành câu đúng, chọn cụm đứng ở vị trí ★.'),
 ('grammar_text',       '次の文章を読んで、文章全体の内容を考えて、（　）に入れるのに最もよいものを、1・2・3・4から一つ選びなさい。',
                        'Đọc đoạn văn, nghĩ đến nội dung toàn bài rồi chọn đáp án điền vào chỗ trống.'),
 ('reading_short',      '次の文章を読んで、後の問いに答えなさい。答えは、1・2・3・4から最もよいものを一つ選びなさい。',
                        'Đọc đoạn văn rồi trả lời câu hỏi, chọn đáp án đúng nhất trong 1・2・3・4.'),
 ('reading_medium',     '次の文章を読んで、後の問いに答えなさい。答えは、1・2・3・4から最もよいものを一つ選びなさい。',
                        'Đọc đoạn văn rồi trả lời câu hỏi, chọn đáp án đúng nhất trong 1・2・3・4.'),
 ('reading_long',       '次の文章を読んで、後の問いに答えなさい。答えは、1・2・3・4から最もよいものを一つ選びなさい。',
                        'Đọc đoạn văn dài rồi trả lời câu hỏi, chọn đáp án đúng nhất trong 1・2・3・4.'),
 ('reading_integrated', '次のAとBの文章を読んで、後の問いに答えなさい。答えは、1・2・3・4から最もよいものを一つ選びなさい。',
                        'Đọc hai bài A và B rồi trả lời câu hỏi, chọn đáp án đúng nhất trong 1・2・3・4.'),
 ('reading_argument',   '次の文章を読んで、後の問いに答えなさい。答えは、1・2・3・4から最もよいものを一つ選びなさい。',
                        'Đọc bài luận rồi trả lời câu hỏi về quan điểm của tác giả, chọn trong 1・2・3・4.'),
 ('info_search',        '次のページの案内を読んで、下の問いに答えなさい。答えは、1・2・3・4から最もよいものを一つ選びなさい。',
                        'Đọc thông báo rồi trả lời câu hỏi, chọn đáp án đúng nhất trong 1・2・3・4.'),
 ('listen_task',        'まず質問を聞いてください。それから話を聞いて、問題用紙の1から4の中から、最もよいものを一つ選んでください。',
                        'Nghe câu hỏi trước, rồi nghe hội thoại và chọn đáp án đúng nhất trong 1 đến 4.'),
 ('listen_point',       'まず質問を聞いてください。そのあと、問題用紙のせんたくしを読んでください。それから話を聞いて、1から4の中から、最もよいものを一つ選んでください。',
                        'Nghe câu hỏi, đọc các lựa chọn, rồi nghe hội thoại và chọn đáp án đúng nhất.'),
 ('listen_overview',    '問題用紙に何も印刷されていません。まず話を聞いてください。それから、質問とせんたくしを聞いて、1から4の中から、最もよいものを一つ選んでください。',
                        'Nghe nội dung trước, sau đó nghe câu hỏi và các lựa chọn rồi chọn đáp án đúng nhất.'),
 ('listen_expression',  'イラストを見ながら質問を聞いてください。矢印の人は何と言いますか。1から3の中から、最もよいものを一つ選んでください。',
                        'Nhìn hình minh họa và nghe câu hỏi: nhân vật có mũi tên sẽ nói gì? Chọn trong 1 đến 3.'),
 ('listen_quick',       'まず文を聞いてください。それから、それに対する返事を聞いて、1から3の中から、最もよいものを一つ選んでください。',
                        'Nghe một câu, rồi nghe các câu đáp lại và chọn câu đáp phù hợp nhất trong 1 đến 3.'),
 ('listen_integrated',  '問題用紙に何も印刷されていません。話を聞いてください。それから、質問とせんたくしを聞いて、1から4の中から、最もよいものを一つ選んでください。',
                        'Nghe nội dung dài, sau đó nghe câu hỏi và các lựa chọn rồi chọn đáp án đúng nhất.')
) AS v(code, ja, vi)
WHERE t.code = v.code;
