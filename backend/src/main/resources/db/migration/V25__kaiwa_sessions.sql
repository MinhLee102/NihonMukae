-- Kaiwa: chỉ lưu tóm tắt mỗi buổi hội thoại. Nội dung từng câu nói và key Gemini của user
-- ở trong trình duyệt của họ, không đưa lên server.
CREATE TABLE kaiwa_sessions (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    level       SMALLINT     NOT NULL CHECK (level BETWEEN 1 AND 5),
    topic       VARCHAR(200) NOT NULL,
    summary     TEXT         NOT NULL,
    turns       INT          NOT NULL DEFAULT 0,
    started_at  TIMESTAMPTZ  NOT NULL,
    ended_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_kaiwa_sessions_user ON kaiwa_sessions(user_id, ended_at DESC);
