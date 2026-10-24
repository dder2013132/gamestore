<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <%@ include file="/WEB-INF/jspf/head.jspf" %>
    <title>ご注文ありがとうございます - PLAYPORT</title>
</head>
<body>
<c:set var="navSection" value="orders" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main class="order-success">
<div class="success-intro"><span class="success-stamp" aria-hidden="true">✓</span><span class="eyebrow">A NEW ADVENTURE BEGINS</span><h1>ご注文、ありがとうございます。</h1><p>注文が完了しました。選んだゲームの内容はこちらです。</p></div>
<c:if test="${not empty order}"><section class="panel"><div class="order-meta"><small>注文番号</small><span class="order-code"><c:out value="${order.orderId}" /></span></div><div class="line-items"><c:forEach var="game" items="${order.games}"><div class="line-item"><img src="${pageContext.request.contextPath}/<c:out value='${game.imagePath}' />" alt=""><div class="line-item-info"><span class="genre-label"><c:out value="${game.genre}" /></span><h3><c:out value="${game.title}" /></h3></div><span class="price">¥<fmt:formatNumber value="${game.price}" pattern="#,##0" /></span></div></c:forEach></div><div class="summary-line summary-total"><span>合計金額</span><strong class="price">¥<fmt:formatNumber value="${order.totalPrice}" pattern="#,##0" /></strong></div></section></c:if>
<div class="order-actions"><a class="button" href="${pageContext.request.contextPath}/orders">注文履歴を見る</a><a class="button button-secondary" href="${pageContext.request.contextPath}/browse">ショッピングを続ける ↗</a></div>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body>
</html>
