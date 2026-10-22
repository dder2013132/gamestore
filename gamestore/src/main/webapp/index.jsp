<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <link rel="stylesheet"
      href="${pageContext.request.contextPath}/css/style.css">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>PLAYPORT</title>
</head>
<body>
    <header>
        <h1>PLAYPORT</h1>
        <nav>
            <ul>
                <li><a href="${pageContext.request.contextPath}/home">ホーム</a></li>
                <li><a href="#">ゲーム一覧</a></li>
                <li><a href="${pageContext.request.contextPath}/cart">カート</a></li>
                <li><a href="${pageContext.request.contextPath}/orders">注文履歴を見る</a></li>
            </ul>
        </nav>
    </header>
    <main>
        <p>ゲームストアへようこそ</p>
        <h2>おすすめゲーム</h2>
        <div class="game-grid">
            <c:forEach var="game" items="${games}">
                <div class="game-card">
                    <img
                        src="${pageContext.request.contextPath}${game.imagePath}"
                        alt="${game.title}">
                    <a href="${pageContext.request.contextPath}/games?id=${game.id}">
                        詳細を見る
                    </a>
                    <h3>${game.title}</h3>
                    <p>${game.price}</p>
                    <p>${game.genre}</p>
                </div>
            </c:forEach>
        </div>
    </main>
</body>
</html>