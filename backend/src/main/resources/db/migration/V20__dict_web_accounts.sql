-- 1. Tài khoản mới phải được admin duyệt mới đăng nhập được. Mọi tài khoản đang có được duyệt sẵn.
ALTER TABLE users ADD COLUMN approved BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE users SET approved = TRUE;

-- 2. Từ điển dict_* thay bảng words của Mazii.
-- edited: mục đã được admin sửa tay, import_dict.py không ghi đè nữa.
-- dict_deleted: mục admin đã xoá, import_dict.py không nạp lại.
ALTER TABLE dict_entries ADD COLUMN edited BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE dict_deleted (
    id         BIGINT PRIMARY KEY,
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Mục do admin tự thêm lấy id từ dãy này, nằm xa dãy ent_seq của JMdict (dưới 3 triệu)
CREATE SEQUENCE dict_custom_id_seq START 10000000000;

CREATE INDEX idx_dict_entries_headword ON dict_entries (headword);
CREATE INDEX idx_dict_entries_other_readings ON dict_entries USING GIN (other_readings);
CREATE INDEX idx_dict_entries_han_viet_trgm ON dict_entries USING GIN (han_viet gin_trgm_ops);
CREATE INDEX idx_dict_entries_word_trgm ON dict_entries USING GIN (word gin_trgm_ops);
CREATE INDEX idx_dict_entries_reading_trgm ON dict_entries USING GIN (reading gin_trgm_ops);
CREATE INDEX idx_dict_meanings_trgm ON dict_meanings USING GIN (meaning gin_trgm_ops);

-- 3. Flashcard và lịch sử tra cứu trỏ sang dict_entries. id Mazii cũ đổi sang ent_seq qua words.jmdict_id,
-- từ nào không có trong dict_entries thì bỏ liên kết (thẻ vẫn giữ nguyên mặt trước/sau).
ALTER TABLE flashcards DROP CONSTRAINT flashcards_word_id_fkey;
ALTER TABLE search_history DROP CONSTRAINT search_history_word_id_fkey;

UPDATE flashcards c SET word_id = (SELECT w.jmdict_id FROM words w WHERE w.id = c.word_id) WHERE c.word_id IS NOT NULL;
UPDATE search_history h SET word_id = (SELECT w.jmdict_id FROM words w WHERE w.id = h.word_id) WHERE h.word_id IS NOT NULL;
UPDATE flashcards SET word_id = NULL WHERE word_id IS NOT NULL AND word_id NOT IN (SELECT id FROM dict_entries);
UPDATE search_history SET word_id = NULL WHERE word_id IS NOT NULL AND word_id NOT IN (SELECT id FROM dict_entries);

ALTER TABLE flashcards ADD CONSTRAINT flashcards_word_id_fkey
    FOREIGN KEY (word_id) REFERENCES dict_entries (id) ON DELETE SET NULL;
ALTER TABLE search_history ADD CONSTRAINT search_history_word_id_fkey
    FOREIGN KEY (word_id) REFERENCES dict_entries (id) ON DELETE SET NULL;

-- 4. Đóng góp của người dùng: nghĩa bổ sung, câu ví dụ, ghi chú. Chỉ hiện công khai khi admin duyệt.
CREATE TABLE dict_contributions (
    id          BIGSERIAL PRIMARY KEY,
    entry_id    BIGINT       NOT NULL REFERENCES dict_entries (id) ON DELETE CASCADE,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind        VARCHAR(16)  NOT NULL CHECK (kind IN ('meaning', 'example', 'note')),
    ja          TEXT,                      -- câu tiếng Nhật, chỉ với kind = 'example'
    vi          TEXT         NOT NULL,     -- nghĩa / bản dịch / ghi chú
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    review_note TEXT,
    reviewed_by UUID         REFERENCES users (id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_dict_contrib_entry ON dict_contributions (entry_id) WHERE status = 'APPROVED';
CREATE INDEX idx_dict_contrib_status ON dict_contributions (status, created_at);
CREATE INDEX idx_dict_contrib_user ON dict_contributions (user_id, created_at DESC);
