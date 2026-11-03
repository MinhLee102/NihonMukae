-- Góp ý của người dùng: báo đề sai, lỗi hệ thống, đề xuất, transcript câu nghe
CREATE TABLE feedback (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     UUID         REFERENCES users(id) ON DELETE SET NULL,
    category    VARCHAR(20)  NOT NULL,   -- exam_error | bug | suggestion | transcript | other
    message     TEXT         NOT NULL,
    page_url    VARCHAR(300),
    context     JSONB,                   -- questionId, sessionId, testId khi góp ý gắn với một câu
    status      VARCHAR(12)  NOT NULL DEFAULT 'new',   -- new | seen | fixed | rejected
    admin_note  VARCHAR(1000),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_feedback_status ON feedback(status, created_at DESC);
