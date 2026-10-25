<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head><%@ include file="/WEB-INF/jspf/head.jspf" %><title>マイページ - PLAYPORT</title></head>
<body>
<c:set var="navSection" value="account" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main class="account-page">
<div class="account-heading page-heading"><div><span class="eyebrow">YOUR OWN LITTLE CORNER</span><h1>マイページ</h1><p>お気に入りも、これまでの冒険も。ここから、ひとつずつ。</p></div><span class="account-flower" aria-hidden="true">✳</span></div>
<c:choose><c:when test="${not empty error}">
    <div class="panel"><p class="auth-error" role="alert"><c:out value="${error}" /></p><div class="account-retry-actions"><a class="button button-secondary" href="${pageContext.request.contextPath}/account">もう一度読み込む</a><a class="section-link" href="${pageContext.request.contextPath}/orders">注文履歴へ</a><a class="section-link" href="${pageContext.request.contextPath}/wishlist">お気に入りへ</a></div></div>
</c:when><c:otherwise>
<section class="account-welcome" aria-label="会員情報"><div><span class="account-avatar" aria-hidden="true">P</span><div><h2><c:out value="${account.username}" /><small> さん、おかえりなさい。</small></h2><p>次の「好き」が、きっと見つかる。</p></div></div><span class="account-joined">${account.formattedJoinedAt} からのメンバー</span></section>
<nav class="account-stats" aria-label="マイページのメニュー">
    <a class="panel account-stat" href="${pageContext.request.contextPath}/library"><span class="account-stat-icon" aria-hidden="true">✳</span><span class="account-stat-label">ライブラリのゲーム</span><strong><fmt:formatNumber value="${account.libraryCount}" pattern="#,##0" /><small> 本</small></strong><span class="account-stat-link">自分のライブラリへ →</span></a>
    <a class="panel account-stat" href="${pageContext.request.contextPath}/wishlist"><span class="account-stat-icon" aria-hidden="true">♡</span><span class="account-stat-label">気になるゲーム</span><strong><fmt:formatNumber value="${account.wishlistCount}" pattern="#,##0" /><small> 本</small></strong><span class="account-stat-link">お気に入りを見る →</span></a>
    <a class="panel account-stat account-security" href="${pageContext.request.contextPath}/account/password"><span class="account-stat-icon" aria-hidden="true">✧</span><span class="account-stat-label">アカウント設定</span><strong>パスワード</strong><span class="account-stat-link">パスワードを変更 →</span></a>
</nav>
<section class="panel account-review-entry"><div><span class="eyebrow">YOUR PLAYER NOTES</span><h2>自分のレビュー</h2><p>書いた感想を見返したり、編集したり。</p></div><a class="button button-secondary button-small" href="${pageContext.request.contextPath}/account/reviews">レビューを管理 →</a></section>
<div class="account-sections">
    <section class="panel" aria-labelledby="account-orders-title"><div class="section-heading"><div><span class="eyebrow">RECENT ADVENTURES</span><h2 id="account-orders-title">最近の注文</h2></div><a class="section-link" href="${pageContext.request.contextPath}/orders">すべて見る →</a></div>
        <c:choose><c:when test="${empty account.recentOrders}"><div class="account-empty"><span aria-hidden="true">↗</span><h3>最初の冒険は、これから。</h3><p>注文したゲームの記録が、ここに並びます。</p><a class="button button-secondary button-small" href="${pageContext.request.contextPath}/browse">ゲームを探す</a></div></c:when><c:otherwise>
            <ul class="account-order-list"><c:forEach var="order" items="${account.recentOrders}"><c:url var="recentOrderUrl" value="/order/complete"><c:param name="id" value="${order.orderId}" /></c:url><li><a href="<c:out value='${recentOrderUrl}' />"><div><span class="order-status is-${order.status.cssClass}"><c:out value="${order.status.label}" /></span><span class="order-date"><c:out value="${order.formattedCreatedAt}" /></span><span class="order-code"><c:out value="${order.orderId}" /></span></div><span class="account-order-price"><strong class="price">¥<fmt:formatNumber value="${order.totalPrice}" pattern="#,##0" /></strong><small>詳細を見る ↗</small></span></a></li></c:forEach></ul>
        </c:otherwise></c:choose>
    </section>
    <section class="panel" aria-labelledby="account-wishlist-title"><div class="section-heading"><div><span class="eyebrow">SAVED FOR A LITTLE LATER</span><h2 id="account-wishlist-title">最近のお気に入り</h2></div><a class="section-link" href="${pageContext.request.contextPath}/wishlist">すべて見る →</a></div>
        <c:choose><c:when test="${empty account.recentWishes}"><div class="account-empty"><span aria-hidden="true">♡</span><h3>「気になる」を、とっておこう。</h3><p>ゲームのハートを押すと、ここから見返せます。</p><a class="button button-secondary button-small" href="${pageContext.request.contextPath}/browse">お気に入りを探す</a></div></c:when><c:otherwise>
            <ul class="account-wish-list"><c:forEach var="game" items="${account.recentWishes}"><li><a href="${pageContext.request.contextPath}/games?id=${game.id}"><img width="64" height="64" src="${pageContext.request.contextPath}/<c:out value='${game.imagePath}' />" alt=""><div><span class="genre-label"><c:out value="${game.genre}" /></span><h3><c:out value="${game.title}" /></h3><c:choose><c:when test="${game.active}"><span class="price">¥<fmt:formatNumber value="${game.price}" pattern="#,##0" /></span></c:when><c:otherwise><span class="sale-status is-paused">販売休止中</span></c:otherwise></c:choose></div><span class="account-wish-arrow" aria-hidden="true">↗</span></a></li></c:forEach></ul>
        </c:otherwise></c:choose>
    </section>
</div>
<div class="account-browse"><span>今日は、どんな世界に寄り道する？</span><a class="section-link" href="${pageContext.request.contextPath}/browse">ゲーム一覧へ ↗</a></div>
</c:otherwise></c:choose>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body></html>
