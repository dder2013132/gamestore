<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <%@ include file="/WEB-INF/jspf/head.jspf" %>
    <title>ゲーム管理 - PLAYPORT</title>
</head>
<body>
<c:set var="navSection" value="admin" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main>
<div class="page-heading admin-heading"><div><span class="eyebrow">BEHIND THE LITTLE GAME SHELF</span><h1>ゲーム管理</h1><p>新しい一本を、ストアの棚に並べよう。</p></div><a class="button button-secondary button-small" href="${pageContext.request.contextPath}/home">ストアを見る ↗</a></div>
<c:if test="${param.created == '1'}"><p class="auth-success" role="status">ゲームを登録しました。ゲーム一覧に表示されます。</p></c:if><c:if test="${not empty error}"><p class="auth-error" role="alert"><c:out value="${error}" /></p></c:if>
<c:if test="${param.updated == '1'}"><p class="auth-success" role="status">ゲーム情報を保存しました。</p></c:if><c:if test="${param.statusUpdated == '1'}"><p class="auth-success" role="status">販売状況を更新しました。</p></c:if>
<div class="admin-layout"><section class="panel admin-registration" aria-labelledby="registration-title"><span class="eyebrow">ADD A NEW ADVENTURE</span><h2 id="registration-title">新しいゲームを登録</h2>
<form class="auth-form" method="post" action="${pageContext.request.contextPath}/admin/games"><input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
<%@ include file="/WEB-INF/jspf/admin-game-fields.jspf" %>
<button class="auth-submit" type="submit">ゲームを登録 ＋</button></form></section>
<section class="panel admin-catalog">
    <span class="eyebrow">ON THE SHELF</span><h2>登録済みゲーム <span class="genre-label">${fn:length(games)} タイトル</span></h2>
    <p class="admin-catalog-help">販売休止中のゲームはストアの一覧に表示されません。注文履歴は残ります。</p>
    <div class="admin-table-wrap"><table class="admin-table">
        <caption class="sr-only">登録済みゲーム一覧</caption>
        <thead><tr><th>ID</th><th>カバー</th><th>タイトル</th><th>価格</th><th>販売状況</th><th>操作</th></tr></thead>
        <tbody><c:forEach var="game" items="${games}"><tr>
            <td>${game.id}</td><td><img width="56" height="56" src="${pageContext.request.contextPath}/<c:out value='${game.imagePath}' />" alt=""></td>
            <td><a href="${pageContext.request.contextPath}/games?id=${game.id}"><c:out value="${game.title}" /></a><br><span class="genre-label"><c:out value="${game.genre}" /></span></td>
            <td>¥<fmt:formatNumber value="${game.price}" pattern="#,##0" /></td>
            <td><span class="sale-status ${game.active ? '' : 'is-paused'}">${game.active ? '販売中' : '販売休止中'}</span></td>
            <td><div class="admin-game-actions">
                <a class="section-link" href="${pageContext.request.contextPath}/admin/games/edit?id=${game.id}">情報を編集 ↗</a>
                <a class="section-link" href="${pageContext.request.contextPath}/admin/games/description?id=${game.id}">説明を編集 ↗</a>
                <form method="post" action="${pageContext.request.contextPath}/admin/games/status">
                    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><input type="hidden" name="id" value="${game.id}">
                    <input type="hidden" name="active" value="${not game.active}">
                    <button class="button button-secondary button-small" type="submit">${game.active ? '販売を休止' : '販売を再開'}</button>
                </form>
            </div></td>
        </tr></c:forEach></tbody>
    </table></div>
</section></div>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body>
</html>
