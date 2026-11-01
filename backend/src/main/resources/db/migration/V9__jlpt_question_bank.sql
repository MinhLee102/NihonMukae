-- Kho câu hỏi dùng chung (Reading, Listening, JLPT, ôn theo dạng bài), blueprint đề thi
-- và phiên làm bài. Số liệu blueprint lấy theo bảng cách tính điểm trong spec (admin sửa được).

CREATE TABLE question_types (
    id              SMALLSERIAL  PRIMARY KEY,
    code            VARCHAR(50)  UNIQUE NOT NULL,
    name_vi         VARCHAR(100) NOT NULL,
    name_ja         VARCHAR(100),
    section         VARCHAR(20)  NOT NULL,          -- moji | goi | bunpou | dokkai | chokai
    levels          SMALLINT[]   NOT NULL,          -- level áp dụng, vd {5,4,3}
    option_count    SMALLINT     NOT NULL DEFAULT 4,
    is_group_based  BOOLEAN      NOT NULL DEFAULT false, -- true: đơn vị random là nhóm (đoạn văn/audio)
    sort_order      SMALLINT     NOT NULL DEFAULT 0,
    is_active       BOOLEAN      NOT NULL DEFAULT true
);

CREATE TABLE jlpt_tests (
    id          BIGSERIAL    PRIMARY KEY,
    level       SMALLINT     NOT NULL,
    title       VARCHAR(500),
    source_note VARCHAR(200),
    year        SMALLINT,
    month       SMALLINT,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (level, year, month)
);

