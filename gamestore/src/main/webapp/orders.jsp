<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <%@ include file="/WEB-INF/jspf/head.jspf" %>
    <title>注文履歴 - PLAYPORT</title>
</head>
<body>
<c:set var="navSection" value="orders" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main>
<div class="page-heading"><span class="eyebrow">STORIES YOU HAVE CHOSEN</span><h1>注文履歴</h1><p>これまでに選んだ、冒険の記録。</p></div>
<%@ include file="/WEB-INF/jspf/collection-nav.jspf" %>
<div class="order-filter"><form class="search-form" method="get" action="${pageContext.request.contextPath}/orders"><label class="sr-only" for="order-search">注文番号で検索</label><input id="order-search" type="search" name="orderId" placeholder="注文番号で検索…" value="<c:out value='${param.orderId}' />"><button type="submit">検索</button></form><a class="section-link" href="${pageContext.request.contextPath}/orders">クリア</a></div>
<c:choose><c:when test="${empty orders}"><div class="empty-state"><span class="empty-symbol" aria-hidden="true">✳</span><h2>${empty param.orderId ? '最初の冒険は、これから。' : '注文が見つかりませんでした'}</h2><p>${empty param.orderId ? 'ゲームを選んで注文すると、ここに記録が残ります。' : '注文番号を確認するか、検索条件をクリアしてください。'}</p><a class="button button-secondary" href="${pageContext.request.contextPath}/browse">ストアへ戻る ↗</a></div></c:when><c:otherwise>
<section class="panel"><table class="order-table"><caption class="sr-only">ご自身の注文履歴</caption><thead><tr><th>注文番号 / 日時</th><th>状態</th><th>合計金額</th></tr></thead><tbody><c:forEach var="order" items="${orders}"><tr><td><a class="order-code" href="${pageContext.request.contextPath}/order/complete?id=${order.orderId}"><c:out value="${order.orderId}" /> ↗</a><span class="order-date"><c:out value="${order.formattedCreatedAt}" /></span></td><td><span class="order-status is-${order.status.cssClass}"><c:out value="${order.status.label}" /></span></td><td><strong class="price">¥<fmt:formatNumber value="${order.totalPrice}" pattern="#,##0" /></strong></td></tr></c:forEach></tbody></table></section>
</c:otherwise></c:choose>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body>
</html>
