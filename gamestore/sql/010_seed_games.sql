-- Fictional demo products. Re-running does not duplicate these image paths.
BEGIN;
INSERT INTO games (title, price, genre, image_url)
SELECT sample.title, sample.price, sample.genre, sample.image_url
FROM (VALUES
    ('Aetherbound', 4800, 'Adventure', 'images/aetherbound.png'),
    ('Moonlit Harvest', 3200, 'Simulation', 'images/moonlit_harvest.png'),
    ('Abyssal Relay', 4500, 'Action', 'images/abyssal_relay.png')
) AS sample(title, price, genre, image_url)
WHERE NOT EXISTS (SELECT 1 FROM games existing WHERE existing.image_url = sample.image_url);
COMMIT;
