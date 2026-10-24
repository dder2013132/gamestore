<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <%@ include file="/WEB-INF/jspf/head.jspf" %>
    <title>アカウント作成 - PLAYPORT</title>
</head>
<body>
<c:set var="navSection" value="account" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main class="auth-shell">
<aside class="auth-story" aria-label="新しい冒険を見つけよう"><img src="${pageContext.request.contextPath}/images/moonlit_harvest.png" alt=""><div class="auth-story-copy"><h2>ふと出会った一本が、<br>毎日を少し、特別に。</h2><p>Find a little world to call your own.</p></div></aside>
<section class="auth-card" aria-labelledby="register-title"><span class="eyebrow">YOUR NEXT CHAPTER STARTS HERE</span><h1 id="register-title">ようこそ、playportへ。</h1><p>アカウントをつくって、お気に入りの世界へ。</p>
<c:if test="${not empty error}"><p class="auth-error" role="alert"><c:out value="${error}" /></p></c:if>
<form class="auth-form" action="${pageContext.request.contextPath}/register" method="post"><input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
<label for="username">ユーザー名</label><input id="username" name="username" type="text" autocomplete="username" pattern="[A-Za-z0-9_]{4,30}" minlength="4" maxlength="30" required autofocus aria-describedby="username-help" value="<c:out value='${username}' />"><small id="username-help">半角英数字と _ で4〜30文字。大文字と小文字は区別しません。</small>
<label for="password">パスワード</label><input id="password" name="password" type="password" autocomplete="new-password" minlength="8" maxlength="128" required aria-describedby="password-help"><small id="password-help">8〜128文字で入力してください。</small>
<label for="passwordConfirm">パスワード（確認）</label><input id="passwordConfirm" name="passwordConfirm" type="password" autocomplete="new-password" minlength="8" maxlength="128" required>
<button class="auth-submit" type="submit">アカウントをつくる →</button></form>
<p class="auth-switch">登録済みですか？ <a href="${pageContext.request.contextPath}/login">ログイン</a></p></section>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body>
</html>
