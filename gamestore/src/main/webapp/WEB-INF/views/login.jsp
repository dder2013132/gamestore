<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <%@ include file="/WEB-INF/jspf/head.jspf" %>
    <title>ログイン - PLAYPORT</title>
</head>
<body>
<c:set var="navSection" value="account" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main class="auth-shell">
<aside class="auth-story" aria-label="新しい冒険を見つけよう"><img src="${pageContext.request.contextPath}/images/moonlit_harvest.png" alt=""><div class="auth-story-copy"><h2>ふと出会った一本が、<br>毎日を少し、特別に。</h2><p>Find a little world to call your own.</p></div></aside>
<section class="auth-card" aria-labelledby="login-title"><span class="eyebrow">WELCOME BACK TO YOUR LITTLE CORNER</span><h1 id="login-title">おかえりなさい。</h1><p>ログインして、次の冒険の続きを。</p>
<c:if test="${param.registered == '1'}"><p class="auth-success" role="status">登録が完了しました。ログインしてください。</p></c:if>
<c:if test="${sessionScope.loginDestination == '/account' or sessionScope.loginDestination == '/account/password'}"><p class="auth-success" role="status">マイページのご利用にはログインが必要です。</p></c:if>
<c:if test="${sessionScope.loginDestination == '/cart'}"><p class="auth-success" role="status">カートのご利用にはログインが必要です。</p></c:if>
<c:if test="${sessionScope.loginDestination == '/wishlist'}"><p class="auth-success" role="status">お気に入りのご利用にはログインが必要です。</p></c:if>
<c:if test="${sessionScope.loginDestination == '/library'}"><p class="auth-success" role="status">自分のライブラリを見るにはログインしてください。</p></c:if>
<c:if test="${sessionScope.loginDestination == '/admin/dashboard'}"><p class="auth-success" role="status">ダッシュボードを見るには管理者アカウントでログインしてください。</p></c:if>
<c:if test="${not empty sessionScope.loginDestination and fn:startsWith(sessionScope.loginDestination, '/games?id=')}"><p class="auth-success" role="status">ログイン後、商品ページに戻ります。カートやお気に入りへの追加は、商品ページでもう一度行えます。</p></c:if>
<c:if test="${not empty error}"><p class="auth-error" role="alert"><c:out value="${error}" /></p></c:if>
<form class="auth-form" action="${pageContext.request.contextPath}/login" method="post"><input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
<label for="username">ユーザー名</label><input id="username" name="username" type="text" autocomplete="username" minlength="4" maxlength="30" required autofocus value="<c:out value='${username}' />">
<label for="password">パスワード</label><input id="password" name="password" type="password" autocomplete="current-password" minlength="8" maxlength="128" required>
<button class="auth-submit" type="submit">ログイン →</button></form>
<p class="auth-switch">初めてご利用ですか？ <a href="${pageContext.request.contextPath}/register">アカウントをつくる</a></p></section>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body>
</html>
