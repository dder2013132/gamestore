<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html><html lang="ja"><head><%@ include file="/WEB-INF/jspf/head.jspf" %><title>レビューを書く - PLAYPORT</title></head><body>
<c:set var="navSection" value="account" /><%@ include file="/WEB-INF/jspf/header.jspf" %>
<main class="review-edit-page"><div class="page-heading"><span class="eyebrow">YOUR LITTLE WORDS MATTER</span><h1>${reviewMode == 'update' ? 'レビューを編集' : 'レビューを書く'}</h1><p>このゲームで出会った「好き」を、あなたの言葉で。</p></div>
<c:if test="${not empty reviewError}"><p class="auth-error" role="alert"><c:out value="${reviewError}" /></p><a class="section-link review-reload" href="${pageContext.request.contextPath}/reviews/edit?gameId=${game.id}">最新のレビューを読み直す →</a></c:if>
<c:if test="${not empty game}"><div class="panel review-game"><img width="72" height="72" src="${pageContext.request.contextPath}/<c:out value='${game.imagePath}' />" alt=""><div><span class="genre-label"><c:out value="${game.genre}" /></span><h2><c:out value="${game.title}" /></h2><span class="review-help">ゲームごとに1件、投稿できます。</span></div></div></c:if>
<c:if test="${canReview}"><section class="panel" aria-label="レビューフォーム"><form class="auth-form" method="post" action="${pageContext.request.contextPath}/reviews/${reviewMode}" data-review-form>
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><input type="hidden" name="gameId" value="${game.id}">
    <c:if test="${reviewMode == 'update'}"><input type="hidden" name="reviewId" value="<c:out value='${formReviewId}' />"><input type="hidden" name="version" value="<c:out value='${formVersion}' />"></c:if>
    <label for="review-rating">評価</label><select id="review-rating" name="rating" required><option value="">星の数を選んでください</option><c:forEach var="stars" begin="1" end="5"><option value="${stars}" ${formRating == stars ? 'selected' : ''}>${stars} / 5</option></c:forEach></select>
    <label for="review-body">レビュー</label><textarea id="review-body" name="body" rows="8" maxlength="2000" required aria-describedby="review-body-help"><c:out value="${formBody}" /></textarea><small id="review-body-help">2,000文字以内。よかったところや、気になったところを自由に書いてください。</small>
    <button class="auth-submit" type="submit">${reviewMode == 'update' ? 'レビューを保存する' : 'レビューを投稿する'}</button>
</form></section></c:if>
<div class="order-actions"><a class="button button-secondary" href="${pageContext.request.contextPath}/games?id=${game.id}#reviews">ゲームのレビューへ戻る</a><a class="button button-secondary" href="${pageContext.request.contextPath}/account/reviews">自分のレビュー一覧</a></div>
</main><%@ include file="/WEB-INF/jspf/footer.jspf" %></body></html>
