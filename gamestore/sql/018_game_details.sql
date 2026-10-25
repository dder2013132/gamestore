-- One atomic statement; safe to run again. Existing catalog and orders are preserved.
DO $$
BEGIN
    CREATE TABLE IF NOT EXISTS game_details (
        game_id INTEGER PRIMARY KEY REFERENCES games(game_id) ON DELETE CASCADE,
        developer VARCHAR(100) NOT NULL DEFAULT '',
        release_date DATE CHECK (release_date BETWEEN DATE '1000-01-01' AND DATE '9999-12-31'),
        supported_languages VARCHAR(200) NOT NULL DEFAULT '',
        gallery_note VARCHAR(200) NOT NULL DEFAULT ''
    );
    CREATE TABLE IF NOT EXISTS game_screenshots (
        game_id INTEGER NOT NULL REFERENCES game_details(game_id) ON DELETE CASCADE,
        position SMALLINT NOT NULL CHECK (position BETWEEN 1 AND 6),
        image_path VARCHAR(300) NOT NULL CHECK (image_path ~ '^images/screenshots/[a-z0-9][a-z0-9_.-]*[.](png|jpg|jpeg|webp)$'),
        PRIMARY KEY (game_id, position),
        UNIQUE (game_id, image_path)
    );
END $$;
