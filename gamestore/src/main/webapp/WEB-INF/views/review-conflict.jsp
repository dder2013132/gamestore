<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html><html lang="ja"><head><%@ include file="/WEB-INF/jspf/head.jspf" %><title>レビューの確認 - PLAYPORT</title></head><body>
<c:set var="navSection" value="account" /><%@ include file="/WEB-INF/jspf/header.jspf" %>
<main class="review-edit-page"><div class="page-heading"><span class="eyebrow">YOUR REVIEW</span><h1>レビューを確認してください</h1></div>
<p class="auth-error" role="alert"><c:out value="${reviewError}" /></p><div class="order-actions"><a class="button button-secondary" href="${pageContext.request.contextPath}/account/reviews">自分のレビュー一覧へ</a><c:if test="${not empty game}"><a class="button button-secondary" href="${pageContext.request.contextPath}/reviews/edit?gameId=${game.id}">最新の内容を読み直す</a></c:if></div>
</main><%@ include file="/WEB-INF/jspf/footer.jspf" %></body></html>
