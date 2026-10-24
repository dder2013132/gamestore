<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head><%@ include file="/WEB-INF/jspf/head.jspf" %><title>お気に入り - PLAYPORT</title></head>
<body>
<c:set var="navSection" value="wishlist" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main>
    <nav class="breadcrumb" aria-label="パンくず"><a href="${pageContext.request.contextPath}/home">ホーム</a><span>/</span><span>お気に入り</span></nav>
    <div class="page-heading"><span class="eyebrow">SAVED FOR ANOTHER LITTLE DAY</span><h1>お気に入りの小さな世界。</h1><p>気になったゲームを、ここに。ハートを押すとお気に入りから外せます。</p></div>
    <c:choose><c:when test="${not empty wishlistError}"><div class="auth-error" role="alert"><c:out value="${wishlistError}" /></div></c:when>
    <c:when test="${not empty games}"><section id="catalog" aria-labelledby="wishlist-title">
        <div class="section-heading"><h2 id="wishlist-title">あなたのお気に入り <span class="genre-label">${resultCount} 件</span></h2><a class="section-link" href="${pageContext.request.contextPath}/browse">ゲームを探す ↗</a></div>
        <div class="game-grid"><c:forEach var="game" items="${games}"><%@ include file="/WEB-INF/jspf/game-card.jspf" %></c:forEach></div>
        <%@ include file="/WEB-INF/jspf/catalog-pagination.jspf" %>
    </section></c:when>
    <c:otherwise><div class="empty-state"><span class="empty-symbol" aria-hidden="true">♡</span><h2>次のお気に入りは、これから。</h2><p>ゲーム一覧や詳細ページのハートを押して、気になる一本を保存しよう。</p><a class="button" href="${pageContext.request.contextPath}/browse">ゲームを見つける ↗</a></div></c:otherwise></c:choose>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body></html>
