<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<!DOCTYPE html>
<html>
    <head>
        <meta charset="UTF-8">
        <title>Checkout</title>
    </head>
    <body>
        <h1>Checkout</h1>
        <c:if test="${not empty cart}">
            <table>
                <tr>
                    <th>Game</th>
                    <th>Price</th>
                </tr>
                <c:forEach var="game" items="${cart}">
                    <tr>
                        <td>${game.title}</td>
                        <td>¥${game.price}</td>
                    </tr>
                </c:forEach>
                <tr>
                    <td>Total</td>
                    <td>¥${total}</td>
                </tr>
            </table>
            <form action="${pageContext.request.contextPath}/order" method="post">
                <button type="submit">注文を確定する</button>
            </form>
        </c:if>
    </body>
</html>