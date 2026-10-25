<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head><%@ include file="/WEB-INF/jspf/head.jspf" %><title>パスワード変更 - PLAYPORT</title></head>
<body>
<c:set var="navSection" value="account" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main class="account-password-page">
<div class="page-heading"><span class="eyebrow">KEEP YOUR LITTLE CORNER SAFE</span><h1>パスワード変更</h1><p>現在のパスワードを確認してから、新しいものに変更します。</p></div>
<c:if test="${param.changed == '1' and empty error}"><p class="auth-success" role="status">パスワードを変更しました。次回から新しいパスワードでログインできます。</p></c:if>
<c:if test="${not empty error}"><p class="auth-error" role="alert"><c:out value="${error}" /></p></c:if>
<div class="account-password-layout">
    <aside class="panel account-password-note"><span class="account-note-symbol" aria-hidden="true">✧</span><h2>大切な冒険のために。</h2><p>ほかのサービスと使い回さず、長めのパスワードを選びましょう。</p><p>変更後もこの端末ではログインを継続します。カートの内容もそのままです。</p><span class="genre-label">8〜128文字・現在とは異なるもの</span></aside>
    <section class="panel" aria-label="パスワード変更フォーム"><form class="auth-form" method="post" action="${pageContext.request.contextPath}/account/password">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
        <label for="current-password">現在のパスワード</label><input id="current-password" name="currentPassword" type="password" autocomplete="current-password" minlength="8" maxlength="128" required>
        <label for="new-password">新しいパスワード</label><input id="new-password" name="newPassword" type="password" autocomplete="new-password" minlength="8" maxlength="128" aria-describedby="new-password-help" required><small id="new-password-help">8〜128文字で入力してください。前後の空白も文字として扱います。</small>
        <label for="password-confirm">新しいパスワード（確認）</label><input id="password-confirm" name="passwordConfirm" type="password" autocomplete="new-password" minlength="8" maxlength="128" required>
        <button class="auth-submit" type="submit">パスワードを変更する</button>
    </form></section>
</div>
<div class="order-actions"><a class="button button-secondary" href="${pageContext.request.contextPath}/account">← マイページへ戻る</a></div>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body></html>
