-- Lịch sử quiz của bộ thẻ: chỉ lưu điểm từng lượt (không lưu từng câu), mỗi người giữ tối đa 200 lượt gần nhất.
CREATE TABLE flashcard_quiz_attempts (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    deck_id    UUID         REFERENCES flashcard_decks (id) ON DELETE SET NULL,
    deck_name  VARCHAR(200) NOT NULL,
    kinds      VARCHAR(40)  NOT NULL,   -- các dạng đã chọn, cách nhau bằng dấu phẩy: meaning,reading,cloze
    total      SMALLINT     NOT NULL,
    correct    SMALLINT     NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_quiz_attempts_user ON flashcard_quiz_attempts (user_id, created_at DESC);
