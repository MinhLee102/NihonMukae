-- Shadowing: bài luyện của từng user = video YouTube + phụ đề đã chia câu (do user dán).
-- Không lưu audio hay bản thu: video phát từ YouTube, bản thu chỉ ở trong trình duyệt.
CREATE TABLE shadowing_materials (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    video_id           VARCHAR(20)  NOT NULL,
    title              VARCHAR(200) NOT NULL,
    -- [{"start": giây, "end": giây, "text": "..."}], theo thứ tự thời gian
    segments           JSONB        NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_practiced_at  TIMESTAMPTZ
);

CREATE INDEX idx_shadowing_materials_user ON shadowing_materials(user_id, updated_at DESC);

-- Các câu user đánh dấu "cần luyện" (chỉ số trong segments)
CREATE TABLE shadowing_marks (
    material_id  UUID        NOT NULL REFERENCES shadowing_materials(id) ON DELETE CASCADE,
    seg_idx      INT         NOT NULL,
    marked_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (material_id, seg_idx)
);
