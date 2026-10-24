<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <%@ include file="/WEB-INF/jspf/head.jspf" %>
    <title>ゲームの説明を編集 - PLAYPORT</title>
</head>
<body>
<c:set var="navSection" value="admin" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main class="description-editor">
    <nav class="breadcrumb" aria-label="パンくず"><a href="${pageContext.request.contextPath}/admin/games">ゲーム管理</a><span>/</span><span>説明を編集</span></nav>
    <div class="page-heading"><span class="eyebrow">TELL A LITTLE STORY</span><h1>この冒険を、言葉で届けよう。</h1><p>保存すると、ゲームの詳細ページに反映されます。</p></div>
    <c:if test="${param.saved == '1' and empty error}"><p class="auth-success" role="status">ゲームの説明を保存しました。</p></c:if>
    <c:if test="${not empty error}"><p class="auth-error" role="alert"><c:out value="${error}" /></p></c:if>
    <section class="panel" aria-labelledby="editing-title">
        <div class="line-item">
            <img src="${pageContext.request.contextPath}/<c:out value='${game.imagePath}' />" alt="">
            <div class="line-item-info"><span class="genre-label"><c:out value="${game.genre}" /></span><h2 id="editing-title"><c:out value="${game.title}" /></h2></div>
        </div>
        <form class="auth-form" method="post" action="${pageContext.request.contextPath}/admin/games/description">
            <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
            <input type="hidden" name="id" value="${game.id}">
            <label for="description">ゲームの説明（任意）</label>
            <textarea id="description" name="description" rows="12" maxlength="${descriptionLimit}" aria-describedby="description-help" placeholder="世界観や遊び方、ゲームの魅力を教えてください。"><c:out value="${formDescription}" /></textarea>
            <small id="description-help">${descriptionLimit}文字以内。改行はそのまま表示されます。空欄で保存すると説明を削除できます。HTMLは使わず、文章で入力してください。</small>
            <button class="auth-submit" type="submit">説明を保存</button>
        </form>
        <div class="order-actions"><a class="section-link" href="${pageContext.request.contextPath}/games?id=${game.id}">詳細ページを確認 ↗</a><a class="section-link" href="${pageContext.request.contextPath}/admin/games">← ゲーム管理に戻る</a></div>
    </section>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body>
</html>
