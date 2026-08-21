-- Cần index này để searchByTrigramSimilarity()
-- không phải full scan 222k dòng mỗi lần fallback.
CREATE INDEX IF NOT EXISTS idx_words_kana_trgm ON words USING gin (kana gin_trgm_ops);