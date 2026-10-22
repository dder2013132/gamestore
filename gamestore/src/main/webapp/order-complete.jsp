<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>注文完了</title>
</head>
<body>
    <h1>ご注文が完了しました！</h1>
    <p>ご注文ありがとうございます。以下の内容で承りました。</p>

    <c:if test="${not empty order}">
        <h2>注文番号</h2>
        <p>${order.orderId}</p>
        <hr>
        <h2>ご注文の商品</h2>
        <ul>
            <c:forEach var="game" items="${order.games}">
                <li>${game.title} — ¥${game.price}</li>
            </c:forEach>
        </ul>
        <hr>
        <h2>合計金額</h2><h2>¥${order.totalPrice}</h2>
    </c:if>
    
</body>
</html>