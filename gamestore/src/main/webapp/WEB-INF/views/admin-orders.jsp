<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ja">
<head><%@ include file="/WEB-INF/jspf/head.jspf" %><title>注文管理 - PLAYPORT</title></head>
<body>
<c:set var="navSection" value="adminOrders" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<main>
<div class="page-heading admin-heading"><div><span class="eyebrow">EVERY LITTLE ADVENTURE</span><h1>注文管理</h1><p>ストアに届いた注文を、ひとつずつ。</p></div><span class="admin-label">ADMIN ONLY</span></div>
<%@ include file="/WEB-INF/jspf/admin-nav.jspf" %>
<section class="panel admin-order-filter" aria-label="注文の検索">
    <form class="admin-order-search" method="get" action="${pageContext.request.contextPath}/admin/orders">
        <div><label for="admin-order-q">注文者・注文番号</label><input id="admin-order-q" type="search" name="q" maxlength="100" placeholder="ユーザー名、または注文番号" value="<c:out value='${query}' />"></div>
        <div><label for="admin-order-from">開始日</label><input id="admin-order-from" type="date" name="from" min="0001-01-01" max="9999-12-31" value="<c:out value='${fromDate}' />"></div>
        <div><label for="admin-order-to">終了日</label><input id="admin-order-to" type="date" name="to" min="0001-01-01" max="9999-12-31" value="<c:out value='${toDate}' />"></div>
        <div><label for="admin-order-status">注文状態</label><select id="admin-order-status" name="status"><option value="">すべての状態</option><c:forEach var="statusOption" items="${orderStatuses}"><option value="${statusOption.code}" ${selectedStatus == statusOption.code ? 'selected' : ''}><c:out value="${statusOption.label}" /></option></c:forEach></select></div>
        <button class="button" type="submit">検索する</button>
        <a class="section-link" href="${pageContext.request.contextPath}/admin/orders">クリア</a>
    </form>
    <p class="admin-catalog-help">注文日時の新しい順に表示します。開始日・終了日の当日を含みます。</p>
</section>
<c:choose><c:when test="${not empty error}"><p class="auth-error" role="alert"><c:out value="${error}" /></p></c:when><c:otherwise>
    <div class="admin-order-stats" aria-label="検索結果の集計">
        <div class="panel"><span>該当する注文</span><strong><fmt:formatNumber value="${resultCount}" pattern="#,##0" /><small> 件</small></strong></div>
        <div class="panel"><span>キャンセルを除く注文金額</span><strong>¥<fmt:formatNumber value="${totalPrice}" pattern="#,##0" /></strong><small>全ページの支払い待ち・完了注文の合計。実際の売上ではありません。</small></div>
    </div>
    <section id="admin-order-results" aria-labelledby="admin-order-list-title">
    <div class="section-heading"><div><span class="eyebrow">ORDER BOOK</span><h2 id="admin-order-list-title">注文一覧</h2></div><span class="genre-label">${resultCount} 件</span></div>
    <c:choose><c:when test="${empty orders}"><div class="empty-state"><span class="empty-symbol" aria-hidden="true">✳</span><h2>注文が見つかりませんでした</h2><p>検索条件を変えるか、新しい注文をお待ちください。</p><a class="button button-secondary" href="${pageContext.request.contextPath}/admin/orders">条件をクリア</a></div></c:when><c:otherwise>
    <div class="panel admin-orders-panel"><table class="order-table admin-orders-table">
        <caption class="sr-only">全会員の注文一覧</caption>
        <thead><tr><th>注文番号 / 日時</th><th>注文者</th><th>ゲーム数</th><th>合計金額 / 詳細</th></tr></thead>
        <tbody><c:forEach var="entry" items="${orders}">
            <c:url var="detailUrl" value="/admin/orders/detail"><c:param name="id" value="${entry.order.orderId}" /><c:param name="q" value="${query}" /><c:param name="from" value="${fromDate}" /><c:param name="to" value="${toDate}" /><c:param name="status" value="${selectedStatus}" /><c:param name="page" value="${pageNumber}" /></c:url>
            <tr><td data-label="注文番号 / 日時"><a class="order-code" href="<c:out value='${detailUrl}' />"><c:out value="${entry.order.orderId}" /></a><span class="order-date"><c:out value="${entry.order.formattedCreatedAt}" /></span></td>
                <td data-label="注文者"><span class="admin-order-member"><c:out value="${entry.username}" /></span><span class="order-date">会員 ID ${entry.order.userId}</span></td>
                <td data-label="ゲーム数 / 状態">${entry.itemCount} 本<span class="admin-order-status"><span class="order-status is-${entry.order.status.cssClass}"><c:out value="${entry.order.status.label}" /></span></span></td>
                <td data-label="合計金額 / 詳細"><strong class="price">¥<fmt:formatNumber value="${entry.order.totalPrice}" pattern="#,##0" /></strong><a class="admin-order-detail-link" href="<c:out value='${detailUrl}' />" aria-label="${fn:escapeXml(entry.order.orderId)} の注文詳細">詳細を見る ↗</a></td></tr>
        </c:forEach></tbody>
    </table></div>
    <p class="catalog-range">${resultCount} 件中 ${firstResult}–${lastResult} 件を表示</p>
    <c:if test="${pageCount > 1}">
        <c:url var="orderPageBase" value="/admin/orders"><c:param name="q" value="${query}" /><c:param name="from" value="${fromDate}" /><c:param name="to" value="${toDate}" /><c:param name="status" value="${selectedStatus}" /></c:url>
        <nav class="pagination" aria-label="注文一覧のページ">
            <c:choose><c:when test="${pageNumber > 1}"><a rel="prev" href="<c:out value='${orderPageBase}' />&amp;page=${pageNumber - 1}#admin-order-results">← 前へ</a></c:when><c:otherwise><span class="page-step is-disabled" aria-disabled="true">← 前へ</span></c:otherwise></c:choose>
            <div class="page-numbers">
                <c:if test="${pageStart > 1}"><a href="<c:out value='${orderPageBase}' />&amp;page=1#admin-order-results" aria-label="1ページ目">1</a><c:if test="${pageStart > 2}"><span class="page-ellipsis">…</span></c:if></c:if>
                <c:forEach var="number" begin="${pageStart}" end="${pageEnd}"><c:choose><c:when test="${number == pageNumber}"><span class="is-current" aria-current="page" aria-label="${number}ページ目">${number}</span></c:when><c:otherwise><a href="<c:out value='${orderPageBase}' />&amp;page=${number}#admin-order-results" aria-label="${number}ページ目">${number}</a></c:otherwise></c:choose></c:forEach>
                <c:if test="${pageEnd < pageCount}"><c:if test="${pageEnd < pageCount - 1}"><span class="page-ellipsis">…</span></c:if><a href="<c:out value='${orderPageBase}' />&amp;page=${pageCount}#admin-order-results" aria-label="${pageCount}ページ目">${pageCount}</a></c:if>
            </div>
            <c:choose><c:when test="${pageNumber < pageCount}"><a rel="next" href="<c:out value='${orderPageBase}' />&amp;page=${pageNumber + 1}#admin-order-results">次へ →</a></c:when><c:otherwise><span class="page-step is-disabled" aria-disabled="true">次へ →</span></c:otherwise></c:choose>
        </nav>
    </c:if>
    </c:otherwise></c:choose>
    </section>
</c:otherwise></c:choose>
</main>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body></html>
