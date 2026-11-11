-- Bằng chứng người dùng đã đồng ý với một việc xử lý dữ liệu (hiện có: Kaiwa gửi nội dung hội thoại tới Google).
-- Lưu theo phiên bản nội dung đồng ý: khi nội dung đổi, tăng phiên bản để hỏi lại.
-- Rút lại đồng ý thì xoá dòng tương ứng.
CREATE TABLE user_consents (
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind         VARCHAR(32) NOT NULL,
    version      INT         NOT NULL,
    accepted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, kind)
);
