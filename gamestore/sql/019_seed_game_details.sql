-- Fictional demo information only. Never overwrite an administrator's saved details.
-- The gallery is AI-created concept art, not screenshots of a released game.
DO $$
DECLARE demo_game INTEGER;
BEGIN
    INSERT INTO game_details (game_id, developer, release_date, supported_languages, gallery_note)
    SELECT game_id, 'Little Moon Studio (架空)', DATE '2026-09-18', '日本語・한국어・English',
        '架空のゲームのために制作したAI生成のイメージ画像です。実際のプレイ画面ではありません。'
    FROM games WHERE title = 'Moonlit Harvest' AND image_url = 'images/moonlit_harvest.png' ORDER BY game_id LIMIT 1
    ON CONFLICT (game_id) DO NOTHING
    RETURNING game_id INTO demo_game;
    IF demo_game IS NOT NULL THEN
        INSERT INTO game_screenshots (game_id, position, image_path) VALUES
            (demo_game, 1, 'images/screenshots/moonlit-farm.png'),
            (demo_game, 2, 'images/screenshots/moonlit-night.png');
    END IF;
END $$;
