<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <%@ include file="/WEB-INF/jspf/head.jspf" %>
    <title>ゲーム一覧 - PLAYPORT</title>
</head>
<body>
<c:set var="navSection" value="browse" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main class="browse-main">
    <nav class="breadcrumb" aria-label="パンくず">
        <a href="${pageContext.request.contextPath}/home">ホーム</a><span>/</span><span>ゲーム一覧</span>
    </nav>
    <div class="page-heading browse-heading">
        <div>
            <span class="eyebrow">THE LITTLE GAME SHELF</span>
            <h1>次のお気に入りを、ここから。</h1>
            <p>気になる名前、好きなジャンル、ちょうどいい価格。あなたのペースで探そう。</p>
        </div>
        <span class="catalog-total">${catalogCount} タイトル</span>
    </div>

    <section class="panel browse-controls" aria-label="ゲームの検索と絞り込み">
        <form class="browse-form" action="${pageContext.request.contextPath}/browse" method="get" role="search">
            <div class="browse-search-field">
                <label for="catalog-search">ゲーム名・ジャンルで検索</label>
                <input id="catalog-search" type="search" name="q" placeholder="気になるゲームを検索…"
                       value="<c:out value='${query}' />" maxlength="200">
            </div>
            <div class="browse-sort-field">
                <label for="catalog-sort">並び順</label>
                <select id="catalog-sort" name="sort">
                    <option value="added" <c:if test="${sort == 'added'}">selected</c:if>>追加順（新しい順）</option>
                    <option value="price_asc" <c:if test="${sort == 'price_asc'}">selected</c:if>>価格の安い順</option>
                    <option value="price_desc" <c:if test="${sort == 'price_desc'}">selected</c:if>>価格の高い順</option>
                    <option value="title" <c:if test="${sort == 'title'}">selected</c:if>>タイトル順</option>
                </select>
            </div>
            <c:if test="${not empty selectedGenre}">
                <input type="hidden" name="genre" value="<c:out value='${selectedGenre}' />">
            </c:if>
            <button class="button" type="submit">検索・適用</button>
        </form>
        <div class="browse-genres" id="genres">
            <span class="browse-filter-label">ジャンル</span>
            <div class="filter-chips" aria-label="ジャンルで絞り込む">
                <c:url var="allUrl" value="/browse">
                    <c:if test="${not empty query}"><c:param name="q" value="${query}" /></c:if>
                    <c:param name="sort" value="${sort}" />
                </c:url>
                <a class="filter-chip ${empty selectedGenre ? 'is-selected' : ''}" href="${allUrl}">すべて</a>
                <c:forEach var="genre" items="${genres}">
                    <c:url var="genreUrl" value="/browse">
                        <c:param name="genre" value="${genre}" />
                        <c:if test="${not empty query}"><c:param name="q" value="${query}" /></c:if>
                        <c:param name="sort" value="${sort}" />
                    </c:url>
                    <a class="filter-chip ${selectedGenre == genre ? 'is-selected' : ''}" href="${genreUrl}"><c:out value="${genre}" /></a>
                </c:forEach>
            </div>
        </div>
    </section>

    <section id="catalog" aria-labelledby="catalog-title">
        <div class="browse-results-heading">
            <h2 id="catalog-title">${filtering ? '検索結果' : 'すべてのゲーム'} <span class="genre-label">${resultCount} 件</span></h2>
            <c:if test="${filtering}"><a class="section-link" href="${pageContext.request.contextPath}/browse">条件をクリア ↗</a></c:if>
        </div>
        <c:if test="${filtering}">
            <p class="result-note"><c:if test="${not empty query}">「<c:out value="${query}" />」</c:if>
                <c:if test="${not empty selectedGenre}"> · <c:out value="${selectedGenre}" /></c:if></p>
        </c:if>
        <c:choose>
            <c:when test="${not empty games}">
                <div class="game-grid">
                    <c:forEach var="game" items="${games}">
                        <%@ include file="/WEB-INF/jspf/game-card.jspf" %>
                    </c:forEach>
                </div>
                <%@ include file="/WEB-INF/jspf/catalog-pagination.jspf" %>
            </c:when>
            <c:otherwise>
                <div class="empty-state">
                    <span class="empty-symbol" aria-hidden="true">✳</span>
                    <h2>${catalogCount == 0 ? '新しい冒険の準備中です。' : 'ゲームが見つかりませんでした'}</h2>
                    <p>${catalogCount == 0 ? 'ゲームが登録されるまで、少しお待ちください。' : '別のキーワードやジャンルで探してみてください。'}</p>
                    <a class="button button-secondary" href="${pageContext.request.contextPath}/browse">条件をクリア</a>
                </div>
            </c:otherwise>
        </c:choose>
    </section>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body>
</html>
