<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <%@ include file="/WEB-INF/jspf/head.jspf" %>
    <title>注文詳細 - PLAYPORT</title>
</head>
<body>
<c:set var="navSection" value="orders" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main class="order-success">
<c:choose><c:when test="${not empty pageError or empty order}">
    <div class="page-heading"><span class="eyebrow">YOUR ORDER</span><h1>注文情報の確認</h1></div>
    <p class="auth-error" role="alert"><c:out value="${empty pageError ? '注文履歴から注文を選んでください。' : pageError}" /></p>
</c:when><c:otherwise>
    <div class="success-intro order-state-intro"><span class="success-stamp is-${order.status.cssClass}" aria-hidden="true">${order.pending ? '…' : (order.cancelled ? '×' : '✓')}</span>
        <span class="eyebrow">YOUR NEXT LITTLE ADVENTURE</span>
        <c:choose><c:when test="${order.pending}"><h1>注文を受け付けました。</h1><p>${repurchaseBlocked ? '購入済みのゲームが含まれています。ライブラリを確認して、この注文をキャンセルしてください。' : '支払い待ちです。テスト決済を完了するか、注文をキャンセルできます。'}</p></c:when>
            <c:when test="${order.cancelled}"><h1>注文をキャンセルしました。</h1><p>注文の記録は履歴に残ります。カートには自動で戻りません。</p></c:when>
            <c:otherwise><h1>ご注文、ありがとうございます。</h1><p>この注文は完了しています。選んだゲームの内容はこちらです。</p></c:otherwise></c:choose>
    </div>
    <p class="demo-payment-notice"><strong>テスト決済</strong> 学習用の注文フローです。カード情報の入力・実際の請求はありません。</p>
    <c:if test="${not empty actionError}"><p class="auth-error" role="alert"><c:out value="${actionError}" /></p></c:if>
    <section class="panel"><div class="order-meta"><small>注文番号</small><span class="order-code"><c:out value="${order.orderId}" /></span></div>
        <dl class="member-order-facts"><div><dt>状態</dt><dd><span class="order-status is-${order.status.cssClass}"><c:out value="${order.status.label}" /></span></dd></div>
            <div><dt>注文日時</dt><dd><c:out value="${order.formattedCreatedAt}" /></dd></div>
            <c:if test="${order.completed}"><div><dt>完了日時</dt><dd><c:out value="${order.formattedCompletedAt}" /></dd></div></c:if>
            <c:if test="${order.cancelled}"><div><dt>キャンセル日時</dt><dd><c:out value="${order.formattedCancelledAt}" /></dd></div></c:if>
        </dl>
        <div class="line-items"><c:forEach var="game" items="${order.games}"><div class="line-item"><img src="${pageContext.request.contextPath}/<c:out value='${game.imagePath}' />" alt=""><div class="line-item-info"><span class="genre-label"><c:out value="${game.genre}" /></span><h3><a href="${pageContext.request.contextPath}/games?id=${game.id}"><c:out value="${game.title}" /></a></h3><c:if test="${order.completed}"><a class="line-item-review-link" href="${pageContext.request.contextPath}/games?id=${game.id}#reviews">ゲーム情報・レビューへ →</a></c:if></div><span class="price">¥<fmt:formatNumber value="${game.price}" pattern="#,##0" /></span></div></c:forEach></div>
        <div class="summary-line summary-total"><span>注文金額</span><strong class="price">¥<fmt:formatNumber value="${order.totalPrice}" pattern="#,##0" /></strong></div>
        <c:if test="${order.pending}"><div class="demo-payment-actions">
            <c:if test="${not repurchaseBlocked}"><p>注文時の金額でテスト決済を行います。実際のお支払いは発生しません。</p></c:if>
            <c:choose><c:when test="${repurchaseBlocked}"><p class="auth-error" role="alert">すでにライブラリにあるゲームが含まれているため、この注文は決済できません。注文をキャンセルして、未購入のゲームだけで注文し直してください。</p><a class="button button-block" href="${pageContext.request.contextPath}/library">ライブラリで見る →</a></c:when><c:otherwise><form action="${pageContext.request.contextPath}/order/demo-pay" method="post" data-order-action-form><input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><input type="hidden" name="id" value="<c:out value='${order.orderId}' />"><button class="button button-block" type="submit">テスト決済を完了する（請求なし）</button></form></c:otherwise></c:choose>
            <details class="order-cancel-confirm"><summary>この注文をキャンセルする</summary><p>キャンセル後は、この注文を完了できません。注文内容は履歴に残ります。</p>
                <form action="${pageContext.request.contextPath}/order/cancel" method="post" data-order-action-form><input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><input type="hidden" name="id" value="<c:out value='${order.orderId}' />"><button class="button button-secondary" type="submit">キャンセルを確定する</button></form>
            </details>
        </div></c:if>
    </section>
</c:otherwise></c:choose>
<div class="order-actions"><c:if test="${order.completed}"><a class="button" href="${pageContext.request.contextPath}/library">ライブラリを見る</a></c:if><a class="button button-secondary" href="${pageContext.request.contextPath}/orders">注文履歴を見る</a><a class="button button-secondary" href="${pageContext.request.contextPath}/browse">ショッピングを続ける ↗</a></div>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body>
</html>
