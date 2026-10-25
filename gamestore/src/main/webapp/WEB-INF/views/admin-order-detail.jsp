<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head><%@ include file="/WEB-INF/jspf/head.jspf" %><title>注文詳細・管理 - PLAYPORT</title></head>
<body>
<c:set var="navSection" value="adminOrders" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main class="admin-order-detail">
<div class="page-heading admin-heading"><div><span class="eyebrow">A CLOSER LOOK</span><h1>注文詳細</h1><p>購入されたゲームと、注文の記録。</p></div><span class="admin-label">ADMIN ONLY</span></div>
<%@ include file="/WEB-INF/jspf/admin-nav.jspf" %>
<%@ include file="/WEB-INF/jspf/admin-order-query.jspf" %>
<c:choose><c:when test="${not empty error}"><p class="auth-error" role="alert"><c:out value="${error}" /></p></c:when><c:otherwise>
<section class="panel">
    <div class="order-meta"><small>注文番号</small><span class="order-code"><c:out value="${adminOrder.order.orderId}" /></span></div>
    <dl class="admin-order-facts"><div><dt>注文者</dt><dd><c:out value="${adminOrder.username}" /><small>会員 ID ${adminOrder.order.userId}</small></dd></div><div><dt>注文日時</dt><dd><c:out value="${adminOrder.order.formattedCreatedAt}" /></dd></div><div><dt>ゲーム数</dt><dd>${adminOrder.itemCount} 本</dd></div></dl>
    <h2>注文したゲーム</h2><p class="admin-catalog-help">金額は購入時の価格です。タイトル・カバーは現在の商品情報を表示します。</p>
    <div class="admin-order-state"><span class="order-status is-${adminOrder.order.status.cssClass}"><c:out value="${adminOrder.order.status.label}" /></span><c:if test="${adminOrder.order.completed}"><span>完了日時：<c:out value="${adminOrder.order.formattedCompletedAt}" /></span></c:if><c:if test="${adminOrder.order.cancelled}"><span>キャンセル日時：<c:out value="${adminOrder.order.formattedCancelledAt}" /></span></c:if></div>
    <div class="line-items"><c:forEach var="game" items="${adminOrder.order.games}"><div class="line-item"><img width="70" height="70" src="${pageContext.request.contextPath}/<c:out value='${game.imagePath}' />" alt=""><div class="line-item-info"><span class="genre-label"><c:out value="${game.genre}" /></span><h3><c:out value="${game.title}" /></h3></div><span class="price">¥<fmt:formatNumber value="${game.price}" pattern="#,##0" /></span></div></c:forEach></div>
    <div class="summary-line summary-total"><span>合計金額</span><strong class="price">¥<fmt:formatNumber value="${adminOrder.order.totalPrice}" pattern="#,##0" /></strong></div>
</section>
</c:otherwise></c:choose>
<div class="order-actions"><a class="button button-secondary" href="<c:out value='${adminOrdersBase}' />">← 注文一覧へ戻る</a></div>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body></html>
