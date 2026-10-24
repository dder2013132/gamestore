-- Six fictional indie games for the demo storefront. Existing products and edits are preserved.
-- Safe to re-run: a matching title OR image path prevents another insertion.
INSERT INTO games (title, price, genre, image_url, description)
SELECT sample.title, sample.price, sample.genre, sample.image_url, sample.description
FROM (VALUES
    ('Lantern Post', 2400, 'Adventure', 'images/lantern_post.png', $story$小さな手紙が、森の町をつないでいく。

キツネの配達員になって、灯りのともる村々へ手紙を届けるアドベンチャー。石畳の道を自転車で走り、住人たちの話に耳を傾けながら、一通ずつ大切な想いを運びます。

寄り道の先には、誰かの忘れ物や思いがけない出会いが。秋風に背中を押されて、今日もあなたを待つポストへ出発しよう。$story$),
    ('Cloudberry Cafe', 1800, 'Simulation', 'images/cloudberry_cafe.png', $story$雲の上の小さなカフェ、本日も開店です。

空に浮かぶ島で、焼きたてのお菓子とあたたかい飲み物を用意するカフェシミュレーション。ベリーの香りに包まれながら、お客さんの好みに合わせた一杯を届けよう。

店先に植物を飾ったり、気になるレシピを試したり。忙しさの合間には窓の外を眺めてひと休み。あなたらしいお店を、少しずつ育てていきましょう。$story$),
    ('Tideglass', 1600, 'Puzzle', 'images/tideglass.png', $story$波と光が教えてくれる、海辺のひらめき。

古い灯台と潮だまりを巡り、ガラスのプリズムに光を通して仕掛けを解くパズルゲーム。水面に映る模様や石に刻まれた印が、次の一歩の手がかりになります。

眺める角度を変え、光の道をつないでみよう。答えにたどり着いた瞬間、静かな海の景色が少し違って見えるはず。潮風とともに、考える時間を楽しんで。$story$),
    ('Mossbound', 2200, 'Platformer', 'images/mossbound.png', $story$小さな一跳びで、森の世界はぐっと広がる。

苔をまとった小さな精霊が、種のランタンを手に大きな森を旅するプラットフォーマー。巨大なキノコや絡み合う木の根を足場に、光の差す道を探していきます。

ジャンプのタイミングをつかみ、隠れた小道へひとっ飛び。道中で出会う森の仲間とともに、眠っていた森の息吹を取り戻そう。$story$),
    ('Starlight Courier', 2600, 'Racing', 'images/starlight_courier.png', $story$星の道を走って、夜空に荷物を届けよう。

小さなホバースクーターで浮遊島を結ぶ、配達レーシングゲーム。星座の下に伸びる曲がりくねった道を駆け抜け、光る目印を頼りに目的地を目指します。

コーナーをなめらかに曲がり、近道を見つけて配達ルートを磨いていこう。速さだけでなく、景色を楽しむ余裕も旅のおともに。今夜の届け先は、どんな島でしょう。$story$),
    ('Fablewood Chronicles', 3600, 'RPG', 'images/fablewood_chronicles.png', $story$物語の扉は、森の奥であなたを待っている。

旅人とキツネの魔法使い、小さな鎧の仲間が力を合わせるファンタジーRPG。古い森の門をくぐり、遺跡に残された記録や町の人々の言葉から、この世界の物語をたどります。

仲間それぞれの得意なことを生かし、行く手の困難をひとつずつ越えていこう。大きな冒険の始まりは、小さな約束から。あなたの選ぶ道が、新しい一章になります。$story$)
) AS sample(title, price, genre, image_url, description)
WHERE NOT EXISTS (SELECT 1 FROM games existing WHERE existing.title = sample.title OR existing.image_url = sample.image_url)
RETURNING game_id, title;
