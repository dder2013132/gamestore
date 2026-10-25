<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html><html lang="ja"><head><%@ include file="/WEB-INF/jspf/head.jspf" %><title>ライブラリの確認 - PLAYPORT</title></head><body>
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main><div class="page-heading"><span class="eyebrow">YOUR GAMES, SAFELY TOGETHER</span><h1>少し時間をおいて、もう一度。</h1><p role="alert">ライブラリの情報を確認できませんでした。重複購入を防ぐため、操作を進められませんでした。</p></div><div class="order-actions"><a class="button" href="${pageContext.request.contextPath}/library">ライブラリを確認する</a><a class="button button-secondary" href="${pageContext.request.contextPath}/orders">注文履歴を確認する</a><a class="button button-secondary" href="${pageContext.request.contextPath}/browse">ゲーム一覧へ</a></div></main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %></body></html>
