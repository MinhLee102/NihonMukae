CREATE TABLE flashcard_decks (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    color       VARCHAR(7),
    icon        VARCHAR(50),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE flashcards (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    deck_id     UUID         NOT NULL REFERENCES flashcard_decks(id) ON DELETE CASCADE,
    front       TEXT         NOT NULL,
    back        TEXT         NOT NULL,
    image_url   TEXT,
    word_id     BIGINT       REFERENCES words(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE flashcard_reviews (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    card_id         UUID         NOT NULL REFERENCES flashcards(id) ON DELETE CASCADE,
    ease_factor     REAL         NOT NULL DEFAULT 2.5,
    interval_days   INTEGER      NOT NULL DEFAULT 1,
    repetitions     INTEGER      NOT NULL DEFAULT 0,
    next_review_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_reviewed   TIMESTAMPTZ,
    UNIQUE (user_id, card_id)
);

CREATE INDEX idx_flashcard_decks_user_id    ON flashcard_decks(user_id);
CREATE INDEX idx_flashcards_deck_id         ON flashcards(deck_id);
CREATE INDEX idx_flashcard_reviews_user_id  ON flashcard_reviews(user_id);
CREATE INDEX idx_flashcard_reviews_next     ON flashcard_reviews(user_id, next_review_at);