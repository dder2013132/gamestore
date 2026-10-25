<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <%@ include file="/WEB-INF/jspf/head.jspf" %>
    <script defer src="${pageContext.request.contextPath}/js/hero.js?v=cozy-21"></script>
    <title>PLAYPORT — 小さなゲーム、大きな物語。</title>
</head>
<body>
<c:set var="navSection" value="home" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main class="home-main">
    <section class="hero" aria-labelledby="hero-title">
        <div class="hero-copy">
            <span class="eyebrow">A LITTLE PLAY, A NEW STORY.</span>
            <h1 id="hero-title">小さなゲームから、<br><em>大きな物語へ。</em></h1>
            <p class="hero-description">ひとりの時間に、ちょっとした冒険を。<br>あなたのお気に入りの一本を、ここで見つけよう。</p>
            <div class="hero-actions"><a class="button" href="${pageContext.request.contextPath}/browse">ゲームを見つける <span aria-hidden="true">↗</span></a>
                <a class="section-link" href="#picks">新着ゲームを見る ↓</a></div>
            <span class="hero-small">FIND SOMETHING THAT FEELS LIKE YOU.</span>
        </div>
        <c:set var="heroSlides" value="${home.heroSlides}" />
        <c:if test="${not empty heroSlides}">
            <div class="hero-art hero-carousel" data-hero-carousel role="region" aria-roledescription="カルーセル" aria-label="新着・人気ゲームのピックアップ">
                <div class="hero-slides" data-hero-slides aria-live="off">
                    <c:forEach var="slide" items="${heroSlides}" varStatus="position">
                        <div class="hero-slide" data-hero-slide role="group" aria-roledescription="スライド" aria-label="${position.count} / ${fn:length(heroSlides)} · <c:out value='${slide.game.title}' />" ${position.first ? '' : 'hidden'}>
                            <a class="hero-cover-link" href="${pageContext.request.contextPath}/games?id=${slide.game.id}">
                                <span class="hero-feature-label"><span aria-hidden="true">✳</span> <c:out value="${slide.label}" /></span>
                                <img ${position.first ? 'fetchpriority="high"' : 'loading="lazy"'} src="${pageContext.request.contextPath}/<c:out value='${slide.game.imagePath}' />" alt="<c:out value='${slide.game.title}' />">
                                <div class="hero-cover-info"><div><small><c:out value="${slide.game.genre}" /><c:if test="${slide.popular}"> · ${slide.buyerCount} 人が購入</c:if></small><strong><c:out value="${slide.game.title}" /></strong><span class="hero-game-link">この世界をのぞく <span aria-hidden="true">↗</span></span></div>
                                    <span class="price">¥<fmt:formatNumber value="${slide.game.price}" pattern="#,##0" /></span></div>
                            </a>
                            <c:if test="${ownedIds[slide.game.id]}"><a class="owned-spotlight-label" href="${pageContext.request.contextPath}/library">✓ ライブラリに追加済み →</a></c:if>
                        </div>
                    </c:forEach>
                </div>
                <div class="hero-carousel-controls" data-hero-controls hidden>
                    <div class="hero-slide-dots" role="group" aria-label="表示するゲームを選ぶ"><c:forEach var="slide" items="${heroSlides}" varStatus="position"><button type="button" class="hero-slide-dot" data-hero-dot aria-label="${position.count}：<c:out value='${slide.game.title}' />" aria-current="${position.first ? 'true' : 'false'}"><span></span></button></c:forEach></div>
                    <span class="hero-slide-count" data-hero-count aria-hidden="true">01 / <fmt:formatNumber value="${fn:length(heroSlides)}" pattern="00" /></span>
                    <div class="hero-slide-actions"><button type="button" data-hero-prev aria-label="前のゲーム">←</button><button type="button" data-hero-next aria-label="次のゲーム">→</button><button type="button" class="hero-play-button" data-hero-play aria-label="自動切替を停止"><span data-hero-play-icon aria-hidden="true">Ⅱ</span></button></div>
                </div>
            </div>
        </c:if>
    </section>
    <div class="store-promise"><span>好きな世界に、ふらっと。</span><span>インディーとの出会い</span><span>小さな発見を、毎日に</span></div>
    <c:choose><c:when test="${not empty homeError}"><section class="panel home-load-error"><h2>少し時間をおいて、もう一度。</h2><p class="auth-error" role="alert"><c:out value="${homeError}" /></p><div class="order-actions"><a class="button" href="${pageContext.request.contextPath}/home">もう一度読み込む</a><a class="button button-secondary" href="${pageContext.request.contextPath}/browse">ゲーム一覧へ</a></div></section></c:when><c:otherwise>
    <nav class="home-section-nav" aria-label="ゲームの見つけ方"><a href="#picks">新着ゲーム ↓</a><a href="#popular-games">人気のゲーム ↓</a><a href="#top-rated-games">高評価のゲーム ↓</a></nav>
    <section class="store-section" id="picks" aria-labelledby="picks-title">
        <div class="section-heading"><div><span class="eyebrow">FRESH ON THE LITTLE SHELF</span><h2 id="picks-title">新しく並んだゲーム。</h2><p class="home-section-note">ストアへの登録が新しい順に、次の冒険をご紹介。</p></div>
            <a class="section-link" href="${pageContext.request.contextPath}/browse">すべてのゲームを見る ↗</a></div>
        <c:choose><c:when test="${not empty home.newGames}"><c:set var="discoveryKind" value="new" />
            <div class="game-grid home-games-grid"><c:forEach var="homeEntry" items="${home.newGames}"><c:set var="game" value="${homeEntry.game}" /><%@ include file="/WEB-INF/jspf/game-card.jspf" %></c:forEach></div>
        </c:when><c:otherwise>
            <div class="empty-state"><span class="empty-symbol" aria-hidden="true">✳</span><h2>新しい冒険の準備中です。</h2><p>ゲームが登録されるまで、少しお待ちください。</p></div>
        </c:otherwise></c:choose>
    </section>
    <section class="store-section" id="popular-games" aria-labelledby="popular-title">
        <div class="section-heading"><div><span class="eyebrow">LITTLE WORLDS, MUCH LOVED</span><h2 id="popular-title">みんなが選んだゲーム。</h2><p class="home-section-note">注文を完了した購入者数の多い順。支払い待ち・キャンセルは含みません。</p></div><a class="section-link" href="${pageContext.request.contextPath}/browse">ほかのゲームも探す ↗</a></div>
        <c:choose><c:when test="${not empty home.popularGames}"><c:set var="discoveryKind" value="popular" /><div class="game-grid home-games-grid"><c:forEach var="homeEntry" items="${home.popularGames}"><c:set var="game" value="${homeEntry.game}" /><%@ include file="/WEB-INF/jspf/game-card.jspf" %></c:forEach></div></c:when>
        <c:otherwise><div class="home-section-empty"><span aria-hidden="true">✳</span><div><h3>人気の物語は、これから。</h3><p>注文が完了したゲームが、ここに少しずつ並びます。</p><a class="section-link" href="${pageContext.request.contextPath}/browse">お気に入りの一本を探す ↗</a></div></div></c:otherwise></c:choose>
    </section>
    <section class="store-section" id="top-rated-games" aria-labelledby="top-rated-title">
        <div class="section-heading"><div><span class="eyebrow">SMALL REVIEWS, LOVELY DISCOVERIES</span><h2 id="top-rated-title">感想から見つける、お気に入り。</h2><p class="home-section-note">平均評価の高い順。同じ評価では、レビュー数の多いゲームから。</p></div><a class="section-link" href="${pageContext.request.contextPath}/library">遊んだゲームに感想を ↗</a></div>
        <c:choose><c:when test="${not empty home.topRatedGames}"><c:set var="discoveryKind" value="rated" /><div class="game-grid home-games-grid"><c:forEach var="homeEntry" items="${home.topRatedGames}"><c:set var="game" value="${homeEntry.game}" /><%@ include file="/WEB-INF/jspf/game-card.jspf" %></c:forEach></div></c:when>
        <c:otherwise><div class="home-section-empty"><span aria-hidden="true">☆</span><div><h3>最初の「よかった」を待っています。</h3><p>レビューが投稿されると、評価と感想の数を添えてご紹介します。</p><a class="section-link" href="${pageContext.request.contextPath}/library">ライブラリから感想を残す ↗</a></div></div></c:otherwise></c:choose>
    </section>
    <div class="home-browse-cta">
        <span class="eyebrow">THERE'S MORE TO DISCOVER</span>
        <h2>あなたらしい一本を、もう少し。</h2>
        <p>${home.catalogCount} タイトルから、名前・ジャンル・価格で探してみよう。</p>
        <a class="button button-secondary" href="${pageContext.request.contextPath}/browse">ゲーム一覧へ <span aria-hidden="true">↗</span></a>
    </div>
    </c:otherwise></c:choose>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body>
</html>
