<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<!DOCTYPE html>
<html>
    <head>
        <meta charset="UTF-8">
        <title>注文リスト</title>
    </head>
    <body>
        <h1>注文リスト</h1>
        <form method="get" action="${pageContext.request.contextPath}/orders">
            <input type="text" name="orderId" value="<c:out value='${param.orderId}' default='' escapeXml='true' />">
            <button type="submit">検索</button>
            <a href="${pageContext.request.contextPath}/orders">クリア</a>
        </form>
        <c:choose>
            <c:when test="${empty orders}">
                <p>注文履歴がありません。</p>
            </c:when>
            <c:otherwise>
                <table border="1">
                    <tr>
                        <th>注文ID</th>
                        <th>注文日時</th>
                        <th>合計</th>
                    </tr>
                    <c:forEach var="order" items="${orders}">
                        <tr>
                            <td><a href="${pageContext.request.contextPath}/order/complete?id=${order.orderId}">${order.orderId}</a></td>
                            <td>${order.formattedCreatedAt}</td>
                            <td>${order.totalPrice}</td>
                        </tr>
                    </c:forEach>
                </table>
            </c:otherwise>
        </c:choose>
    </body>
</html>