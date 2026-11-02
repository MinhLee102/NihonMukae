-- Hồ sơ người dùng, khóa tài khoản, và kho file media (audio, ảnh) của đề thi.

ALTER TABLE users
    ADD COLUMN target_level   SMALLINT,                       -- cấp độ mong muốn, 1 (N1) đến 5 (N5), null = chưa chọn
    ADD COLUMN enabled        BOOLEAN     NOT NULL DEFAULT true, -- false = bị admin khóa
    ADD COLUMN last_login_at  TIMESTAMPTZ;

-- File upload của admin. storage_key là tên file trong thư mục local hoặc id file trên Google Drive.
CREATE TABLE media_files (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    kind           VARCHAR(10)  NOT NULL,          -- audio | image
    mime_type      VARCHAR(100) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    sha256         CHAR(64)     NOT NULL UNIQUE,   -- chống upload trùng nội dung
    original_name  VARCHAR(255),
    storage        VARCHAR(20)  NOT NULL,          -- local | drive
    storage_key    VARCHAR(255) NOT NULL,
    uploaded_by    UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_media_files_uploader ON media_files(uploaded_by, created_at DESC);
