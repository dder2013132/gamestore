<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <%@ include file="/WEB-INF/jspf/head.jspf" %>
    <title>注文内容の確認 - PLAYPORT</title>
</head>
<body>
<c:set var="navSection" value="cart" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main>
<ol class="checkout-steps" aria-label="購入手順"><li>01 カート</li><li class="is-active" aria-current="step">02 注文確認</li><li>03 完了</li></ol>
<div class="page-heading"><span class="eyebrow">ALMOST TIME TO PLAY</span><h1>注文内容の確認</h1><p>ゲームと金額を確認して、注文を確定してください。</p></div>
<c:if test="${not empty orderError}"><div class="auth-error" role="alert"><c:out value="${orderError}" /><div><a class="section-link" href="${pageContext.request.contextPath}/cart">カートを確認する</a> · <a class="section-link" href="${pageContext.request.contextPath}/orders">注文履歴を確認する</a></div></div></c:if>
<c:if test="${not empty cart}"><div class="two-column"><section class="panel"><h2>あなたが選んだゲーム</h2><div class="line-items"><c:forEach var="game" items="${cart}"><div class="line-item"><img src="${pageContext.request.contextPath}/<c:out value='${game.imagePath}' />" alt=""><div class="line-item-info"><span class="genre-label"><c:out value="${game.genre}" /></span><h3><c:out value="${game.title}" /></h3></div><span class="price">¥<fmt:formatNumber value="${game.price}" pattern="#,##0" /></span></div></c:forEach></div></section>
<aside class="panel summary-panel"><span class="eyebrow">ONE MORE LITTLE STEP</span><h2>注文の合計</h2><div class="summary-line"><span>ゲーム</span><span>${fn:length(cart)} 点</span></div><div class="summary-line summary-total"><span>合計金額</span><strong class="price">¥<fmt:formatNumber value="${total}" pattern="#,##0" /></strong></div><form action="${pageContext.request.contextPath}/order" method="post" data-checkout-form data-checkout-blocked="${orderBlocked}"><input type="hidden" name="checkoutToken" value="${checkoutToken}"><input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><button class="button button-block" type="submit" data-checkout-button <c:if test="${orderBlocked}">disabled</c:if>>${orderBlocked ? 'カートを確認してください' : '注文を確定する →'}</button></form><p class="summary-help"><a href="${pageContext.request.contextPath}/cart">← カートへ戻る</a></p></aside></div></c:if>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body>
</html>
