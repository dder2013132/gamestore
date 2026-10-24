-- Japanese descriptions for the fictional demo catalog.
-- Only fills empty descriptions; existing administrator edits are preserved.
-- One atomic statement: no explicit BEGIN/COMMIT is needed in A5:SQL.
WITH descriptions (title, image_url, description) AS (
    VALUES
    ('PLAYPORT Adventure', 'images/stellar_odyssey.jpg', $description$まだ見ぬ星へ、あなただけの冒険を。

遠い宇宙に浮かぶ不思議な惑星を巡り、古い遺跡に残された手がかりをたどるアドベンチャー。色鮮やかな景色の向こうには、思いがけない出会いと小さな謎が待っています。

気になる場所に寄り道したり、見つけたものをつなぎ合わせたり。好奇心を道しるべに、星々に眠る物語を少しずつ解き明かしていこう。$description$),
    ('Neon Drift', 'images/neon_drift.jpg', $description$街が眠るころ、あなたの走りが目を覚ます。

ネオンがきらめく夜の街を舞台に、コーナーを駆け抜けるレーシングゲーム。光の流れるストリートと雨に輝く路面が、いつもの一周を特別な時間に変えてくれます。

ブレーキのタイミング、曲がる角度、立ち上がりの加速。少しずつ走りを磨きながら、自分だけの気持ちいいラインを見つけよう。次の一周で、昨日の自分を追い越して。$description$),
    ('Moonlit Harvest', 'images/moonlit_harvest.png', $description$月明かりの下で、暮らしをゆっくり育てよう。

丘の上の小さな農家から始まる、穏やかな農園シミュレーション。畑に種をまき、水をあげ、育った作物を収穫する。そんな小さな積み重ねが、あなただけの居場所をつくっていきます。

畑仕事のあとは、蛍の舞う小道を散歩したり、家の明かりを眺めたり。急がなくても大丈夫。今日できたことをひとつ喜びながら、自分のペースでこの町の暮らしを楽しんで。$description$),
    ('Abyssal Relay', 'images/abyssal_relay.png', $description$深海の静けさの中で、途切れた信号を追え。

小さな探査艇に乗り込み、海溝の底に眠る巨大な中継施設を目指すアクション探索ゲーム。青白い光に浮かぶ建造物と、闇の中できらめく生き物たちが、未知の世界へと誘います。

狭い通路を慎重に進み、周囲を調べながら信号の発信源へ。頼れるのは探査艇の灯りと、わずかに届く通信だけ。静かな緊張感の先に隠された、深海の秘密を見届けよう。$description$),
    ('Aetherbound', 'images/aetherbound.png', $description$雲の海を越えて、忘れられた空の物語へ。

夕日に染まる浮遊島と、島々を結ぶ古い石橋を旅するファンタジーアドベンチャー。風に揺れるマントをまとい、空に残された遺跡や不思議な仕掛けをたどっていきます。

足元に広がる雲海、遠くに見える塔、道の途中で見つかる小さな手がかり。景色を楽しみながら寄り道を重ね、この世界が空に浮かぶ理由に近づこう。次の橋の向こうに、新しい発見が待っています。$description$),
    ('Ember Express', 'images/ember_express.png', $description$切符を一枚。夕暮れの向こうへ出発しよう。

赤と真鍮の蒸気機関車で、山あいの町を結ぶアドベンチャー。雪をいただく峰、川に架かる石橋、明かりのともる客室。車窓に流れる景色のひとつひとつが、旅の思い出になります。

途中の駅で人々の話に耳を傾け、小さな頼まれごとを通して沿線の物語にふれていこう。汽笛が響いたら、次の町へ。あたたかな灯りと少しの好奇心を乗せて、あなただけの旅を始めませんか。$description$)
)
UPDATE games AS game
SET description = sample.description
FROM descriptions AS sample
WHERE game.title = sample.title
  AND game.image_url = sample.image_url
  AND (game.description IS NULL OR game.description ~ '^[[:space:]]*$')
RETURNING game.game_id, game.title;
