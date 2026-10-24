CREATE TABLE IF NOT EXISTS wishlist_items (
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    game_id INTEGER NOT NULL REFERENCES games(game_id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, game_id)
);
CREATE INDEX IF NOT EXISTS wishlist_user_created_idx ON wishlist_items (user_id, created_at DESC, game_id DESC);
