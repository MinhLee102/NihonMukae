-- V1__init_schema.sql
-- Initial schema placeholder
-- Extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";     -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "pg_trgm";      -- trigram fuzzy search

-- =============================================================
-- USERS & AUTH
-- =============================================================

CREATE TABLE users (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255),                        -- NULL nếu chỉ dùng OAuth
    name        VARCHAR(100),
    avatar_url  TEXT,
    provider    VARCHAR(20)  NOT NULL DEFAULT 'local', -- 'local' | 'google'
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',  -- 'USER' | 'ADMIN'
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       TEXT        NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token   ON refresh_tokens(token);

-- =============================================================
-- DICTIONARY
-- =============================================================

CREATE TABLE words (
    id              BIGSERIAL    PRIMARY KEY,
    jmdict_id       INTEGER      UNIQUE,
    kanji           VARCHAR(100),
    kana            VARCHAR(100) NOT NULL,
    romaji          VARCHAR(200),
    jlpt_level      SMALLINT,               -- 1=N1 ... 5=N5, NULL=unknown
    frequency_rank  INTEGER,
    search_vector   TSVECTOR,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Full-text search index (GIN)
CREATE INDEX idx_words_search_vector ON words USING GIN(search_vector);

-- Exact match indexes
CREATE INDEX idx_words_kanji  ON words(kanji);
CREATE INDEX idx_words_kana   ON words(kana);
CREATE INDEX idx_words_jlpt   ON words(jlpt_level);

-- Fuzzy / partial match (trigram)
CREATE INDEX idx_words_kanji_trgm  ON words USING GIN(kanji  gin_trgm_ops);
CREATE INDEX idx_words_kana_trgm   ON words USING GIN(kana   gin_trgm_ops);
CREATE INDEX idx_words_romaji_trgm ON words USING GIN(romaji gin_trgm_ops);

CREATE TABLE word_meanings (
    id          BIGSERIAL   PRIMARY KEY,
    word_id     BIGINT      NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    lang        VARCHAR(5)  NOT NULL,   -- 'vi' | 'en'
    pos         VARCHAR(50),            -- part of speech: 'noun', 'verb', ...
    meaning     TEXT        NOT NULL,
    sort_order  SMALLINT    NOT NULL DEFAULT 0,
    UNIQUE (word_id, lang, sort_order)
);

CREATE INDEX idx_word_meanings_word_id ON word_meanings(word_id);
CREATE INDEX idx_word_meanings_lang    ON word_meanings(word_id, lang);

CREATE TABLE example_sentences (
    id          BIGSERIAL   PRIMARY KEY,
    word_id     BIGINT      NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    lang_src    VARCHAR(5)  NOT NULL DEFAULT 'ja',
    lang_tgt    VARCHAR(5)  NOT NULL,   -- 'vi' | 'en'
    sentence    TEXT        NOT NULL,
    translation TEXT
);

CREATE INDEX idx_example_sentences_word_id ON example_sentences(word_id);

-- =============================================================
-- SEARCH HISTORY (requires users)
-- =============================================================

CREATE TABLE search_history (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    word_id     BIGINT      REFERENCES words(id) ON DELETE SET NULL,
    raw_query   VARCHAR(255),
    searched_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Query: lấy lịch sử gần nhất của user, sort by time DESC
CREATE INDEX idx_search_history_user_time
    ON search_history(user_id, searched_at DESC);