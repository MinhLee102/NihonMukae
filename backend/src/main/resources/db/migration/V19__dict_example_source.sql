-- Nguồn của câu ví dụ: 'ai' (Gemini viết) hoặc 'tatoeba' (Tatoeba / Tanaka Corpus, CC BY 2.0 FR).
-- source_id là mã câu Tatoeba, dùng để dẫn link ghi nguồn.
ALTER TABLE dict_examples
    ADD COLUMN source    VARCHAR(16) NOT NULL DEFAULT 'ai',
    ADD COLUMN source_id BIGINT;
