<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<!DOCTYPE html>
<html lang="ja">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>カート - PLAYPORT</title>
        <link rel="stylesheet"
        href="${pageContext.request.contextPath}/css/style.css">
    </head>
    <body>
        <main>
            <h1>ショッピングカート</h1>
            <div class="cart-list">

                <p>合計金額: ¥${totalPrice}</p>
                <c:forEach var="game" items="${cart}">
                    <div class="cart-item">
                        <div class="cart-info">
                            <h3>${game.title}</h3>
                            <p>¥${game.price}</p>
                        </div>
                        <form action="${pageContext.request.contextPath}/cart/remove" method="post">
                            <input type="hidden" name="gameId" value="${game.id}">
                            <button type="submit">削除</button>
                        </form>
                    </div>
                </c:forEach>

                <c:if test="${not empty cart}">
                    <form action="${pageContext.request.contextPath}/checkout" method="get">
                        <button type="submit">購入手続きへ</button>
                    </form>
                </c:if>
                <c:if test="${empty cart}">
                    <p>カートに商品はありません。</p>
                </c:if>
            </div>
        </main>
    </body>
</html>