<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head><%@ include file="/WEB-INF/jspf/head.jspf" %><title>ゲーム情報を編集 - PLAYPORT</title></head>
<body>
<c:set var="navSection" value="admin" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main class="description-editor">
    <nav class="breadcrumb" aria-label="パンくず"><a href="${pageContext.request.contextPath}/admin/games">ゲーム管理</a><span>/</span><span>情報を編集</span></nav>
    <div class="page-heading"><span class="eyebrow">A LITTLE CARE FOR YOUR GAME</span><h1>ゲーム情報を編集</h1><p>タイトルや価格、カバーを更新できます。過去の注文の購入価格は変わりません。</p></div>
    <c:if test="${not empty error}"><p class="auth-error" role="alert"><c:out value="${error}" /></p></c:if>
    <section class="panel" aria-labelledby="editing-title">
        <h2 id="editing-title"><c:out value="${editingGame.title}" /></h2>
        <span class="sale-status ${editingGame.active ? '' : 'is-paused'}">${editingGame.active ? '販売中' : '販売休止中'}</span>
        <form class="auth-form" method="post" action="${pageContext.request.contextPath}/admin/games/edit">
            <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><input type="hidden" name="id" value="${editingGame.id}">
            <%@ include file="/WEB-INF/jspf/admin-game-fields.jspf" %>
            <button class="auth-submit" type="submit">変更を保存</button>
        </form>
        <div class="order-actions"><a class="section-link" href="${pageContext.request.contextPath}/games?id=${editingGame.id}">詳細ページを確認 ↗</a><a class="section-link" href="${pageContext.request.contextPath}/admin/games">← ゲーム管理に戻る</a></div>
    </section>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body></html>
