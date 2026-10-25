<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <%@ include file="/WEB-INF/jspf/head.jspf" %>
    <title><c:out value="${game.title}" /> - PLAYPORT</title>
</head>
<body>
<c:set var="navSection" value="browse" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main>
<nav class="breadcrumb" aria-label="パンくず"><a href="${pageContext.request.contextPath}/home">ホーム</a><span>/</span><a href="${pageContext.request.contextPath}/browse">ゲーム一覧</a><span>/</span><span><c:out value="${game.genre}" /></span></nav>
<div class="detail-layout">
    <div class="detail-art"><img class="game-detail-image" src="${pageContext.request.contextPath}/<c:out value='${game.imagePath}' />" alt="<c:out value='${game.title}' />"></div>
    <section class="detail-copy"><span class="eyebrow">YOUR NEXT LITTLE ADVENTURE</span><span class="genre-label"><c:out value="${game.genre}" /></span>
        <h1><c:out value="${game.title}" /></h1>
        <c:if test="${ownedIds[game.id]}"><a class="owned-library-link" href="${pageContext.request.contextPath}/library">ライブラリにあるゲーム · 一覧へ →</a></c:if>
        <c:if test="${not empty reviews}"><a class="detail-review-link" href="#reviews"><c:choose><c:when test="${reviews.totalCount > 0}"><span aria-hidden="true">★</span> <fmt:formatNumber value="${reviews.averageRating}" pattern="0.0" /> <span>（${reviews.totalCount} 件のレビュー）</span></c:when><c:otherwise>レビューを見る →</c:otherwise></c:choose></a></c:if>
        <div class="detail-wishlist"><%@ include file="/WEB-INF/jspf/wishlist-button.jspf" %><span data-wishlist-label data-game-id="${game.id}">${wishlistIds[game.id] ? 'お気に入りに保存済み' : 'お気に入りに保存'}</span></div>
        <dl class="detail-facts"><div><dt>ジャンル</dt><dd><c:out value="${game.genre}" /></dd></div><div><dt>タイトル</dt><dd><c:out value="${game.title}" /></dd></div>
        <c:if test="${empty gameDetailsError}"><div><dt>開発元</dt><dd><c:out value="${empty gameDetails.developer ? '未登録' : gameDetails.developer}" /></dd></div><div><dt>発売日</dt><dd><c:out value="${empty gameDetails.formattedReleaseDate ? '未登録' : gameDetails.formattedReleaseDate}" /></dd></div><div><dt>対応言語</dt><dd><c:out value="${empty gameDetails.supportedLanguages ? '未登録' : gameDetails.supportedLanguages}" /></dd></div></c:if></dl>
        <c:if test="${not empty gameDetailsError}"><p class="auth-error" role="alert"><c:out value="${gameDetailsError}" /></p></c:if>
        <div class="purchase-box"><span class="eyebrow">READY FOR A NEW STORY?</span><span class="price">¥<fmt:formatNumber value="${game.price}" pattern="#,##0" /></span>
            <c:choose><c:when test="${ownedIds[game.id]}"><a class="button button-block" href="${pageContext.request.contextPath}/library">ライブラリで見る →</a><p>購入済みのゲームです。もう一度購入する必要はありません。</p><a class="section-link" href="#reviews">ゲームの感想・レビューへ →</a></c:when>
            <c:when test="${game.active}"><form action="${pageContext.request.contextPath}/cart" method="post"><input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><input type="hidden" name="gameId" value="${game.id}"><button class="button button-block" type="submit">カートに追加 <span aria-hidden="true">＋</span></button></form>
            <p>${empty sessionScope.loginUser ? 'カートのご利用にはログインが必要です。' : '気になる一本を、カートに入れておこう。'}</p></c:when>
            <c:otherwise><span class="sale-status is-paused">販売休止中</span><p>現在、このゲームは購入できません。過去の注文は注文履歴で確認できます。</p></c:otherwise></c:choose><a class="section-link" href="${pageContext.request.contextPath}/browse">← ほかのゲームも探す</a>
        </div>
    </section>
</div>
<%@ include file="/WEB-INF/jspf/game-gallery.jspf" %>
<c:if test="${not empty game.description}"><section class="panel description-section"><span class="eyebrow">ABOUT THIS LITTLE WORLD</span><h2>このゲームについて</h2><div class="description-copy"><c:out value="${game.description}" /></div></section></c:if>
<%@ include file="/WEB-INF/jspf/game-reviews.jspf" %>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body>
</html>
