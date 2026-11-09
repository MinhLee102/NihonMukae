-- Tìm tiếng Việt không dấu (nhat ban -> Nhật Bản). unaccent không IMMUTABLE nên bọc lại mới đánh chỉ mục được.
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE OR REPLACE FUNCTION immutable_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    AS $$ SELECT public.unaccent('public.unaccent', $1) $$;

CREATE INDEX idx_dict_meanings_unaccent_trgm ON dict_meanings USING GIN (immutable_unaccent(meaning) gin_trgm_ops);
CREATE INDEX idx_dict_entries_han_viet_unaccent_trgm ON dict_entries USING GIN (immutable_unaccent(han_viet) gin_trgm_ops);
