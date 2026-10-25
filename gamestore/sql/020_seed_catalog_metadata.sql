-- Fictional metadata for the demo catalog, matched by cover path so renamed titles keep working.
-- Fill only blank metadata. Never change games, gallery notes or gallery images.
DO $$
BEGIN
    INSERT INTO game_details AS existing (game_id, developer, release_date, supported_languages)
    SELECT game.game_id, sample.developer, sample.release_date, sample.languages
    FROM (VALUES
        ('images/stellar_odyssey.jpg', 'Playport Pocket Studio (架空)', DATE '2025-11-21', '日本語・한국어・English'),
        ('images/neon_drift.jpg', 'Nightlane Games (架空)', DATE '2026-03-06', '日本語・English'),
        ('images/moonlit_harvest.png', 'Little Moon Studio (架空)', DATE '2026-09-18', '日本語・한국어・English'),
        ('images/abyssal_relay.png', 'Deep Signal Studio (架空)', DATE '2026-02-13', '日本語・한국어・English'),
        ('images/aetherbound.png', 'Skyweave Studio (架空)', DATE '2025-10-17', '日本語・English'),
        ('images/ember_express.png', 'Emberline Works (架空)', DATE '2026-08-07', '日本語・한국어・English'),
        ('images/lantern_post.png', 'Foxglove Post Studio (架空)', DATE '2026-10-02', '日本語・한국어・English'),
        ('images/cloudberry_cafe.png', 'Cloudberry Atelier (架空)', DATE '2026-07-17', '日本語・한국어・English'),
        ('images/tideglass.png', 'Seaglass Studio (架空)', DATE '2026-05-22', '日本語・English'),
        ('images/mossbound.png', 'Mosslight Games (架空)', DATE '2026-04-10', '日本語・한국어・English'),
        ('images/starlight_courier.png', 'Orbit Parcel Studio (架空)', DATE '2026-06-12', '日本語・English'),
        ('images/fablewood_chronicles.png', 'Fableleaf Works (架空)', DATE '2025-12-05', '日本語・한국어・English')
    ) AS sample(image_url, developer, release_date, languages)
    JOIN games game ON game.image_url = sample.image_url
    ON CONFLICT (game_id) DO UPDATE SET
        developer = CASE WHEN BTRIM(existing.developer) = '' THEN EXCLUDED.developer ELSE existing.developer END,
        release_date = COALESCE(existing.release_date, EXCLUDED.release_date),
        supported_languages = CASE WHEN BTRIM(existing.supported_languages) = '' THEN EXCLUDED.supported_languages ELSE existing.supported_languages END
    WHERE BTRIM(existing.developer) = '' OR existing.release_date IS NULL OR BTRIM(existing.supported_languages) = '';
END $$;