CREATE TABLE question_groups (
    id            BIGSERIAL    PRIMARY KEY,
    type_id       SMALLINT     REFERENCES question_types(id),
    level         SMALLINT     NOT NULL,
    test_id       BIGINT       REFERENCES jlpt_tests(id) ON DELETE SET NULL,
    test_order    SMALLINT,
    title         VARCHAR(500),
    passage       TEXT,
    audio_url     TEXT,
    image_url     TEXT,
    topic         VARCHAR(100),
    source        VARCHAR(30)  NOT NULL DEFAULT 'admin',
    content_hash  CHAR(64)     UNIQUE,
    is_active     BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE questions (
    id            BIGSERIAL    PRIMARY KEY,
    type_id       SMALLINT     NOT NULL REFERENCES question_types(id),
    level         SMALLINT     NOT NULL,
    test_id       BIGINT       REFERENCES jlpt_tests(id) ON DELETE SET NULL,
    test_order    SMALLINT,
    group_id      BIGINT       REFERENCES question_groups(id) ON DELETE CASCADE,
    question      TEXT         NOT NULL,
    audio_url     TEXT,
    options       JSONB        NOT NULL,            -- ["…","…","…","…"] (3-4 phần tử)
    correct       CHAR(1)      NOT NULL,            -- A | B | C | D
    explanation   TEXT,
    source        VARCHAR(30)  NOT NULL DEFAULT 'admin',
    content_hash  CHAR(64)     UNIQUE NOT NULL,     -- chống trùng khi import
    is_active     BOOLEAN      NOT NULL DEFAULT true,
    created_by    UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_questions_type_level ON questions(type_id, level) WHERE is_active;
CREATE INDEX idx_questions_test       ON questions(test_id, test_order);
CREATE INDEX idx_questions_group      ON questions(group_id);
CREATE INDEX idx_groups_type_level    ON question_groups(type_id, level) WHERE is_active;

CREATE TABLE exam_blocks (
    level         SMALLINT     NOT NULL,
    block_no      SMALLINT     NOT NULL,
    name          VARCHAR(100) NOT NULL,
    duration_min  SMALLINT     NOT NULL,
    PRIMARY KEY (level, block_no)
);

CREATE TABLE exam_blueprints (
    id                   SERIAL       PRIMARY KEY,
    level                SMALLINT     NOT NULL,
    block_no             SMALLINT     NOT NULL,
    type_id              SMALLINT     NOT NULL REFERENCES question_types(id),
    quantity             SMALLINT     NOT NULL,
    points_per_question  NUMERIC(4,2) NOT NULL,
    sort_order           SMALLINT     NOT NULL DEFAULT 0,
    FOREIGN KEY (level, block_no) REFERENCES exam_blocks(level, block_no),
    UNIQUE (level, type_id)
);

CREATE TABLE exam_levels (
    level        SMALLINT PRIMARY KEY,
    total_score  SMALLINT NOT NULL DEFAULT 180,
    pass_score   SMALLINT NOT NULL
);

CREATE TABLE exam_scoring_sections (
    level      SMALLINT     NOT NULL REFERENCES exam_levels(level),
    code       VARCHAR(20)  NOT NULL,               -- language | reading | language_reading | listening
    sections   TEXT[]       NOT NULL,               -- question_types.section thuộc phần này
    max_score  SMALLINT     NOT NULL,
    min_score  SMALLINT     NOT NULL,               -- điểm liệt
    PRIMARY KEY (level, code)
);

CREATE TABLE practice_sessions (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mode            VARCHAR(10)  NOT NULL,          -- practice | exam
    level           SMALLINT     NOT NULL,
    title           VARCHAR(500),
    test_id         BIGINT       REFERENCES jlpt_tests(id) ON DELETE SET NULL,
    is_random       BOOLEAN      NOT NULL DEFAULT false,
    type_ids        SMALLINT[],
    question_count  SMALLINT,
    time_limit_min  SMALLINT,                       -- null = không giới hạn (chỉ practice)
    feedback_mode   VARCHAR(10)  NOT NULL DEFAULT 'after',  -- instant | after (exam luôn after)
    status          VARCHAR(15)  NOT NULL DEFAULT 'in_progress', -- in_progress | submitted
    block_state     JSONB        NOT NULL DEFAULT '[]'::jsonb,   -- [{blockNo, name, durationMin, startedAt, deadline, submittedAt}]
    snapshot        JSONB,                          -- nội dung câu hỏi và kết quả lúc nộp bài
    score           NUMERIC(6,2),
    max_score       NUMERIC(6,2),
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ
);
CREATE INDEX idx_sessions_user ON practice_sessions(user_id, started_at DESC);

CREATE TABLE practice_answers (
    id           BIGSERIAL    PRIMARY KEY,
    session_id   UUID         NOT NULL REFERENCES practice_sessions(id) ON DELETE CASCADE,
    question_id  BIGINT       REFERENCES questions(id) ON DELETE SET NULL,
    position     SMALLINT     NOT NULL,             -- thứ tự câu trong phiên
    block_no     SMALLINT     NOT NULL DEFAULT 1,
    type_id      SMALLINT,
    points       NUMERIC(4,2) NOT NULL DEFAULT 1,
    selected     CHAR(1),
    is_correct   BOOLEAN,
    locked       BOOLEAN      NOT NULL DEFAULT false,
    answered_at  TIMESTAMPTZ,
    UNIQUE (session_id, position)
);
CREATE INDEX idx_practice_answers_session ON practice_answers(session_id);

-- ═══ Dữ liệu tham chiếu ═══════════════════════════════════════════
INSERT INTO question_types (code, name_vi, name_ja, section, levels, option_count, is_group_based, sort_order) VALUES
 ('kanji_reading',      'Đọc Hán tự',                       '漢字読み',     'moji',   '{5,4,3,2,1}', 4, false, 10),
 ('orthography',        'Nhớ chữ Hán / viết Hán tự',        '表記',         'moji',   '{5,4,3,2}',   4, false, 11),
 ('word_formation',     'Tiền tố, hậu tố',                  '語形成',       'moji',   '{2}',         4, false, 12),
 ('context_word',       'Chọn từ theo ngữ cảnh',            '文脈規定',     'goi',    '{5,4,3,2,1}', 4, false, 20),
 ('synonym',            'Từ đồng nghĩa',                    '言い換え類義', 'goi',    '{5,4,3,2,1}', 4, false, 21),
 ('word_usage',         'Cách dùng từ',                     '用法',         'goi',    '{4,3,2,1}',   4, false, 22),
 ('grammar_choice',     'Chọn ngữ pháp',                    '文の文法1',    'bunpou', '{5,4,3,2,1}', 4, false, 30),
 ('grammar_ordering',   'Sắp xếp câu',                      '文の文法2',    'bunpou', '{5,4,3,2,1}', 4, false, 31),
 ('grammar_text',       'Ngữ pháp đoạn văn',               '文章の文法',   'bunpou', '{5,4,3,2,1}', 4, true,  32),
 ('reading_short',      'Đoản văn',                         '内容理解(短文)', 'dokkai', '{5,4,3,2,1}', 4, true, 40),
 ('reading_medium',     'Trung văn',                        '内容理解(中文)', 'dokkai', '{5,4,3,2,1}', 4, true, 41),
 ('reading_long',       'Trường văn (hiểu nội dung)',       '内容理解(長文)', 'dokkai', '{1}',         4, true, 42),
 ('reading_integrated', 'Bài so sánh',                      '統合理解',     'dokkai', '{2,1}',       4, true,  43),
 ('reading_argument',   'Trường văn (hiểu luận điểm)',      '主張理解(長文)', 'dokkai', '{3,2,1}',     4, true,  44),
 ('info_search',        'Tìm kiếm thông tin',               '情報検索',     'dokkai', '{5,4,3,2,1}', 4, true,  45),
 ('listen_task',        'Hiểu đề bài',                      '課題理解',     'chokai', '{5,4,3,2,1}', 4, false, 50),
 ('listen_point',       'Hiểu điểm chính',                  'ポイント理解', 'chokai', '{5,4,3,2,1}', 4, false, 51),
 ('listen_overview',    'Hiểu bao quát',                    '概要理解',     'chokai', '{3,2,1}',     4, false, 52),
 ('listen_expression',  'Diễn đạt bằng lời nói',            '発話表現',     'chokai', '{5,4,3}',     3, false, 53),
 ('listen_quick',       'Phản hồi tức thì',                 '即時応答',     'chokai', '{5,4,3,2,1}', 3, false, 54),
 ('listen_integrated',  'Hiểu tổng hợp',                    '統合理解',     'chokai', '{2,1}',       4, true,  55);

INSERT INTO exam_levels (level, total_score, pass_score) VALUES (5, 180, 80), (4, 180, 90), (3, 180, 95), (2, 180, 90), (1, 180, 100);

INSERT INTO exam_scoring_sections (level, code, sections, max_score, min_score) VALUES
 (5, 'language_reading', '{moji,goi,bunpou,dokkai}', 120, 38), (5, 'listening', '{chokai}', 60, 19),
 (4, 'language_reading', '{moji,goi,bunpou,dokkai}', 120, 38), (4, 'listening', '{chokai}', 60, 19),
 (3, 'language', '{moji,goi,bunpou}', 60, 19), (3, 'reading', '{dokkai}', 60, 19), (3, 'listening', '{chokai}', 60, 19),
 (2, 'language', '{moji,goi,bunpou}', 60, 19), (2, 'reading', '{dokkai}', 60, 19), (2, 'listening', '{chokai}', 60, 19),
 (1, 'language', '{moji,goi,bunpou}', 60, 19), (1, 'reading', '{dokkai}', 60, 19), (1, 'listening', '{chokai}', 60, 19);

INSERT INTO exam_blocks (level, block_no, name, duration_min) VALUES
 (5, 1, 'Moji-Goi', 25), (5, 2, 'Bunpou-Dokkai', 50), (5, 3, 'Chokai', 30),
 (4, 1, 'Moji-Goi', 30), (4, 2, 'Bunpou-Dokkai', 60), (4, 3, 'Chokai', 35),
 (3, 1, 'Moji-Goi', 30), (3, 2, 'Bunpou-Dokkai', 70), (3, 3, 'Chokai', 40),
 (2, 1, 'Ngôn ngữ + Đọc hiểu', 105), (2, 2, 'Chokai', 50),
 (1, 1, 'Ngôn ngữ + Đọc hiểu', 110), (1, 2, 'Chokai', 60);

-- Blueprint: số câu x điểm mỗi câu theo level. Khối: N5 đến N3 dùng 3 khối, N2 và N1 dùng 2 khối
INSERT INTO exam_blueprints (level, block_no, type_id, quantity, points_per_question, sort_order)
SELECT v.level,
       CASE WHEN t.section IN ('moji', 'goi') THEN 1
            WHEN t.section IN ('bunpou', 'dokkai') THEN CASE WHEN v.level >= 3 THEN 2 ELSE 1 END
            ELSE CASE WHEN v.level >= 3 THEN 3 ELSE 2 END END,
       t.id, v.qty, v.pts, t.sort_order
FROM (VALUES
 ('kanji_reading', 5, 7, 2), ('kanji_reading', 4, 7, 1), ('kanji_reading', 3, 8, 1), ('kanji_reading', 2, 5, 1), ('kanji_reading', 1, 6, 1),
 ('orthography', 5, 5, 2), ('orthography', 4, 5, 2), ('orthography', 3, 6, 1), ('orthography', 2, 5, 1),
 ('word_formation', 2, 3, 1),
 ('context_word', 5, 6, 2), ('context_word', 4, 8, 2), ('context_word', 3, 11, 1), ('context_word', 2, 7, 1), ('context_word', 1, 7, 1.5),
 ('synonym', 5, 3, 3), ('synonym', 4, 4, 2), ('synonym', 3, 5, 1), ('synonym', 2, 5, 1), ('synonym', 1, 6, 1),
 ('word_usage', 4, 4, 3), ('word_usage', 3, 5, 1), ('word_usage', 2, 5, 1), ('word_usage', 1, 6, 1.25),
 ('grammar_choice', 5, 9, 3), ('grammar_choice', 4, 13, 1), ('grammar_choice', 3, 13, 1), ('grammar_choice', 2, 12, 1), ('grammar_choice', 1, 10, 1),
 ('grammar_ordering', 5, 4, 3), ('grammar_ordering', 4, 4, 3), ('grammar_ordering', 3, 5, 1), ('grammar_ordering', 2, 5, 2), ('grammar_ordering', 1, 5, 2),
 ('grammar_text', 5, 4, 3), ('grammar_text', 4, 4, 2.5), ('grammar_text', 3, 4, 1.75), ('grammar_text', 2, 4, 2), ('grammar_text', 1, 4, 2.5),
 ('reading_short', 5, 2, 5), ('reading_short', 4, 3, 3), ('reading_short', 3, 4, 3.5), ('reading_short', 2, 5, 3), ('reading_short', 1, 4, 3),
 ('reading_medium', 5, 2, 5), ('reading_medium', 4, 3, 5), ('reading_medium', 3, 6, 4), ('reading_medium', 2, 8, 3), ('reading_medium', 1, 8, 2.5),
 ('reading_long', 1, 3, 3),
 ('reading_integrated', 2, 2, 3), ('reading_integrated', 1, 2, 3),
 ('reading_argument', 3, 4, 4), ('reading_argument', 2, 3, 3), ('reading_argument', 1, 3, 3),
 ('info_search', 5, 1, 4), ('info_search', 4, 2, 4), ('info_search', 3, 2, 3), ('info_search', 2, 2, 3), ('info_search', 1, 2, 2),
 ('listen_task', 5, 7, 2.5), ('listen_task', 4, 8, 2), ('listen_task', 3, 6, 2), ('listen_task', 2, 5, 2), ('listen_task', 1, 5, 2),
 ('listen_point', 5, 6, 2.5), ('listen_point', 4, 7, 2.5), ('listen_point', 3, 6, 2), ('listen_point', 2, 6, 2.5), ('listen_point', 1, 6, 2.5),
 ('listen_overview', 3, 3, 3), ('listen_overview', 2, 5, 3), ('listen_overview', 1, 5, 3),
 ('listen_expression', 5, 5, 2.5), ('listen_expression', 4, 5, 2.5), ('listen_expression', 3, 4, 2.25),
 ('listen_quick', 5, 6, 2.5), ('listen_quick', 4, 8, 1.75), ('listen_quick', 3, 9, 2), ('listen_quick', 2, 11, 1), ('listen_quick', 1, 11, 1),
 ('listen_integrated', 2, 3, 3), ('listen_integrated', 1, 3, 3)
) AS v(code, level, qty, pts)
JOIN question_types t ON t.code = v.code;
