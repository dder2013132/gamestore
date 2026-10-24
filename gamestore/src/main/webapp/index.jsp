<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <%@ include file="/WEB-INF/jspf/head.jspf" %>
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
                <a class="section-link" href="#picks">今日のおすすめを見る ↓</a></div>
            <span class="hero-small">FIND SOMETHING THAT FEELS LIKE YOU.</span>
        </div>
        <c:if test="${not empty spotlight}">
            <div class="hero-art"><span class="hero-sticker">TODAY'S LITTLE DISCOVERY</span>
                <a class="hero-cover-link" href="${pageContext.request.contextPath}/games?id=${spotlight.id}">
                    <img fetchpriority="high" src="${pageContext.request.contextPath}/<c:out value='${spotlight.imagePath}' />" alt="<c:out value='${spotlight.title}' />">
                    <div class="hero-cover-info"><div><small><c:out value="${spotlight.genre}" /></small><strong><c:out value="${spotlight.title}" /></strong></div>
                        <span class="price">¥<fmt:formatNumber value="${spotlight.price}" pattern="#,##0" /></span></div>
                </a>
            </div>
        </c:if>
    </section>
    <div class="store-promise"><span>好きな世界に、ふらっと。</span><span>インディーとの出会い</span><span>小さな発見を、毎日に</span></div>
    <section class="store-section" id="picks" aria-labelledby="picks-title">
        <div class="section-heading"><div><span class="eyebrow">PICK A LITTLE ADVENTURE</span><h2 id="picks-title">今日、連れていきたい冒険。</h2></div>
            <a class="section-link" href="${pageContext.request.contextPath}/browse">すべてのゲームを見る ↗</a></div>
        <c:choose><c:when test="${not empty picks}">
            <div class="game-grid"><c:forEach var="game" items="${picks}"><%@ include file="/WEB-INF/jspf/game-card.jspf" %></c:forEach></div>
        </c:when><c:otherwise>
            <div class="empty-state"><span class="empty-symbol" aria-hidden="true">✳</span><h2>新しい冒険の準備中です。</h2><p>ゲームが登録されるまで、少しお待ちください。</p></div>
        </c:otherwise></c:choose>
    </section>
    <div class="home-browse-cta">
        <span class="eyebrow">THERE'S MORE TO DISCOVER</span>
        <h2>あなたらしい一本を、もう少し。</h2>
        <p>${catalogCount} タイトルから、名前・ジャンル・価格で探してみよう。</p>
        <a class="button button-secondary" href="${pageContext.request.contextPath}/browse">ゲーム一覧へ <span aria-hidden="true">↗</span></a>
    </div>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body>
</html>