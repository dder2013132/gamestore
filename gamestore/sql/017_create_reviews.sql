-- One atomic statement; no account, game or order is changed.
DO $$
BEGIN
    CREATE TABLE IF NOT EXISTS reviews (
        review_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
        game_id INTEGER NOT NULL REFERENCES games(game_id) ON DELETE CASCADE,
        rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
        body VARCHAR(2000) NOT NULL CHECK (char_length(btrim(body)) BETWEEN 1 AND 2000),
        version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        UNIQUE (user_id, game_id)
    );
    CREATE INDEX IF NOT EXISTS reviews_game_created_idx ON reviews (game_id, created_at DESC, review_id DESC);
    CREATE INDEX IF NOT EXISTS reviews_user_updated_idx ON reviews (user_id, updated_at DESC, review_id DESC);
END $$;
