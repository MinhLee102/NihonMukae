-- My Shelf: bài đọc tiếng Nhật do user tự dán vào. Furigana được tách đồng bộ
-- bằng kuromoji khi lưu nên không cần cột furigana_status
CREATE TABLE shelf_items (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(500) NOT NULL,
    content         TEXT         NOT NULL,
    content_tokens  JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_shelf_items_user ON shelf_items(user_id, updated_at DESC);
