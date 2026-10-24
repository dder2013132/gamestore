<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <%@ include file="/WEB-INF/jspf/head.jspf" %>
    <title>カート - PLAYPORT</title>
</head>
<body>
<c:set var="navSection" value="cart" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main>
<div class="page-heading"><span class="eyebrow">A LITTLE BAG OF ADVENTURES</span><h1>ショッピングカート</h1><p>次の冒険の準備は、できた？</p></div>
<c:if test="${not empty cartMessage}"><p class="auth-success" role="status"><c:out value="${cartMessage}" /></p></c:if>
<c:if test="${cartBlocked}"><p class="auth-error" role="alert">販売休止中の商品、または金額上限を超える商品があります。商品をカートから削除してから購入手続きに進んでください。</p></c:if>
<c:choose><c:when test="${not empty cart}"><div class="two-column"><div class="cart-list">
<c:forEach var="game" items="${cart}"><article class="cart-item">
    <a href="${pageContext.request.contextPath}/games?id=${game.id}"><img class="cart-thumb" src="${pageContext.request.contextPath}/<c:out value='${game.imagePath}' />" alt="<c:out value='${game.title}' />"></a>
    <div class="cart-info"><c:if test="${not game.active}"><span class="sale-status is-paused">販売休止中</span> </c:if><span class="genre-label"><c:out value="${game.genre}" /></span><h3><a href="${pageContext.request.contextPath}/games?id=${game.id}"><c:out value="${game.title}" /></a></h3><p class="price">¥<fmt:formatNumber value="${game.price}" pattern="#,##0" /></p></div>
    <form action="${pageContext.request.contextPath}/cart/remove" method="post"><input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><input type="hidden" name="gameId" value="${game.id}"><button class="button-text" type="submit" aria-label="<c:out value='${game.title}' /> をカートから削除">削除</button></form>
</article></c:forEach><a class="section-link" href="${pageContext.request.contextPath}/browse">← ショッピングを続ける</a></div>
<aside class="panel summary-panel"><span class="eyebrow">YOUR NEXT CHAPTER</span><h2>ご注文内容</h2><div class="summary-line"><span>ゲーム</span><span>${fn:length(cart)} 点</span></div><div class="summary-line summary-total"><span>合計金額</span><strong class="price">¥<fmt:formatNumber value="${totalPrice}" pattern="#,##0" /></strong></div><c:choose><c:when test="${cartBlocked}"><button class="button button-block" type="button" disabled>カートを確認してください</button></c:when><c:otherwise><a class="button button-block" href="${pageContext.request.contextPath}/checkout">購入手続きへ <span aria-hidden="true">→</span></a></c:otherwise></c:choose><p class="summary-help">注文確定前に内容を確認できます。</p></aside></div>
</c:when><c:otherwise><div class="empty-state"><span class="empty-symbol" aria-hidden="true">✳</span><h2>カートは、まだ空っぽ。</h2><p>気になるゲームを見つけて、次の冒険を始めよう。</p><a class="button" href="${pageContext.request.contextPath}/browse">ゲームを見つける ↗</a></div></c:otherwise></c:choose>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body>
</html>
